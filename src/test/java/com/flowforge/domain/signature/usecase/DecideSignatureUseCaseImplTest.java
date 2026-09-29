package com.flowforge.domain.signature.usecase;

import static com.flowforge.domain.signature.SignatureFixtures.NOW;
import static com.flowforge.domain.signature.SignatureFixtures.TENANT;
import static com.flowforge.domain.signature.SignatureFixtures.external;
import static com.flowforge.domain.signature.SignatureFixtures.internal;
import static com.flowforge.domain.signature.SignatureFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.signature.InMemorySignatureRepository;
import com.flowforge.domain.signature.SignatureAccessResolver;
import com.flowforge.domain.signature.SignatureNotifier;
import com.flowforge.domain.signature.SignatureOutcomeHandler;
import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.SignatureProgression;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.SignatureStatus;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.signature.port.in.DecideSignatureUseCase;
import com.flowforge.domain.signature.port.in.DecideSignatureUseCase.Choice;
import com.flowforge.domain.signature.port.out.SignatureMetricsPort;
import com.flowforge.domain.signature.port.out.SignatureOutcomeListener;
import com.flowforge.domain.signature.port.out.SignaturePort;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.domain.workflow.portal.model.PortalAccessDeniedException;
import com.flowforge.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * S-DEC-1 à 6, S-PROG-2/4/7 (intégration), S-INT-2/5/6, S-OUT-2 (déclenchement) — décisions des
 * signataires internes et externes (TODO §72, M6/M6b/M6c).
 */
class DecideSignatureUseCaseImplTest {

    private final InMemorySignatureRepository repository = spy(new InMemorySignatureRepository());
    private final PortalTokenPort tokenPort = mock(PortalTokenPort.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final SignaturePort port = mock(SignaturePort.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final AddDossierElementUseCase addElement = mock(AddDossierElementUseCase.class);
    private final SendNotificationUseCase sendNotification = mock(SendNotificationUseCase.class);
    private final SignatureMetricsPort metrics = mock(SignatureMetricsPort.class);
    private final SignatureOutcomeListener listener = mock(SignatureOutcomeListener.class);
    private DecideSignatureUseCaseImpl useCase;
    private SignatureRequest request;
    private int seq;

    @BeforeEach
    void setUp() {
        when(tokenPort.hash(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        Dossier dossier = mock(Dossier.class);
        when(dossier.status()).thenReturn(com.flowforge.domain.dossier.model.DossierStatus.OUVERT);
        when(dossiers.findById(any())).thenReturn(Optional.of(dossier));
        when(port.fetchArtifacts(any())).thenReturn(
                new SignaturePort.SignedArtifacts("signed".getBytes(), "application/pdf", "proof".getBytes(),
                        "application/pdf", true));
        when(storage.store(eq(TENANT), anyString(), any())).thenReturn("ref");
        DossierWorkflowLinkRepository links = mock(DossierWorkflowLinkRepository.class);
        WorkflowParticipantPort participants = mock(WorkflowParticipantPort.class);
        SignatureNotifier notifier = new SignatureNotifier(TENANT, links, participants, sendNotification);
        useCase = build(Clock.fixed(NOW, ZoneOffset.UTC), notifier);
    }

    private DecideSignatureUseCaseImpl build(Clock clock, SignatureNotifier notifier) {
        SignatureAccessResolver resolver = new SignatureAccessResolver(repository, tokenPort, dossiers, clock);
        SignatureOutcomeHandler handler = new SignatureOutcomeHandler(TENANT, repository, port, storage, addElement,
                notifier, metrics, listener, clock);
        return new DecideSignatureUseCaseImpl(repository, resolver, handler, clock);
    }

    /** Crée une demande et ses signataires ; ceux qui ont un e-mail reçoivent le jeton « tok-<rang> ». */
    private List<Signer> seed(SignatureMode mode, Signer... drafts) {
        request = request(UUID.randomUUID(), mode, "proc-" + (++seq));
        List<Signer> started = SignatureProgression.start(mode, List.of(drafts).stream()
                .map(d -> new Signer(d.id(), d.tenantId(), request.id(), d.position(), d.kind(), d.required(),
                        d.userId(), d.displayName(), d.email(), d.email() == null ? null : "hash:tok-" + d.position(),
                        d.status(), null, 0, null, null, 0L))
                .toList()).signers();
        repository.insert(request, started);
        return started;
    }

    private Signer ext(int pos, boolean required) {
        return external(UUID.randomUUID(), pos, required, "s" + pos + "@example.org");
    }

    private List<SignerStatus> statuses() {
        return repository.findSigners(request.id()).stream().map(Signer::status).toList();
    }

    @Test
    void signatureOfTheOnlyRequiredSignerClosesSignedAndAttachesTheArtifacts() {
        seed(SignatureMode.PARALLEL, ext(0, true));

        DecideSignatureUseCase.Result result = useCase.decideByToken("tok-0", Choice.SIGN, null);

        assertThat(result.signerStatus()).isEqualTo(SignerStatus.SIGNED);
        assertThat(result.requestStatus()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.SIGNED);
        verify(addElement, times(2)).add(any(), any());
        verify(listener).outcomeRecorded(request.id());
    }

    @Test
    void secondSubmissionOfTheSameSignatureIsRefusedAndHasNoEffect() {
        // S-DEC-3
        seed(SignatureMode.PARALLEL, ext(0, true), ext(1, true));
        useCase.decideByToken("tok-0", Choice.SIGN, null);

        assertThatThrownBy(() -> useCase.decideByToken("tok-0", Choice.SIGN, null))
                .isInstanceOf(PortalAccessDeniedException.class);
        assertThat(statuses()).containsExactly(SignerStatus.SIGNED, SignerStatus.PENDING);
    }

    @Test
    void concurrentDecisionThatLosesTheCompareAndSetIsRefused() {
        seed(SignatureMode.PARALLEL, ext(0, true), ext(1, true));
        org.mockito.Mockito.doReturn(false).when(repository).update(any(), any());

        assertThatThrownBy(() -> useCase.decideByToken("tok-0", Choice.SIGN, null))
                .isInstanceOf(PortalAccessDeniedException.class);
        verify(listener, never()).outcomeRecorded(any());
    }

    @Test
    void decisionThatLosesTheCompareAndSetOnceIsRetriedOnTheFreshState() {
        // revue adversariale n°3 : deux signataires distincts décident en même temps
        seed(SignatureMode.PARALLEL, ext(0, true), ext(1, true));
        org.mockito.Mockito.doReturn(false).doCallRealMethod().when(repository).update(any(), any());

        useCase.decideByToken("tok-0", Choice.SIGN, null);

        assertThat(statuses()).containsExactly(SignerStatus.SIGNED, SignerStatus.PENDING);
    }

    @Test
    void declineReasonIsCleanedAndBounded() {
        // S-DEC-2
        seed(SignatureMode.PARALLEL, ext(0, false), ext(1, true));
        assertThatThrownBy(() -> useCase.decideByToken("tok-0", Choice.DECLINE, "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(statuses()).containsOnly(SignerStatus.PENDING);

        useCase.decideByToken("tok-0", Choice.DECLINE, "  pas\r\nd'accord\t<b>x</b>  ");

        assertThat(repository.findSigners(request.id()).getFirst().declineReason())
                .isEqualTo("pas  d'accord <b>x</b>");
    }

    @Test
    void decisionsAreRefusedOnceTheRequestIsClosedOrPastItsDeadline() {
        // S-DEC-4
        seed(SignatureMode.PARALLEL, ext(0, true));
        DecideSignatureUseCaseImpl late = build(
                Clock.fixed(request.deadlineAt().plus(Duration.ofSeconds(1)), ZoneOffset.UTC),
                new SignatureNotifier(TENANT, mock(DossierWorkflowLinkRepository.class),
                        mock(WorkflowParticipantPort.class), sendNotification));
        assertThatThrownBy(() -> late.decideByToken("tok-0", Choice.SIGN, null))
                .isInstanceOf(PortalAccessDeniedException.class);

        repository.updateRequest(request.closed(com.flowforge.domain.signature.model.SignatureOutcome.CANCELLED, null, NOW));
        assertThatThrownBy(() -> useCase.decideByToken("tok-0", Choice.SIGN, null))
                .isInstanceOf(PortalAccessDeniedException.class);
    }

    @Test
    void requiredDeclineClosesDeclinedRevokesOthersAndAttachesNothing() {
        // S-PROG-2
        seed(SignatureMode.PARALLEL, ext(0, true), ext(1, true), ext(2, false));
        DecideSignatureUseCase.Result result = useCase.decideByToken("tok-1", Choice.DECLINE, "non");

        assertThat(result.requestStatus()).isEqualTo(SignatureStatus.DECLINED);
        assertThat(statuses()).containsExactly(SignerStatus.REVOKED, SignerStatus.DECLINED, SignerStatus.REVOKED);
        assertThat(repository.findById(request.id()).orElseThrow().statusReason()).isEqualTo("REQUIRED_DECLINED");
        verify(addElement, never()).add(any(), any());
        verify(port, never()).fetchArtifacts(any());
        verify(listener).outcomeRecorded(request.id());
    }

    @Test
    void optionalDeclineKeepsTheRequestOpenAndLastRequiredSignatureRevokesTheOptional() {
        // S-PROG-4, S-PROG-7
        seed(SignatureMode.PARALLEL, ext(0, true), ext(1, false), ext(2, false));
        assertThat(useCase.decideByToken("tok-1", Choice.DECLINE, null).requestStatus())
                .isEqualTo(SignatureStatus.PENDING);
        assertThat(useCase.decideByToken("tok-0", Choice.SIGN, null).requestStatus())
                .isEqualTo(SignatureStatus.SIGNED);
        assertThat(statuses()).containsExactly(SignerStatus.SIGNED, SignerStatus.DECLINED, SignerStatus.REVOKED);
    }

    @Test
    void sequentialModeGivesEachSignerTheirTurn() {
        // S-DEC-5
        seed(SignatureMode.SEQUENTIAL, ext(0, true), ext(1, true));
        assertThatThrownBy(() -> useCase.decideByToken("tok-1", Choice.SIGN, null))
                .isInstanceOf(PortalAccessDeniedException.class);

        assertThat(useCase.decideByToken("tok-0", Choice.SIGN, null).requestStatus())
                .isEqualTo(SignatureStatus.PENDING);
        assertThat(statuses()).containsExactly(SignerStatus.SIGNED, SignerStatus.PENDING);
        assertThat(useCase.decideByToken("tok-1", Choice.SIGN, null).requestStatus())
                .isEqualTo(SignatureStatus.SIGNED);
    }

    @Test
    void parallelModeAcceptsDecisionsInAnyOrder() {
        // S-DEC-6
        seed(SignatureMode.PARALLEL, ext(0, true), ext(1, true));
        useCase.decideByToken("tok-1", Choice.SIGN, null);
        assertThat(useCase.decideByToken("tok-0", Choice.SIGN, null).requestStatus())
                .isEqualTo(SignatureStatus.SIGNED);
    }

    @Test
    void internalSignerDecidesOnlyForHimselfAndOnlyOnHisTurn() {
        // S-INT-2, S-INT-5
        List<Signer> signers = seed(SignatureMode.SEQUENTIAL, ext(0, true), internal(UUID.randomUUID(), 1, true, "bob"));
        Signer bob = signers.get(1);

        assertThatThrownBy(() -> useCase.decideInternal("bob", bob.id(), Choice.SIGN, null))
                .isInstanceOf(PortalAccessDeniedException.class); // pas son tour
        useCase.decideByToken("tok-0", Choice.SIGN, null);

        assertThatThrownBy(() -> useCase.decideInternal("mallory", bob.id(), Choice.SIGN, null))
                .isInstanceOf(PortalAccessDeniedException.class); // autre utilisateur
        assertThatThrownBy(() -> useCase.decideInternal("bob", signers.get(0).id(), Choice.SIGN, null))
                .isInstanceOf(PortalAccessDeniedException.class); // signataire externe
        assertThatThrownBy(() -> useCase.decideInternal("bob", UUID.randomUUID(), Choice.SIGN, null))
                .isInstanceOf(PortalAccessDeniedException.class); // inconnu

        assertThat(useCase.decideInternal("bob", bob.id(), Choice.SIGN, null).requestStatus())
                .isEqualTo(SignatureStatus.SIGNED);
    }

    @Test
    void nextInternalSignerIsNotifiedWhenItBecomesHisTurn() {
        // S-INT-6
        seed(SignatureMode.SEQUENTIAL, ext(0, true), internal(UUID.randomUUID(), 1, true, "bob"));
        DossierWorkflowLinkRepository links = mock(DossierWorkflowLinkRepository.class);
        when(links.findByProcessInstanceId(any(), any())).thenReturn(Optional.empty());

        useCase.decideByToken("tok-0", Choice.SIGN, null);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(sendNotification).send(captor.capture(), eq(TENANT));
        assertThat(captor.getValue().userId()).isEqualTo("bob");
    }
}
