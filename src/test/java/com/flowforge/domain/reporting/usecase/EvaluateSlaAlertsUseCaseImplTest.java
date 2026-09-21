package com.flowforge.domain.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.reporting.UnitOfWork;
import com.flowforge.domain.reporting.model.SlaAlertCandidate;
import com.flowforge.domain.reporting.model.SlaAlertState;
import com.flowforge.domain.reporting.model.SlaStatus;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.port.out.SlaAlertCandidatePort;
import com.flowforge.domain.reporting.port.out.SlaAlertStateRepository;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EvaluateSlaAlertsUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final UnitOfWork INLINE = Runnable::run;

    private TenantSlaSettingsRepository settingsRepository;
    private SlaAlertCandidatePort candidatePort;
    private SlaAlertStateRepository stateRepository;
    private SendNotificationUseCase send;
    private NotificationWebhookRepository webhookRepository;
    private DossierWorkflowLinkRepository linkRepository;
    private WorkflowParticipantPort participantPort;
    private EvaluateSlaAlertsUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        settingsRepository = mock(TenantSlaSettingsRepository.class);
        candidatePort = mock(SlaAlertCandidatePort.class);
        stateRepository = mock(SlaAlertStateRepository.class);
        send = mock(SendNotificationUseCase.class);
        webhookRepository = mock(NotificationWebhookRepository.class);
        linkRepository = mock(DossierWorkflowLinkRepository.class);
        participantPort = mock(WorkflowParticipantPort.class);
        useCase = new EvaluateSlaAlertsUseCaseImpl(
                settingsRepository,
                candidatePort,
                stateRepository,
                send,
                webhookRepository,
                linkRepository,
                participantPort);
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(linkRepository.findByDossierIdOrderBySequence(any())).thenReturn(List.of());
    }

    @Test
    void scan_noSettingsOrDisabledOrNotDue_doesNothing() {
        when(settingsRepository.findByTenantId(TENANT)).thenReturn(Optional.empty());
        assertThat(useCase.scan(TENANT, NOW, INLINE)).isZero();

        when(settingsRepository.findByTenantId(TENANT)).thenReturn(Optional.of(settings(false, null, List.of(), List.of())));
        assertThat(useCase.scan(TENANT, NOW, INLINE)).isZero();

        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, NOW.minus(Duration.ofMinutes(30)), List.of(), List.of())));
        assertThat(useCase.scan(TENANT, NOW, INLINE)).isZero();

        verifyNoCandidates();
    }

    @Test
    void scan_sameStatus_doesNotNotify() {
        UUID id = UUID.randomUUID();
        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, null, List.of("a@b.co"), List.of())));
        when(candidatePort.listCandidates(TENANT)).thenReturn(List.of(candidate(id, 9, false)));
        when(stateRepository.find(TENANT, id))
                .thenReturn(Optional.of(new SlaAlertState(TENANT, id, SlaStatus.AT_RISK, NOW.minus(Duration.ofHours(1)))));

        useCase.scan(TENANT, NOW, INLINE);

        verify(send, never()).send(any(), any());
        verify(stateRepository, never()).save(any());
        assertScanClockAdvanced();
    }

    @Test
    void scan_absentAtRisk_notifiesOnceThenStores() {
        UUID id = UUID.randomUUID();
        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, NOW.minus(Duration.ofMinutes(60)), List.of("a@b.co"), List.of())));
        when(candidatePort.listCandidates(TENANT)).thenReturn(List.of(candidate(id, 9, false)));
        when(stateRepository.find(TENANT, id)).thenReturn(Optional.empty());

        useCase.scan(TENANT, NOW, INLINE);

        NotificationEvent event = sentOnce();
        assertThat(event.channels()).containsExactly(NotificationChannel.EMAIL);
        assertThat(event.subject()).contains("AT_RISK").doesNotContain("BREACHED");
        assertThat(event.body()).contains("DOS-1").contains("CLAIM").contains("AT_RISK");
        assertThat(event.payload()).containsEntry("type", "SLA_THRESHOLD_CROSSED");
        assertThat(event.payload()).containsEntry("dossierId", id.toString());
        assertStored(id, SlaStatus.AT_RISK);
    }

    @Test
    void scan_atRiskThenBreached_sendsOnlyBreached() {
        UUID id = UUID.randomUUID();
        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, null, List.of("a@b.co"), List.of())));
        when(candidatePort.listCandidates(TENANT)).thenReturn(List.of(candidate(id, 11, false)));
        when(stateRepository.find(TENANT, id))
                .thenReturn(Optional.of(new SlaAlertState(TENANT, id, SlaStatus.AT_RISK, NOW.minus(Duration.ofHours(2)))));

        useCase.scan(TENANT, NOW, INLINE);

        NotificationEvent event = sentOnce();
        assertThat(event.subject()).contains("BREACHED").doesNotContain("AT_RISK");
        assertThat(event.body()).contains("BREACHED");
        assertStored(id, SlaStatus.BREACHED);
    }

    @Test
    void scan_missedWindow_sendsOnlyBreached() {
        UUID id = UUID.randomUUID();
        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, null, List.of("a@b.co"), List.of())));
        when(candidatePort.listCandidates(TENANT)).thenReturn(List.of(candidate(id, 30, false)));
        when(stateRepository.find(TENANT, id))
                .thenReturn(Optional.of(new SlaAlertState(TENANT, id, SlaStatus.ON_TRACK, NOW)));

        useCase.scan(TENANT, NOW, INLINE);

        NotificationEvent event = sentOnce();
        assertThat(event.subject()).contains("BREACHED").doesNotContain("AT_RISK");
        assertStored(id, SlaStatus.BREACHED);
    }

    @Test
    void scan_returnToOnTrackAndFirstOnTrack_storeWithoutAlert() {
        UUID back = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, null, List.of("a@b.co"), List.of())));
        when(candidatePort.listCandidates(TENANT)).thenReturn(List.of(candidate(back, 1, false), candidate(fresh, 1, false)));
        when(stateRepository.find(TENANT, back))
                .thenReturn(Optional.of(new SlaAlertState(TENANT, back, SlaStatus.BREACHED, NOW)));
        when(stateRepository.find(TENANT, fresh)).thenReturn(Optional.empty());

        useCase.scan(TENANT, NOW, INLINE);

        verify(send, never()).send(any(), any());
        verify(stateRepository).save(new SlaAlertState(TENANT, back, SlaStatus.ON_TRACK, NOW));
        verify(stateRepository).save(new SlaAlertState(TENANT, fresh, SlaStatus.ON_TRACK, NOW));
    }

    @Test
    void scan_reentryAfterOnTrack_alertsAgain() {
        UUID id = UUID.randomUUID();
        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, null, List.of("a@b.co"), List.of())));
        when(candidatePort.listCandidates(TENANT)).thenReturn(List.of(candidate(id, 9, false)));
        when(stateRepository.find(TENANT, id))
                .thenReturn(Optional.of(new SlaAlertState(TENANT, id, SlaStatus.ON_TRACK, NOW)));

        useCase.scan(TENANT, NOW, INLINE);

        assertThat(sentOnce().subject()).contains("AT_RISK");
    }

    @Test
    void scan_unscored_isIgnored() {
        UUID id = UUID.randomUUID();
        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, null, List.of("a@b.co"), List.of())));
        when(candidatePort.listCandidates(TENANT))
                .thenReturn(List.of(new SlaAlertCandidate(id, "DOS-1", "CLAIM", NOW, null, NOW, false, null)));

        useCase.scan(TENANT, NOW, INLINE);

        verify(send, never()).send(any(), any());
        verify(stateRepository, never()).save(any());
    }

    @Test
    void scan_closedDossier_neverAtRisk_andBreachedStillAlerts() {
        UUID calm = UUID.randomUUID();
        UUID late = UUID.randomUUID();
        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, null, List.of("a@b.co"), List.of())));
        when(candidatePort.listCandidates(TENANT))
                .thenReturn(List.of(candidate(calm, 9, true), candidate(late, 30, true)));
        when(stateRepository.find(eq(TENANT), any())).thenReturn(Optional.empty());

        useCase.scan(TENANT, NOW, INLINE);

        verify(stateRepository).save(new SlaAlertState(TENANT, calm, SlaStatus.ON_TRACK, NOW));
        assertStored(late, SlaStatus.BREACHED);
        NotificationEvent event = sentOnce();
        assertThat(event.subject()).contains("BREACHED");
    }

    @Test
    void scan_fanOutOneEventPerRecipientAndSkipsDisabledWebhook() {
        UUID id = UUID.randomUUID();
        UUID enabled = UUID.randomUUID();
        UUID disabled = UUID.randomUUID();
        when(settingsRepository.findByTenantId(TENANT)).thenReturn(Optional.of(
                settings(true, null, List.of("a@b.co", "c@d.fr"), List.of(enabled, disabled))));
        when(candidatePort.listCandidates(TENANT)).thenReturn(List.of(candidate(id, 9, false)));
        when(stateRepository.find(TENANT, id)).thenReturn(Optional.empty());
        NotificationWebhookConfig enabledConfig = webhook(true);
        NotificationWebhookConfig disabledConfig = webhook(false);
        when(webhookRepository.findByIdAndTenantId(enabled, TENANT)).thenReturn(Optional.of(enabledConfig));
        when(webhookRepository.findByIdAndTenantId(disabled, TENANT)).thenReturn(Optional.of(disabledConfig));
        DossierWorkflowLink link = mock(DossierWorkflowLink.class);
        when(linkRepository.findByDossierIdOrderBySequence(id)).thenReturn(List.of(link, link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("u1", "u1", "u2"));

        List<String> trace = new ArrayList<>();
        UnitOfWork watching = action -> {
            trace.add("begin");
            action.run();
            trace.add("end");
        };
        when(send.send(any(), eq(TENANT))).thenAnswer(inv -> {
            trace.add("send");
            return List.of();
        });
        when(stateRepository.save(any())).thenAnswer(inv -> {
            trace.add("save");
            return inv.getArgument(0);
        });

        useCase.scan(TENANT, NOW, watching);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(send, times(5)).send(captor.capture(), eq(TENANT));
        assertThat(captor.getAllValues().stream().filter(e -> e.channels().contains(NotificationChannel.EMAIL)))
                .hasSize(2);
        assertThat(captor.getAllValues().stream().filter(e -> e.channels().contains(NotificationChannel.WEBHOOK)))
                .hasSize(1);
        assertThat(captor.getAllValues().stream().filter(e -> e.channels().contains(NotificationChannel.BROWSER)))
                .extracting(NotificationEvent::userId)
                .containsExactly("u1", "u2");
        assertThat(trace).containsExactly("begin", "send", "send", "send", "send", "send", "save", "end");
    }

    @Test
    void scan_failureRollsBackThatDossierAndStillAdvancesClock() {
        UUID broken = UUID.randomUUID();
        UUID fine = UUID.randomUUID();
        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, null, List.of("a@b.co"), List.of())));
        when(candidatePort.listCandidates(TENANT)).thenReturn(List.of(candidate(broken, 9, false), candidate(fine, 9, false)));
        when(stateRepository.find(eq(TENANT), any())).thenReturn(Optional.empty());
        when(send.send(any(), eq(TENANT)))
                .thenThrow(new RuntimeException("outbox down"))
                .thenReturn(List.of());

        assertThat(useCase.scan(TENANT, NOW, INLINE)).isEqualTo(1);

        verify(stateRepository, never()).save(new SlaAlertState(TENANT, broken, SlaStatus.AT_RISK, NOW));
        verify(stateRepository).save(new SlaAlertState(TENANT, fine, SlaStatus.AT_RISK, NOW));
        assertScanClockAdvanced();
    }

    @Test
    void scan_settingsChangedConcurrently_finalSavePreservesTheNewerAggregate() {
        UUID id = UUID.randomUUID();
        UUID webhook = UUID.randomUUID();
        TenantSlaSettings atScanStart = settings(true, null, List.of("a@b.co"), List.of(webhook));
        // The admin disables alerting and clears the webhook while the scan is still running.
        TenantSlaSettings afterConcurrentPut = settings(false, null, List.of(), List.of());
        when(settingsRepository.findByTenantId(TENANT)).thenReturn(Optional.of(atScanStart), Optional.of(afterConcurrentPut));
        when(candidatePort.listCandidates(TENANT)).thenReturn(List.of(candidate(id, 9, false)));
        when(stateRepository.find(TENANT, id)).thenReturn(Optional.empty());
        NotificationWebhookConfig enabledWebhook = webhook(true);
        when(webhookRepository.findByIdAndTenantId(webhook, TENANT)).thenReturn(Optional.of(enabledWebhook));

        useCase.scan(TENANT, NOW, INLINE);

        ArgumentCaptor<TenantSlaSettings> captor = ArgumentCaptor.forClass(TenantSlaSettings.class);
        verify(settingsRepository).save(captor.capture());
        TenantSlaSettings saved = captor.getValue();
        assertThat(saved.lastAlertScanAt()).isEqualTo(NOW);
        assertThat(saved.alertingEnabled()).isFalse();
        assertThat(saved.alertEmails()).isEmpty();
        assertThat(saved.alertWebhookIds()).isEmpty();
    }

    private void verifyNoCandidates() {
        verify(candidatePort, never()).listCandidates(any());
        verify(send, never()).send(any(), any());
    }

    private NotificationEvent sentOnce() {
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(send).send(captor.capture(), eq(TENANT));
        return captor.getValue();
    }

    private void assertStored(UUID id, SlaStatus status) {
        verify(stateRepository).save(new SlaAlertState(TENANT, id, status, NOW));
    }

    private void assertScanClockAdvanced() {
        ArgumentCaptor<TenantSlaSettings> captor = ArgumentCaptor.forClass(TenantSlaSettings.class);
        verify(settingsRepository).save(captor.capture());
        assertThat(captor.getValue().lastAlertScanAt()).isEqualTo(NOW);
    }

    private static SlaAlertCandidate candidate(UUID id, int elapsedHours, boolean closed) {
        Instant created = NOW.minus(Duration.ofHours(elapsedHours));
        return new SlaAlertCandidate(
                id, "DOS-1", "CLAIM", created, closed ? NOW : null, NOW, closed, 10);
    }

    private static TenantSlaSettings settings(
            boolean enabled, Instant lastScan, List<String> emails, List<UUID> webhooks) {
        return new TenantSlaSettings(
                TENANT,
                95,
                enabled,
                60,
                emails,
                webhooks,
                false,
                24,
                30,
                enabled ? NOW.minus(Duration.ofDays(2)) : null,
                lastScan,
                null,
                null,
                NOW,
                "admin");
    }

    private static NotificationWebhookConfig webhook(boolean enabled) {
        NotificationWebhookConfig config = mock(NotificationWebhookConfig.class);
        when(config.enabled()).thenReturn(enabled);
        return config;
    }
}
