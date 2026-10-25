package be.myrmacta.domain.signature.usecase;

import static be.myrmacta.domain.signature.SignatureFixtures.NOW;
import static be.myrmacta.domain.signature.SignatureFixtures.TENANT;
import static be.myrmacta.domain.signature.SignatureFixtures.external;
import static be.myrmacta.domain.signature.SignatureFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.signature.InMemorySignatureRepository;
import be.myrmacta.domain.signature.SignatureNotifier;
import be.myrmacta.domain.signature.SignatureOutcomeHandler;
import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureProgression;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.port.out.SignatureMetricsPort;
import be.myrmacta.domain.signature.port.out.SignatureOutcomeListener;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S-OUT-4 (partie domaine), S-OUT-5 : retour d'un fournisseur réel (TODO §72, M8). */
class HandleSignatureCallbackUseCaseImplTest {

    private final InMemorySignatureRepository repository = spy(new InMemorySignatureRepository());
    private final SignaturePort port = mock(SignaturePort.class);
    private final SignatureMetricsPort metrics = mock(SignatureMetricsPort.class);
    private final SignatureOutcomeListener listener = mock(SignatureOutcomeListener.class);
    private final AddDossierElementUseCase addElement = mock(AddDossierElementUseCase.class);
    private HandleSignatureCallbackUseCaseImpl useCase;
    private SignatureRequest request;
    private List<Signer> signers;

    @BeforeEach
    void setUp() {
        DocumentStoragePort storage = mock(DocumentStoragePort.class);
        when(storage.store(any(), any(), any())).thenReturn("ref");
        when(port.fetchArtifacts("hosted:1")).thenReturn(
                new SignaturePort.SignedArtifacts("S".getBytes(), "application/pdf", "P".getBytes(),
                        "application/pdf", false));
        SignatureNotifier notifier = new SignatureNotifier(TENANT, mock(DossierWorkflowLinkRepository.class),
                mock(WorkflowParticipantPort.class), mock(SendNotificationUseCase.class));
        SignatureOutcomeHandler handler = new SignatureOutcomeHandler(TENANT, repository, port, storage, addElement,
                notifier, metrics, listener, Clock.fixed(NOW, ZoneOffset.UTC));
        useCase = new HandleSignatureCallbackUseCaseImpl(repository, handler, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
        request = request(UUID.randomUUID(), SignatureMode.PARALLEL).withProviderReference("hosted:1");
        signers = SignatureProgression.start(SignatureMode.PARALLEL, List.of(
                        external(request.id(), 0, true, "a@example.org"), external(request.id(), 1, false, "b@example.org")))
                .signers();
        repository.insert(request, signers);
    }

    private SignaturePort.SignatureCallbackEvent event(SignatureOutcome outcome, Map<UUID, SignerStatus> decisions) {
        return new SignaturePort.SignatureCallbackEvent("hosted:1", outcome, decisions);
    }

    private List<SignerStatus> statuses() {
        return repository.findSigners(request.id()).stream().map(Signer::status).toList();
    }

    @Test
    void signedEventClosesTheRequestKeepsReportedDecisionsAndAttachesTheArtifacts() {
        assertThat(useCase.apply(event(SignatureOutcome.SIGNED, Map.of(signers.get(0).id(), SignerStatus.SIGNED))))
                .isTrue();

        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(repository.findById(request.id()).orElseThrow().statusReason()).isEqualTo("PROVIDER_CALLBACK");
        assertThat(statuses()).containsExactly(SignerStatus.SIGNED, SignerStatus.REVOKED);
        verify(addElement, org.mockito.Mockito.times(2)).add(any(), any());
        verify(listener).outcomeRecorded(request.id());
    }

    @Test
    void expiredEventExpiresEveryOpenSigner() {
        useCase.apply(event(SignatureOutcome.EXPIRED, Map.of()));
        assertThat(statuses()).containsOnly(SignerStatus.EXPIRED);
        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.EXPIRED);
        verify(addElement, never()).add(any(), any());
    }

    @Test
    void declinedEventRecordsWhoDeclined() {
        useCase.apply(event(SignatureOutcome.DECLINED, Map.of(signers.get(1).id(), SignerStatus.DECLINED)));
        assertThat(statuses()).containsExactly(SignerStatus.REVOKED, SignerStatus.DECLINED);
        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.DECLINED);
    }

    @Test
    void eventForAClosedRequestIsIgnoredAndCounted() {
        // S-OUT-5 : callback tardif, rejeu
        useCase.apply(event(SignatureOutcome.SIGNED, Map.of()));

        assertThat(useCase.apply(event(SignatureOutcome.DECLINED, Map.of()))).isFalse();

        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.SIGNED);
        verify(metrics).callbackRejected("already_closed");
        verify(addElement, org.mockito.Mockito.times(2)).add(any(), any()); // pas de second jeu d'artefacts
    }

    @Test
    void unknownReferenceIsRetriableNotACachedFailure() {
        // revue adversariale n°8 : le fournisseur peut rappeler avant l'enregistrement de la référence
        assertThatThrownBy(() -> useCase.apply(
                        new SignaturePort.SignatureCallbackEvent("hosted:404", SignatureOutcome.SIGNED, Map.of())))
                .isInstanceOf(be.myrmacta.domain.connector.model.CallbackRetryLaterException.class);
        verify(metrics).callbackRejected("unknown_reference");
    }

    @Test
    void concurrentCloseLosesTheCompareAndSetAndIsCounted() {
        doReturn(false).when(repository).update(any(), any());
        assertThat(useCase.apply(event(SignatureOutcome.SIGNED, Map.of()))).isFalse();
        verify(metrics).callbackRejected("concurrent");
        verify(listener, never()).outcomeRecorded(any());
    }
}
