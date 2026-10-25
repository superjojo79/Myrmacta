package be.myrmacta.domain.workflow.portal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierConflictException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.in.UpdateDossierFieldsUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierFieldsUseCase.UpdateDossierFieldsCommand;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.workflow.UserQuestionAnswers;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.port.out.TaskQuestionAnswerRepository;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.portal.PortalAccessResolver;
import be.myrmacta.domain.workflow.portal.PortalParticipantNotifier;
import be.myrmacta.domain.workflow.portal.model.IdentityAssuranceLevel;
import be.myrmacta.domain.workflow.portal.model.IdentityMatch;
import be.myrmacta.domain.workflow.portal.model.NationalRegisterNumber;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import be.myrmacta.domain.workflow.portal.model.PortalCredential;
import be.myrmacta.domain.workflow.portal.model.PortalIdentityAttempt;
import be.myrmacta.domain.workflow.portal.model.PortalIdentityField;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkIdentity;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatus;
import be.myrmacta.domain.workflow.portal.model.PortalRecipientSource;
import be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition;
import be.myrmacta.domain.workflow.portal.model.VerifiedIdentity;
import be.myrmacta.domain.workflow.portal.port.in.CompletePortalIdentityUseCase.Outcome;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityProviderPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityProviderPort.AuthorizationRequest;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityProviderPort.IdentityProviderException;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityProviderPort.ProviderOption;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityRepository;
import be.myrmacta.domain.workflow.portal.port.out.PortalSecretCipherPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalTaskLookupPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * TODO §104 (issue #210, lot B) — authentification forte d'un tiers : démarrage (T3), retour du fournisseur (T4, T5,
 * T7, T10), vue réduite (T3b), soumission avec comparaison ou premier enregistrement du NRN (T5, T6, T8, T9).
 */
class PortalIdentityUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");
    private static final String TENANT = "tenant_dev";
    private static final String NRN = "85073003328";
    private static final PortalIdentityField FIELD = new PortalIdentityField("nrnDemandeur");

    private final InMemoryPortalLinkRepository links = new InMemoryPortalLinkRepository();
    private final FakeIdentities identities = new FakeIdentities();
    private final PortalTaskLookupPort taskLookup = mock(PortalTaskLookupPort.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final PortalIdentityProviderPort providers = mock(PortalIdentityProviderPort.class);
    private final UpdateDossierFieldsUseCase updateFields = mock(UpdateDossierFieldsUseCase.class);
    private final WorkflowEnginePort engine = mock(WorkflowEnginePort.class);
    private final TaskQuestionSchemaRepository schemas = mock(TaskQuestionSchemaRepository.class);
    private final AtomicInteger tokens = new AtomicInteger();
    private final PortalTokenPort tokenPort = new PortalTokenPort() {
        @Override
        public String newToken() {
            return "tok" + tokens.incrementAndGet();
        }

        @Override
        public String hash(String token) {
            return "hash-" + token;
        }
    };
    private final PortalSecretCipherPort cipher = new PortalSecretCipherPort() {
        @Override
        public String encrypt(String plaintext) {
            return "enc:" + plaintext;
        }

        @Override
        public String decrypt(String ciphertext) {
            return ciphertext.substring("enc:".length());
        }
    };
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final PortalAccessResolver resolver = new PortalAccessResolver(links, tokenPort, taskLookup, dossiers, clock,
            identities);
    private final StartPortalIdentityUseCaseImpl start = new StartPortalIdentityUseCaseImpl(TENANT, resolver, providers,
            identities, tokenPort, cipher, clock);
    private final CompletePortalIdentityUseCaseImpl complete = new CompletePortalIdentityUseCaseImpl(TENANT, identities,
            links, taskLookup, dossiers, providers, tokenPort, cipher, clock, Duration.ofMinutes(60));
    private final GetPortalTaskUseCaseImpl get = new GetPortalTaskUseCaseImpl(TENANT, resolver, schemas,
            mock(DossierElementRepository.class), providers);
    private final SubmitPortalTaskUseCaseImpl submit = new SubmitPortalTaskUseCaseImpl(TENANT, resolver, schemas,
            new UserQuestionAnswers(TENANT, mock(TaskQuestionAnswerRepository.class)), engine, links,
            mock(PortalParticipantNotifier.class), clock, identities, cipher, updateFields);

    private PortalLink link;
    private Map<String, Object> dossierFields;

    @BeforeEach
    void strongAuthenticationTask() {
        PortalLink pending = PortalLink.pendingSend(UUID.randomUUID(), TENANT, UUID.randomUUID(), "task-1", "pi-1", "avis",
                "a@b.org", 7, NOW, "x");
        links.insert(pending);
        link = pending.activated("hash-link", NOW);
        links.transition(pending, link);
        dossierFields = new HashMap<>();
        when(taskLookup.findOpenTask("task-1"))
                .thenReturn(Optional.of(new PortalTaskLookupPort.OpenTask("task-1", "pi-1", "Avis du riverain", "Consignes")));
        when(taskLookup.findExternalTaskDefinition("task-1")).thenReturn(Optional.of(new PortalTaskDefinition("task-1",
                "avis", PortalRecipientSource.parse("dossierField:email").orElseThrow(), null, FIELD)));
        when(dossiers.findById(link.dossierId())).thenAnswer(inv -> Optional.of(dossier()));
        when(providers.providers()).thenReturn(List.of(new ProviderOption("fas", "eID, itsme et autres (FAS)")));
        when(providers.begin(eq("fas"), anyString(), anyString()))
                .thenReturn(new AuthorizationRequest(URI.create("https://idp.example/auth?x=1"), "verifier-1"));
        TaskQuestionSchema schema = mock(TaskQuestionSchema.class);
        when(schema.fields()).thenReturn(List.of());
        when(schemas.findByKey(TENANT, "avis")).thenReturn(Optional.of(schema));
    }

    private Dossier dossier() {
        return new Dossier(link.dossierId(), TENANT, "R-1", "DEMANDE", "d", DossierStatus.EN_COURS, Map.copyOf(dossierFields),
                null, 3, "u", NOW, "u", NOW, null, null, null);
    }

    private VerifiedIdentity verified(String nrn, IdentityAssuranceLevel level) {
        return new VerifiedIdentity("fas", level, "Alice", "Martin",
                nrn == null ? null : NationalRegisterNumber.parse(nrn).orElseThrow());
    }

    /** Démarre, simule le retour du fournisseur et renvoie l'issue. */
    private Outcome authenticate(VerifiedIdentity identity) {
        URI uri = start.start(PortalCredential.of("link", null), "fas");
        assertThat(uri).hasHost("idp.example");
        String state = "tok" + (tokens.get() - 1);
        when(providers.complete("fas", "code-1", "verifier-1", "tok" + tokens.get())).thenReturn(identity);
        return complete.complete(state, "code-1", null);
    }

    @Test
    void startOpensAShortSingleUseAttemptWithAnEncryptedVerifier_T3() {
        start.start(PortalCredential.of("link", null), "fas");

        PortalIdentityAttempt attempt = identities.attempts.getFirst();
        assertThat(attempt.linkId()).isEqualTo(link.id());
        assertThat(attempt.stateHash()).startsWith("hash-tok");
        assertThat(attempt.pkceVerifierEnc()).isEqualTo("enc:verifier-1");
        assertThat(attempt.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void startIsRefusedForATaskWithoutStrongAuthenticationOrAnUnknownProvider() {
        when(providers.begin(eq("google"), anyString(), anyString()))
                .thenThrow(new IdentityProviderException("unknown provider"));
        assertThatThrownBy(() -> start.start(PortalCredential.of("link", null), "google"))
                .isInstanceOf(PortalAccessDeniedException.class);

        when(taskLookup.findExternalTaskDefinition("task-1")).thenReturn(Optional.of(new PortalTaskDefinition("task-1",
                "avis", PortalRecipientSource.parse("dossierField:email").orElseThrow(), null)));
        assertThatThrownBy(() -> start.start(PortalCredential.of("link", null), "fas"))
                .isInstanceOf(PortalAccessDeniedException.class);
        assertThat(identities.attempts).isEmpty();
    }

    @Test
    void aMatchingIdentityGivesAProofAndStoresTheNumberEncrypted_T5() {
        dossierFields.put("nrnDemandeur", "85.07.30-033.28");

        Outcome outcome = authenticate(verified(NRN, IdentityAssuranceLevel.HIGH));

        assertThat(outcome.isVerified()).isTrue();
        PortalLinkIdentity identity = identities.byLink.get(link.id());
        assertThat(identity.proofHash()).isEqualTo("hash-" + outcome.proof());
        assertThat(identity.nrnEnc()).isEqualTo("enc:" + NRN);
        assertThat(identity.match()).isEqualTo(IdentityMatch.CONFORME);
        assertThat(identity.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        assertThat(outcome.toString()).doesNotContain(outcome.proof());
    }

    @Test
    void anotherPersonALowLevelOrAMissingNumberIsRefusedAndNothingIsKept_T4_T5_T7() {
        dossierFields.put("nrnDemandeur", NRN);
        assertThat(authenticate(verified("90011512462", IdentityAssuranceLevel.HIGH)).isVerified()).isFalse();
        assertThat(authenticate(verified(NRN, IdentityAssuranceLevel.LOW)).isVerified()).isFalse();
        assertThat(authenticate(verified(null, IdentityAssuranceLevel.SUBSTANTIAL)).isVerified()).isFalse();
        assertThat(identities.byLink).isEmpty();
    }

    @Test
    void aReplayedUnknownOrCancelledReturnIsRefused_T10() {
        start.start(PortalCredential.of("link", null), "fas");
        String state = "tok" + (tokens.get() - 1);

        assertThat(complete.complete(state, null, "access_denied").isVerified()).as("annulé").isFalse();
        assertThat(complete.complete(state, "code-1", null).isVerified()).as("rejoué").isFalse();
        assertThat(complete.complete("inconnu", "code-1", null).isVerified()).isFalse();
        assertThat(complete.complete(null, "code-1", null).isVerified()).isFalse();
        verify(providers, never()).complete(any(), any(), any(), any());
    }

    @Test
    void theProviderFailingGivesTheNeutralRefusal() {
        start.start(PortalCredential.of("link", null), "fas");
        when(providers.complete(any(), any(), any(), any())).thenThrow(new IdentityProviderException("bad signature"));

        Outcome outcome = complete.complete("tok" + (tokens.get() - 1), "code-1", null);

        assertThat(outcome.isVerified()).isFalse();
        assertThat(outcome.refusal()).contains("bad signature");
    }

    @Test
    void theLinkTokenAloneShowsOnlyTheTaskNameTheReferenceAndTheProviders_T3b() {
        var view = get.get(PortalCredential.of("link", null));

        assertThat(view.identityRequired()).isTrue();
        assertThat(view.dossierReference()).isEqualTo("R-1");
        assertThat(view.taskName()).isEqualTo("Avis du riverain");
        assertThat(view.instructions()).isNull();
        assertThat(view.fields()).isEmpty();
        assertThat(view.ownAttachments()).isEmpty();
        assertThat(view.identityProviders()).extracting(ProviderOption::id).containsExactly("fas");
        assertThatThrownBy(() -> submit.submit(PortalCredential.of("link", null), Map.of()))
                .isInstanceOf(PortalAccessDeniedException.class);
    }

    @Test
    void withTheProofTheFullTaskIsShownWithTheVerifiedName() {
        Outcome outcome = authenticate(verified(NRN, IdentityAssuranceLevel.HIGH));

        var view = get.get(PortalCredential.of(null, outcome.proof()));

        assertThat(view.instructions()).isEqualTo("Consignes");
        assertThat(view.verifiedName()).isEqualTo("Alice Martin");
        assertThat(view.identityProviders()).isEmpty();
    }

    /** T6, T8, T9 — champ vide : NRN écrit en contexte système à version attendue ; preuve et NRN effacés ensuite. */
    @Test
    void aFirstRegistrationWritesTheNumberAtSubmission_T6() {
        Outcome outcome = authenticate(verified(NRN, IdentityAssuranceLevel.SUBSTANTIAL));

        submit.submit(PortalCredential.of(null, outcome.proof()), Map.of());

        ArgumentCaptor<UpdateDossierFieldsCommand> command = ArgumentCaptor.forClass(UpdateDossierFieldsCommand.class);
        ArgumentCaptor<AccessContext> context = ArgumentCaptor.forClass(AccessContext.class);
        verify(updateFields).update(command.capture(), context.capture());
        assertThat(command.getValue().fieldsPatch()).containsExactly(Map.entry("nrnDemandeur", NRN));
        assertThat(command.getValue().expectedVersion()).isEqualTo(3L);
        assertThat(command.getValue().updatedBy()).isEqualTo(link.actorId());
        assertThat(context.getValue()).isInstanceOf(AccessContext.System.class);
        assertThat(identities.submitted).containsExactly(link.id());
        assertThat(links.findById(link.id()).orElseThrow().status()).isEqualTo(PortalLinkStatus.COMPLETED);
        verify(engine).completeTask("task-1", Map.of());
    }

    @Test
    void aMatchingIdentityDoesNotRewriteTheField() {
        dossierFields.put("nrnDemandeur", NRN);
        Outcome outcome = authenticate(verified(NRN, IdentityAssuranceLevel.HIGH));

        submit.submit(PortalCredential.of(null, outcome.proof()), Map.of());

        verify(updateFields, never()).update(any(), any());
        assertThat(identities.submitted).containsExactly(link.id());
    }

    /** T6 — le champ a été rempli avec un autre NRN entre l'authentification et la soumission. */
    @Test
    void theFieldFilledWithAnotherNumberSinceTheAuthenticationIsRefused_T6() {
        Outcome outcome = authenticate(verified(NRN, IdentityAssuranceLevel.HIGH));
        dossierFields.put("nrnDemandeur", "90011512462");

        assertThatThrownBy(() -> submit.submit(PortalCredential.of(null, outcome.proof()), Map.of()))
                .isInstanceOf(PortalAccessDeniedException.class);
        verify(updateFields, never()).update(any(), any());
        verify(engine, never()).completeTask(any(), any());
    }

    @Test
    void aConcurrentChangeOfTheDossierIsANeutralRefusal() {
        Outcome outcome = authenticate(verified(NRN, IdentityAssuranceLevel.HIGH));
        when(updateFields.update(any(), any())).thenThrow(new DossierConflictException("Expected version 3 but found 4"));

        assertThatThrownBy(() -> submit.submit(PortalCredential.of(null, outcome.proof()), Map.of()))
                .isInstanceOf(PortalAccessDeniedException.class).hasMessageNotContaining(NRN);
        verify(engine, never()).completeTask(any(), any());
    }

    @Test
    void anIdentityWithoutNumberAndAnEmptyFieldSubmitsWithoutWriting_T7() {
        Outcome outcome = authenticate(verified(null, IdentityAssuranceLevel.SUBSTANTIAL));
        assertThat(identities.byLink.get(link.id()).match()).isEqualTo(IdentityMatch.SANS_NRN);

        submit.submit(PortalCredential.of(null, outcome.proof()), Map.of());

        verify(updateFields, never()).update(any(), any());
        verify(engine).completeTask("task-1", Map.of());
    }

    /** Identités en mémoire, avec les mêmes règles que l'adaptateur JDBC (usage unique, une identité par lien). */
    private static final class FakeIdentities implements PortalIdentityRepository {
        final List<PortalIdentityAttempt> attempts = new ArrayList<>();
        final List<String> consumed = new ArrayList<>();
        final Map<UUID, PortalLinkIdentity> byLink = new HashMap<>();
        final List<UUID> submitted = new ArrayList<>();

        @Override
        public void insertAttempt(PortalIdentityAttempt attempt) {
            attempts.add(attempt);
        }

        @Override
        public Optional<PortalIdentityAttempt> consumeAttempt(String stateHash, Instant now) {
            if (consumed.contains(stateHash)) {
                return Optional.empty();
            }
            Optional<PortalIdentityAttempt> found = attempts.stream()
                    .filter(a -> a.stateHash().equals(stateHash) && a.expiresAt().isAfter(now)).findFirst();
            found.ifPresent(a -> consumed.add(stateHash));
            return found;
        }

        @Override
        public void saveIdentity(PortalLinkIdentity identity) {
            byLink.put(identity.linkId(), identity);
        }

        @Override
        public Optional<PortalLinkIdentity> findByProofHash(String proofHash) {
            return byLink.values().stream().filter(i -> proofHash.equals(i.proofHash())).findFirst();
        }

        @Override
        public Optional<PortalLinkIdentity> findByLinkId(UUID linkId) {
            return Optional.ofNullable(byLink.get(linkId));
        }

        @Override
        public boolean markSubmitted(UUID linkId, Instant now) {
            submitted.add(linkId);
            return true;
        }

        @Override
        public void deleteUnsubmitted(UUID linkId) {
            byLink.remove(linkId);
        }

        @Override
        public int purgeExpired(Instant now) {
            return 0;
        }

        @Override
        public Map<UUID, be.myrmacta.domain.workflow.portal.model.IdentityVerification> tracesByLinkId(
                java.util.Collection<UUID> linkIds) {
            return Map.of();
        }
    }
}
