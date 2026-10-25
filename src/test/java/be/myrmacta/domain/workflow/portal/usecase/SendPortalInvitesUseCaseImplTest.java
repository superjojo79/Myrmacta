package be.myrmacta.domain.workflow.portal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.workflow.portal.PortalParticipantNotifier;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatus;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatusReason;
import be.myrmacta.domain.workflow.portal.port.out.PortalInviteSenderPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalTaskLookupPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SendPortalInvitesUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    private final InMemoryPortalLinkRepository repository = new InMemoryPortalLinkRepository();
    private final PortalInviteSenderPort sender = mock(PortalInviteSenderPort.class);
    private final PortalTaskLookupPort taskLookup = mock(PortalTaskLookupPort.class);
    private final DossierRepository dossierRepository = mock(DossierRepository.class);
    private final PortalParticipantNotifier notifier = mock(PortalParticipantNotifier.class);
    private final AtomicInteger tokens = new AtomicInteger();
    private final PortalTokenPort tokenPort = new PortalTokenPort() {
        @Override
        public String newToken() {
            return "token-" + tokens.incrementAndGet();
        }

        @Override
        public String hash(String token) {
            return "hash-of-" + token;
        }
    };
    private SendPortalInvitesUseCaseImpl useCase;
    private PortalLink pending;

    @BeforeEach
    void setUp() {
        useCase = new SendPortalInvitesUseCaseImpl(repository, tokenPort, sender, taskLookup, dossierRepository,
                notifier, Clock.fixed(NOW, ZoneOffset.UTC), 3, 50);
        pending = PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", UUID.randomUUID(), "task-1", "pi-1",
                "avis", "alice@example.org", 7, NOW, "x");
        repository.insert(pending);
        when(taskLookup.findOpenTask("task-1"))
                .thenReturn(Optional.of(new PortalTaskLookupPort.OpenTask("task-1", "pi-1", "Avis du tiers", null)));
        when(dossierRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void success_sendsTokenThenStoresOnlyItsHash() {
        // T-SEND-1
        assertThat(useCase.sendPending()).isEqualTo(1);

        verify(sender).send(any(PortalLink.class), eq("token-1"), any());
        PortalLink stored = repository.findById(pending.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(PortalLinkStatus.ACTIVE);
        assertThat(stored.tokenHash()).isEqualTo("hash-of-token-1");
        assertThat(stored.expiresAt()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
    }

    @Test
    void failure_retriesWithNewTokenThenGivesUpAndNotifies() {
        // T-SEND-2
        doThrow(new IllegalStateException("smtp down")).when(sender).send(any(), anyString(), any());

        useCase.sendPending();
        useCase.sendPending();
        assertThat(repository.findById(pending.id()).orElseThrow().status()).isEqualTo(PortalLinkStatus.PENDING_SEND);
        useCase.sendPending();

        PortalLink stored = repository.findById(pending.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(PortalLinkStatus.NOT_SENT);
        assertThat(stored.statusReason()).isEqualTo(PortalLinkStatusReason.SEND_FAILED);
        assertThat(stored.tokenHash()).isNull();
        assertThat(tokens.get()).isEqualTo(3);
        verify(notifier).notifyNotSent(stored);
    }

    @Test
    void taskAlreadyGone_revokesWithoutSending() {
        when(taskLookup.findOpenTask("task-1")).thenReturn(Optional.empty());

        useCase.sendPending();

        verify(sender, never()).send(any(), anyString(), any());
        assertThat(repository.findById(pending.id()).orElseThrow().statusReason())
                .isEqualTo(PortalLinkStatusReason.TASK_GONE);
    }

    // ---- TODO §79 (D5) : un lien pris par un autre exécutant n'est pas envoyé une seconde fois ----------------------

    @Test
    void aLinkClaimedByAnotherSenderIsNeitherSentNorTouched() {
        var spy = org.mockito.Mockito.spy(repository);
        org.mockito.Mockito.doReturn(false).when(spy).claimForSend(any(), any(), any());
        var contender = new SendPortalInvitesUseCaseImpl(spy, tokenPort, sender, taskLookup, dossierRepository, notifier,
                Clock.fixed(NOW, ZoneOffset.UTC), 3, 50);

        assertThat(contender.sendPending()).isZero();

        verify(sender, never()).send(any(), anyString(), any());
        verify(spy, never()).transition(any(), any());
        assertThat(repository.findById(pending.id()).orElseThrow().status()).isEqualTo(PortalLinkStatus.PENDING_SEND);
    }

    @Test
    void theClaimIsTakenBeforeTheEmailIsSentAndForTheDocumentedLease() {
        var spy = org.mockito.Mockito.spy(repository);
        var sendingUseCase = new SendPortalInvitesUseCaseImpl(spy, tokenPort, sender, taskLookup, dossierRepository, notifier,
                Clock.fixed(NOW, ZoneOffset.UTC), 3, 50);

        assertThat(sendingUseCase.sendPending()).isEqualTo(1);

        var order = org.mockito.Mockito.inOrder(spy, sender);
        order.verify(spy).claimForSend(any(), eq(NOW), eq(SendPortalInvitesUseCaseImpl.SEND_LEASE));
        order.verify(sender).send(any(), anyString(), any());
    }
}
