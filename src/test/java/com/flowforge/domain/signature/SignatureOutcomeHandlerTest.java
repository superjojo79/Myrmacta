package com.flowforge.domain.signature;

import static com.flowforge.domain.signature.SignatureFixtures.NOW;
import static com.flowforge.domain.signature.SignatureFixtures.TENANT;
import static com.flowforge.domain.signature.SignatureFixtures.external;
import static com.flowforge.domain.signature.SignatureFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.SignatureOutcome;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.signature.port.out.SignatureMetricsPort;
import com.flowforge.domain.signature.port.out.SignatureOutcomeListener;
import com.flowforge.domain.signature.port.out.SignaturePort;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** S-OUT-2, S-DOC-1, S-ADM-2 (propagation) — suites d'une fermeture (TODO §72, M6). */
class SignatureOutcomeHandlerTest {

    private final InMemorySignatureRepository repository = new InMemorySignatureRepository();
    private final SignaturePort port = mock(SignaturePort.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final AddDossierElementUseCase addElement = mock(AddDossierElementUseCase.class);
    private final SendNotificationUseCase sendNotification = mock(SendNotificationUseCase.class);
    private final SignatureMetricsPort metrics = mock(SignatureMetricsPort.class);
    private final SignatureOutcomeListener listener = mock(SignatureOutcomeListener.class);
    private SignatureOutcomeHandler handler;
    private SignatureRequest request;

    @BeforeEach
    void setUp() {
        DossierWorkflowLinkRepository links = mock(DossierWorkflowLinkRepository.class);
        DossierWorkflowLink link = mock(DossierWorkflowLink.class);
        when(link.id()).thenReturn(UUID.randomUUID());
        when(links.findByProcessInstanceId(any(), any())).thenReturn(Optional.of(link));
        WorkflowParticipantPort participants = mock(WorkflowParticipantPort.class);
        when(participants.findParticipantUserIds(link)).thenReturn(List.of("manager", "reviewer"));
        SignatureNotifier notifier = new SignatureNotifier(TENANT, links, participants, sendNotification);
        handler = new SignatureOutcomeHandler(TENANT, repository, port, storage, addElement, notifier, metrics,
                listener, Clock.fixed(NOW, ZoneOffset.UTC));
        request = request(UUID.randomUUID(), SignatureMode.PARALLEL).withProviderReference("stub:x")
                .withSimulation(true, "SIMULATED");
        when(storage.store(eq(TENANT), anyString(), any())).thenReturn("ref/signed", "ref/proof");
    }

    @Test
    void signedOutcomeAttachesMarkedProducedDocsAndNotifiesEveryParticipant() {
        when(port.fetchArtifacts("stub:x")).thenReturn(new SignaturePort.SignedArtifacts(
                "S".getBytes(), "application/pdf", "P".getBytes(), "application/pdf", true));

        handler.afterClosed(request, SignatureOutcome.SIGNED);

        ArgumentCaptor<AddDossierElementUseCase.AddCommand> add =
                ArgumentCaptor.forClass(AddDossierElementUseCase.AddCommand.class);
        verify(addElement, times(2)).add(add.capture(), any(AccessContext.class));
        List<AddDossierElementUseCase.AddCommand> commands = add.getAllValues();
        assertThat(commands).extracting(AddDossierElementUseCase.AddCommand::type)
                .containsOnly(ElementType.PRODUCED_DOC);
        assertThat(commands.get(0).metadata()).containsEntry("signatureRole", "SIGNED_DOCUMENT")
                .containsEntry("signatureRequestId", request.id().toString()).containsEntry("simulated", true);
        assertThat(commands.get(1).metadata()).containsEntry("signatureRole", "SIGNATURE_PROOF");
        assertThat(commands.get(0).label()).contains("SIMULATION");
        assertThat(commands.get(0).storageRef()).isEqualTo("ref/signed");
        assertThat(commands).extracting(AddDossierElementUseCase.AddCommand::dossierId).containsOnly(request.dossierId());
        verify(sendNotification, times(2)).send(any(NotificationEvent.class), eq(TENANT));
        verify(metrics).outcome(SignatureOutcome.SIGNED);
        verify(listener).outcomeRecorded(request.id());
    }

    @Test
    void artifactWithoutProofAttachesOnlyTheSignedDocument() {
        when(port.fetchArtifacts("stub:x")).thenReturn(new SignaturePort.SignedArtifacts(
                "S".getBytes(), "application/pdf", null, null, false));

        handler.afterClosed(request, SignatureOutcome.SIGNED);

        verify(addElement, times(1)).add(any(), any());
    }

    @Test
    void artifactFailureBubblesUpSoTheCallerRollsBack() {
        when(port.fetchArtifacts("stub:x")).thenThrow(new IllegalStateException("provider down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.afterClosed(request, SignatureOutcome.SIGNED))
                .isInstanceOf(IllegalStateException.class);
        verify(listener, never()).outcomeRecorded(any());
    }

    @Test
    void blobsAlreadyStoredAreDeletedWhenALaterStepRollsTheTransactionBack() {
        // revue adversariale n°9 : sinon ils ne seraient jamais référencés ni purgés
        when(port.fetchArtifacts("stub:x")).thenReturn(new SignaturePort.SignedArtifacts(
                "signed".getBytes(), "application/pdf", "proof".getBytes(), "application/pdf", true));
        when(addElement.add(any(), any())).thenReturn(null).thenThrow(new IllegalStateException("db down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.afterClosed(request, SignatureOutcome.SIGNED))
                .isInstanceOf(IllegalStateException.class);

        verify(storage).delete(TENANT, "ref/signed");
        verify(storage).delete(TENANT, "ref/proof");
    }

    @Test
    void nonSignedOutcomesAttachNothingButStillNotifyAndResume() {
        for (SignatureOutcome outcome : List.of(SignatureOutcome.DECLINED, SignatureOutcome.EXPIRED,
                SignatureOutcome.FAILED, SignatureOutcome.CANCELLED)) {
            handler.afterClosed(request, outcome);
        }
        verify(addElement, never()).add(any(), any());
        verify(port, never()).fetchArtifacts(any());
        verify(listener, times(4)).outcomeRecorded(request.id());
        verify(sendNotification, times(8)).send(any(), eq(TENANT));
    }

    @Test
    void cancellationAndExpiryAreBestEffortAtTheProvider() {
        doThrow(new IllegalStateException("down")).when(port).cancel("stub:x");

        handler.afterClosed(request, SignatureOutcome.CANCELLED);
        handler.afterClosed(request, SignatureOutcome.EXPIRED);
        handler.afterClosed(request, SignatureOutcome.DECLINED);

        verify(port, times(2)).cancel("stub:x");
        verify(listener, times(3)).outcomeRecorded(request.id());
    }

    @Test
    void closeAppliesTheAggregateAtomicallyThenRunsTheAftermath() {
        Signer pending = SignatureFixtures.external(request.id(), 0, true, "a@example.org")
                .withStatus(SignerStatus.PENDING);
        repository.insert(request, List.of(pending));
        List<Signer> revoked = SignatureOutcomeHandler.closeOpenSigners(List.of(pending), SignerStatus.REVOKED, NOW);

        assertThat(handler.close(repository.findById(request.id()).orElseThrow(), revoked,
                SignatureOutcome.CANCELLED, "CANCELLED_BY_USER")).isTrue();

        SignatureRequest stored = repository.findById(request.id()).orElseThrow();
        assertThat(stored.status().name()).isEqualTo("CANCELLED");
        assertThat(stored.statusReason()).isEqualTo("CANCELLED_BY_USER");
        assertThat(stored.completedAt()).isEqualTo(NOW);
        assertThat(repository.findSigners(request.id()).getFirst().status()).isEqualTo(SignerStatus.REVOKED);
        // un second appel perd le compare-and-set : rien n'est refait
        assertThat(handler.close(request, revoked, SignatureOutcome.CANCELLED, null)).isFalse();
        verify(listener, times(1)).outcomeRecorded(request.id());
    }

    @Test
    void closeOpenSignersLeavesDecidedOnesUntouched() {
        UUID req = UUID.randomUUID();
        Signer signed = external(req, 0, true, "a@example.org").decided(SignerStatus.SIGNED, NOW, null);
        Signer waiting = external(req, 1, true, "b@example.org");
        List<Signer> out = SignatureOutcomeHandler.closeOpenSigners(List.of(signed, waiting), SignerStatus.EXPIRED, NOW);
        assertThat(out).extracting(Signer::status).containsExactly(SignerStatus.SIGNED, SignerStatus.EXPIRED);
    }
}
