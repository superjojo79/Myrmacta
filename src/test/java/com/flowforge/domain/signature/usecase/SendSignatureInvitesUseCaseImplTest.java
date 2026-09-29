package com.flowforge.domain.signature.usecase;

import static com.flowforge.domain.signature.SignatureFixtures.NOW;
import static com.flowforge.domain.signature.SignatureFixtures.TENANT;
import static com.flowforge.domain.signature.SignatureFixtures.external;
import static com.flowforge.domain.signature.SignatureFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.signature.InMemorySignatureRepository;
import com.flowforge.domain.signature.SignatureNotifier;
import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.SignatureOutcome;
import com.flowforge.domain.signature.model.SignatureProgression;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.signature.port.out.SignatureInviteSenderPort;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** S-DEC-7 (parallèle et séquentiel) — invitations des signataires externes (TODO §72, M4). */
class SendSignatureInvitesUseCaseImplTest {

    private final InMemorySignatureRepository repository = org.mockito.Mockito.spy(new InMemorySignatureRepository());
    private final PortalTokenPort tokenPort = mock(PortalTokenPort.class);
    private final SignatureInviteSenderPort sender = mock(SignatureInviteSenderPort.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final SendNotificationUseCase sendNotification = mock(SendNotificationUseCase.class);
    private final DossierWorkflowLinkRepository links = mock(DossierWorkflowLinkRepository.class);
    private final WorkflowParticipantPort participants = mock(WorkflowParticipantPort.class);
    private SendSignatureInvitesUseCaseImpl useCase;
    private int tokens;

    @BeforeEach
    void setUp() {
        when(tokenPort.newToken()).thenAnswer(inv -> "token-" + (++tokens));
        when(tokenPort.hash(anyString())).thenAnswer(inv -> "hash-of-" + inv.getArgument(0));
        when(dossiers.findById(any())).thenReturn(Optional.empty());
        DossierWorkflowLink link = mock(DossierWorkflowLink.class);
        when(link.id()).thenReturn(UUID.randomUUID());
        when(links.findByProcessInstanceId(any(), any())).thenReturn(Optional.of(link));
        when(participants.findParticipantUserIds(link)).thenReturn(List.of("manager"));
        SignatureNotifier notifier = new SignatureNotifier(TENANT, links, participants, sendNotification);
        useCase = new SendSignatureInvitesUseCaseImpl(repository, tokenPort, sender, dossiers, notifier,
                Clock.fixed(NOW, ZoneOffset.UTC), 3, 50);
    }

    private SignatureRequest seed(SignatureMode mode, int count) {
        SignatureRequest r = request(UUID.randomUUID(), mode);
        List<Signer> drafts = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> external(r.id(), i, true, "s" + i + "@example.org"))
                .toList();
        repository.insert(r, SignatureProgression.start(mode, drafts).signers());
        return r;
    }

    @Test
    void parallelSendsToEveryPendingExternalSignerAndStoresOnlyTheHash() {
        SignatureRequest r = seed(SignatureMode.PARALLEL, 2);

        assertThat(useCase.sendPending()).isEqualTo(2);

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(sender, times(2)).send(any(), tokenCaptor.capture(), any());
        List<Signer> after = repository.findSigners(r.id());
        assertThat(after).extracting(Signer::inviteSentAt).containsOnly(NOW);
        assertThat(after).extracting(Signer::tokenHash)
                .containsExactly("hash-of-" + tokenCaptor.getAllValues().get(0), "hash-of-" + tokenCaptor.getAllValues().get(1));
        assertThat(after).extracting(Signer::tokenHash).doesNotContain(tokenCaptor.getAllValues().toArray(String[]::new));
        assertThat(useCase.sendPending()).isZero();
    }

    @Test
    void sequentialInvitesOnlyTheSignerWhoseTurnItIs() {
        SignatureRequest r = seed(SignatureMode.SEQUENTIAL, 2);

        assertThat(useCase.sendPending()).isEqualTo(1);
        assertThat(repository.findSigners(r.id())).extracting(Signer::inviteSentAt).containsExactly(NOW, null);

        // le premier signe : le second passe PENDING (D-SIGNATURE-PROGRESSION-SYNC) puis est invité
        List<Signer> signers = repository.findSigners(r.id());
        SignatureProgression.Result next = SignatureProgression.decide(SignatureMode.SEQUENTIAL, signers,
                signers.get(0).id(), SignatureProgression.Decision.SIGN, NOW, null);
        repository.update(repository.findById(r.id()).orElseThrow(), next.signers().stream()
                .map(s -> s.withVersion(repository.findSignerById(s.id()).orElseThrow().version()))
                .toList());
        assertThat(useCase.sendPending()).isEqualTo(1);
        assertThat(repository.findSigners(r.id())).extracting(Signer::inviteSentAt).containsOnly(NOW);
    }

    @Test
    void closedRequestsAreNotInvited() {
        SignatureRequest r = seed(SignatureMode.PARALLEL, 1);
        repository.updateRequest(r.closed(SignatureOutcome.CANCELLED, null, NOW));

        assertThat(useCase.sendPending()).isZero();
        verify(sender, never()).send(any(), any(), any());
    }

    @Test
    void sendFailureCountsAnAttemptAndNotifiesOnceTheCeilingIsReached() {
        SignatureRequest r = seed(SignatureMode.PARALLEL, 1);
        doThrow(new IllegalStateException("smtp down")).when(sender).send(any(), any(), any());

        for (int i = 0; i < 5; i++) {
            useCase.sendPending();
        }

        Signer signer = repository.findSigners(r.id()).getFirst();
        assertThat(signer.inviteAttempts()).isEqualTo(3);
        assertThat(signer.inviteSentAt()).isNull();
        assertThat(signer.tokenHash()).isNull();
        assertThat(signer.status()).isEqualTo(SignerStatus.PENDING);
        verify(sender, times(3)).send(any(), any(), any());
        verify(sendNotification, times(1)).send(any(), anyString());
    }

    @Test
    void nothingIsSentWhenTheReservationLosesTheCompareAndSet() {
        // revue adversariale n°4 : un renvoi ou une autre instance est passé avant — jamais de lien mort par e-mail
        SignatureRequest r = seed(SignatureMode.PARALLEL, 1);
        org.mockito.Mockito.doReturn(false).when(repository).updateSigner(any());

        assertThat(useCase.sendPending()).isZero();

        verify(sender, never()).send(any(), any(), any());
    }

    @Test
    void theTokenHashIsPersistedBeforeTheMailLeaves() {
        SignatureRequest r = seed(SignatureMode.PARALLEL, 1);
        doAnswer(inv -> {
            Signer stored = repository.findSigners(r.id()).getFirst();
            assertThat(stored.tokenHash()).isEqualTo("hash-of-token-1");
            assertThat(stored.inviteSentAt()).isNotNull();
            return null;
        }).when(sender).send(any(), any(), any());

        assertThat(useCase.sendPending()).isEqualTo(1);
    }
}
