package be.myrmacta.domain.workflow.portal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.workflow.UserQuestionAnswers;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.port.out.TaskQuestionAnswerRepository;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.portal.PortalAccessResolver;
import be.myrmacta.domain.workflow.portal.model.PortalCredential;
import be.myrmacta.domain.workflow.portal.PortalParticipantNotifier;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatus;
import be.myrmacta.domain.workflow.portal.port.out.PortalAvailabilityPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalInviteSenderPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalTaskLookupPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TODO §68, Phase 8 — branches que les tests moteur n'atteignent pas : transition compare-and-set
 * perdue face à un écrivain concurrent (le perdant ne doit ni tracer, ni compléter, ni notifier),
 * et données disparues entre l'émission du lien et son usage (schéma supprimé).
 */
class PortalRaceAndMissingDataTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final String TOKEN = "tok";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryPortalLinkRepository repository = new InMemoryPortalLinkRepository();
    private final PortalTaskLookupPort taskLookup = mock(PortalTaskLookupPort.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final TaskQuestionSchemaRepository schemas = mock(TaskQuestionSchemaRepository.class);
    private final TaskQuestionAnswerRepository answers = mock(TaskQuestionAnswerRepository.class);
    private final WorkflowEnginePort engine = mock(WorkflowEnginePort.class);
    private final PortalParticipantNotifier notifier = mock(PortalParticipantNotifier.class);
    private final PortalTokenPort tokenPort = new PortalTokenPort() {
        @Override
        public String newToken() {
            return TOKEN;
        }

        @Override
        public String hash(String token) {
            return "hash-" + token;
        }
    };
    private final PortalAccessResolver resolver =
            new PortalAccessResolver(repository, tokenPort, taskLookup, dossiers, clock);
    private final UUID dossierId = UUID.randomUUID();
    private PortalLink active;

    @BeforeEach
    void setUp() {
        active = PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", dossierId, "task-1", "pi-1", "avis",
                "alice@example.org", 7, NOW, "agent").activated("hash-" + TOKEN, NOW);
        repository.insert(active);
        when(taskLookup.findOpenTask("task-1"))
                .thenReturn(Optional.of(new PortalTaskLookupPort.OpenTask("task-1", "pi-1", "Avis", null)));
        when(dossiers.findById(dossierId)).thenReturn(Optional.of(new Dossier(dossierId, "tenant_dev", "REF", "T",
                "L", DossierStatus.OUVERT, Map.of(), null, 0L, "u", NOW, null, null, null, null, null)));
        when(schemas.findByKey("tenant_dev", "avis")).thenReturn(Optional.of(
                new TaskQuestionSchema(UUID.randomUUID(), "tenant_dev", "avis", "Avis",
                        List.of(new FieldDefinition("comment", "Commentaire", FieldType.STRING, false, null, List.of(), null)),
                        "u", "u")));
    }

    private SubmitPortalTaskUseCaseImpl submitUseCase() {
        return new SubmitPortalTaskUseCaseImpl("tenant_dev", resolver, schemas,
                new UserQuestionAnswers("tenant_dev", answers), engine, repository, notifier, clock);
    }

    @Test
    void resolver_refusesAnOverlongTokenWithoutLookingItUp() {
        assertThatThrownBy(() -> resolver.resolve("x".repeat(129))).isInstanceOf(PortalAccessDeniedException.class);
        assertThat(repository.findById(active.id()).orElseThrow().lastAccessedAt()).isNull();
    }

    @Test
    void resolver_losingTheAccessRace_isDenied() {
        repository.loseEveryRace = true;

        assertThatThrownBy(() -> resolver.resolve(TOKEN)).isInstanceOf(PortalAccessDeniedException.class);
    }

    @Test
    void submit_losingTheCompletionRace_recordsNothing_completesNothing_notifiesNobody() {
        PortalAccessResolver.ResolvedAccess access = resolver.resolve(TOKEN);
        PortalAccessResolver racing = mock(PortalAccessResolver.class);
        when(racing.resolve(PortalCredential.of(TOKEN, null))).thenReturn(access);
        repository.loseEveryRace = true;
        SubmitPortalTaskUseCaseImpl useCase = new SubmitPortalTaskUseCaseImpl("tenant_dev", racing, schemas,
                new UserQuestionAnswers("tenant_dev", answers), engine, repository, notifier, clock);

        assertThatThrownBy(() -> useCase.submit(TOKEN, Map.of())).isInstanceOf(PortalAccessDeniedException.class);
        verifyNoInteractions(answers, engine, notifier);
    }

    @Test
    void submit_withoutBody_isValidatedAsAnEmptyAnswerSet() {
        submitUseCase().submit(TOKEN, null);

        verify(engine).completeTask("task-1", Map.of());
        assertThat(repository.findById(active.id()).orElseThrow().status()).isEqualTo(PortalLinkStatus.COMPLETED);
    }

    @Test
    void schemaDeletedAfterIssuance_deniesBothReadAndSubmit_withTheNeutralError() {
        when(schemas.findByKey("tenant_dev", "avis")).thenReturn(Optional.empty());
        GetPortalTaskUseCaseImpl get =
                new GetPortalTaskUseCaseImpl("tenant_dev", resolver, schemas, mock(DossierElementRepository.class));

        assertThatThrownBy(() -> get.get(TOKEN)).isInstanceOf(PortalAccessDeniedException.class);
        assertThatThrownBy(() -> submitUseCase().submit(TOKEN, Map.of()))
                .isInstanceOf(PortalAccessDeniedException.class);
        verify(engine, never()).completeTask(anyString(), any());
    }

    @Test
    void expiry_losingTheRace_neitherCountsNorNotifies() {
        repository.loseEveryRace = true;
        Clock later = Clock.fixed(NOW.plusSeconds(8 * 86_400L), ZoneOffset.UTC);

        assertThat(new ExpirePortalLinksUseCaseImpl(repository, notifier, later, 50).expireDue()).isZero();
        verifyNoInteractions(notifier);
    }

    @Test
    void invalidation_losingTheRace_isNotCounted() {
        repository.loseEveryRace = true;

        assertThat(new InvalidatePortalLinksForTaskUseCaseImpl(repository, clock).onTaskEnded("task-1"))
                .isZero();
    }

    @Test
    void sendFailure_losingTheRace_doesNotNotify() {
        PortalLink pending = PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", dossierId, "task-2", "pi-1",
                "avis", "bob@example.org", 7, NOW, "agent");
        repository.insert(pending);
        when(taskLookup.findOpenTask("task-2"))
                .thenReturn(Optional.of(new PortalTaskLookupPort.OpenTask("task-2", "pi-1", "Avis", null)));
        PortalInviteSenderPort sender = mock(PortalInviteSenderPort.class);
        doThrow(new IllegalStateException("smtp down")).when(sender).send(any(), anyString(), any());
        repository.loseEveryRace = true;

        int sent = new SendPortalInvitesUseCaseImpl(repository, tokenPort, sender, taskLookup, dossiers, notifier,
                clock, 1, 50).sendPending();

        assertThat(sent).isZero();
        verifyNoInteractions(notifier);
    }

    @Test
    void revoke_losingTheRace_isAConflict() {
        DossierAccessPolicy policy = mock(DossierAccessPolicy.class);
        PortalAvailabilityPort availability = mock(PortalAvailabilityPort.class);
        IssuePortalLinkUseCaseImpl issuer = new IssuePortalLinkUseCaseImpl("tenant_dev", repository, schemas,
                mock(PortalRecipientResolverPort.class), notifier, clock, 7, 30);
        ManagePortalLinkUseCaseImpl manage = new ManagePortalLinkUseCaseImpl("tenant_dev", repository, dossiers,
                policy, taskLookup, engine, issuer, availability, clock);
        repository.loseEveryRace = true;

        assertThatThrownBy(() -> manage.revoke(dossierId, active.id(), AccessContext.user("agent", Set.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("concurrently");
    }
}
