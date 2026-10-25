package be.myrmacta.domain.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.notification.model.NotificationWebhookConfig;
import be.myrmacta.domain.notification.port.out.NotificationWebhookRepository;
import be.myrmacta.domain.reporting.model.SlaAlertCandidate;
import be.myrmacta.domain.reporting.model.SlaAlertState;
import be.myrmacta.domain.reporting.model.SlaStatus;
import be.myrmacta.domain.reporting.model.TenantSlaSettings;
import be.myrmacta.domain.reporting.port.in.UpsertTenantSlaSettingsUseCase;
import be.myrmacta.domain.reporting.port.out.SlaAlertCandidatePort;
import be.myrmacta.domain.reporting.port.out.SlaAlertStateRepository;
import be.myrmacta.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpsertTenantSlaSettingsUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-03-15T00:00:00Z");

    private TenantSlaSettingsRepository repository;
    private NotificationWebhookRepository webhookRepository;
    private SlaAlertCandidatePort candidatePort;
    private SlaAlertStateRepository alertStateRepository;
    private UpsertTenantSlaSettingsUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(TenantSlaSettingsRepository.class);
        webhookRepository = mock(NotificationWebhookRepository.class);
        candidatePort = mock(SlaAlertCandidatePort.class);
        alertStateRepository = mock(SlaAlertStateRepository.class);
        useCase = new UpsertTenantSlaSettingsUseCaseImpl(
                repository,
                new TenantAdminGuard(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                webhookRepository,
                candidatePort,
                alertStateRepository);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByTenantId(TENANT)).thenReturn(Optional.empty());
    }

    @Test
    void upsert_adminPersistsSettings() {
        TenantSlaSettings saved = useCase.upsert(
                new UpsertTenantSlaSettingsUseCase.Command(TENANT, 90),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(saved.tenantId()).isEqualTo(TENANT);
        assertThat(saved.complianceTargetPercent()).isEqualTo(90);
        assertThat(saved.updatedBy()).isEqualTo("admin");
        assertThat(saved.updatedAt()).isEqualTo(NOW);
        assertThat(saved.alertingEnabled()).isFalse();
        assertThat(saved.alertIntervalMinutes()).isEqualTo(60);
        assertThat(saved.biExportEnabled()).isFalse();
        assertThat(saved.biIntervalHours()).isEqualTo(24);
        assertThat(saved.biRetentionDays()).isEqualTo(30);

        ArgumentCaptor<TenantSlaSettings> captor = ArgumentCaptor.forClass(TenantSlaSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isEqualTo(saved);
        verifyNoInteractions(candidatePort, alertStateRepository);
    }

    @Test
    void upsert_rejectsPercentOutsideOneToHundred() {
        assertThatThrownBy(() -> useCase.upsert(
                        new UpsertTenantSlaSettingsUseCase.Command(TENANT, 0),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complianceTargetPercent");
        assertThatThrownBy(() -> useCase.upsert(
                        new UpsertTenantSlaSettingsUseCase.Command(TENANT, 101),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void upsert_rejectsOutOfRangeCadenceAndRetentionAndEmails() {
        assertThatThrownBy(() -> useCase.upsert(command(true, 14, List.of(), List.of(), false, 24, 30), admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alertIntervalMinutes");
        assertThatThrownBy(() -> useCase.upsert(command(true, 10081, List.of(), List.of(), false, 24, 30), admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alertIntervalMinutes");
        assertThatThrownBy(() -> useCase.upsert(command(false, 60, List.of(), List.of(), true, 0, 30), admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("biIntervalHours");
        assertThatThrownBy(() -> useCase.upsert(command(false, 60, List.of(), List.of(), true, 721, 30), admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("biIntervalHours");
        assertThatThrownBy(() -> useCase.upsert(command(false, 60, List.of(), List.of(), true, 24, 0), admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("biRetentionDays");
        assertThatThrownBy(() -> useCase.upsert(command(false, 60, List.of(), List.of(), true, 24, 366), admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("biRetentionDays");
        assertThatThrownBy(() -> useCase.upsert(
                        command(true, 60, List.of("not-an-email"), List.of(), false, 24, 30), admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alert email");

        verifyNoInteractions(repository);
    }

    @Test
    void upsert_normalizesEmailsAndRejectsMoreThanTwenty() {
        TenantSlaSettings saved = useCase.upsert(
                command(true, 15, List.of(" a@b.co ", "a@b.co", "c@d.fr"), List.of(), false, 1, 1), admin());

        assertThat(saved.alertEmails()).containsExactly("a@b.co", "c@d.fr");

        List<String> tooMany = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "u" + i + "@b.co")
                .toList();
        assertThatThrownBy(() -> useCase.upsert(command(true, 60, tooMany, List.of(), false, 24, 30), admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alert emails");
    }

    @Test
    void upsert_requiresEnabledWebhookOfTheTenant() {
        UUID enabled = UUID.randomUUID();
        UUID disabled = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        NotificationWebhookConfig enabledConfig = webhook(true);
        NotificationWebhookConfig disabledConfig = webhook(false);
        when(webhookRepository.findByIdAndTenantId(enabled, TENANT)).thenReturn(Optional.of(enabledConfig));
        when(webhookRepository.findByIdAndTenantId(disabled, TENANT)).thenReturn(Optional.of(disabledConfig));
        when(webhookRepository.findByIdAndTenantId(missing, TENANT)).thenReturn(Optional.empty());

        TenantSlaSettings saved = useCase.upsert(
                command(false, 60, List.of(), List.of(enabled, enabled), false, 24, 30), admin());
        assertThat(saved.alertWebhookIds()).containsExactly(enabled);

        assertThatThrownBy(() -> useCase.upsert(
                        command(false, 60, List.of(), List.of(disabled), false, 24, 30), admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("webhook");
        assertThatThrownBy(() -> useCase.upsert(
                        command(false, 60, List.of(), List.of(missing), false, 24, 30), admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("webhook");
        verify(repository).save(any());
    }

    @Test
    void upsert_snapshotOnAlertingActivationSkipsUnscoredAndDoesNotNotify() {
        UUID scored = UUID.randomUUID();
        UUID unscored = UUID.randomUUID();
        when(candidatePort.listCandidates(TENANT)).thenReturn(List.of(
                new SlaAlertCandidate(
                        scored, "DOS-1", "CLAIM", NOW.minus(Duration.ofHours(5)), null, NOW, false, 1),
                new SlaAlertCandidate(unscored, "DOS-2", "LOG", NOW, null, NOW, false, null)));

        TenantSlaSettings saved = useCase.upsert(command(true, 60, List.of(), List.of(), false, 24, 30), admin());

        assertThat(saved.alertingEnabled()).isTrue();
        assertThat(saved.alertingEnabledAt()).isEqualTo(NOW);
        assertThat(saved.lastAlertScanAt()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SlaAlertState>> captor = ArgumentCaptor.forClass(List.class);
        verify(alertStateRepository).replaceAll(org.mockito.ArgumentMatchers.eq(TENANT), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).dossierId()).isEqualTo(scored);
        assertThat(captor.getValue().get(0).lastStatus()).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void upsert_reactivationReplacesSnapshotAndKeepingAlertsDoesNot() {
        UUID scored = UUID.randomUUID();
        when(candidatePort.listCandidates(TENANT)).thenReturn(List.of(
                new SlaAlertCandidate(scored, "DOS-1", "CLAIM", NOW, null, NOW, false, 48)));
        TenantSlaSettings disabled = existing(false, NOW.minus(Duration.ofHours(2)));
        when(repository.findByTenantId(TENANT)).thenReturn(Optional.of(disabled));

        useCase.upsert(command(true, 60, List.of(), List.of(), false, 24, 30), admin());
        verify(alertStateRepository).replaceAll(org.mockito.ArgumentMatchers.eq(TENANT), any());

        TenantSlaSettings enabled = existing(true, NOW.minus(Duration.ofMinutes(10)));
        when(repository.findByTenantId(TENANT)).thenReturn(Optional.of(enabled));
        TenantSlaSettings kept = useCase.upsert(command(true, 60, List.of(), List.of(), false, 24, 30), admin());
        assertThat(kept.lastAlertScanAt()).isEqualTo(NOW.minus(Duration.ofMinutes(10)));
        assertThat(kept.alertingEnabledAt()).isEqualTo(enabled.alertingEnabledAt());
        verify(alertStateRepository).replaceAll(any(), any());
    }

    @Test
    void upsert_enablingBiSetsAnchorWithoutImmediateExportClock() {
        TenantSlaSettings saved = useCase.upsert(command(false, 60, List.of(), List.of(), true, 24, 30), admin());
        assertThat(saved.biExportEnabled()).isTrue();
        assertThat(saved.biEnabledAt()).isEqualTo(NOW);
        assertThat(saved.lastBiExportAt()).isNull();
        verify(alertStateRepository, never()).replaceAll(any(), any());
    }

    @Test
    void upsert_omittedFieldsKeepStoredAlertingAndRecipients() {
        UUID webhook = UUID.randomUUID();
        when(repository.findByTenantId(TENANT)).thenReturn(Optional.of(new TenantSlaSettings(
                TENANT,
                90,
                true,
                15,
                List.of("ops@example.com"),
                List.of(webhook),
                true,
                2,
                7,
                NOW.minus(Duration.ofDays(1)),
                NOW.minus(Duration.ofHours(1)),
                NOW.minus(Duration.ofDays(1)),
                null,
                NOW.minus(Duration.ofDays(2)),
                "admin")));

        TenantSlaSettings saved = useCase.upsert(
                new UpsertTenantSlaSettingsUseCase.Command(TENANT, 92, null, null, null, null, null, null, null),
                admin());

        assertThat(saved.complianceTargetPercent()).isEqualTo(92);
        assertThat(saved.alertingEnabled()).isTrue();
        assertThat(saved.alertEmails()).containsExactly("ops@example.com");
        assertThat(saved.alertWebhookIds()).containsExactly(webhook);
        assertThat(saved.biExportEnabled()).isTrue();
        assertThat(saved.biIntervalHours()).isEqualTo(2);
        assertThat(saved.biRetentionDays()).isEqualTo(7);
        assertThat(saved.alertIntervalMinutes()).isEqualTo(15);
        assertThat(saved.lastAlertScanAt()).isEqualTo(NOW.minus(Duration.ofHours(1)));
        verify(alertStateRepository, never()).replaceAll(any(), any());
        verifyNoInteractions(webhookRepository);
    }

    @Test
    void upsert_nonAdminDeniedWithoutRepositoryCall() {
        assertThatThrownBy(() -> useCase.upsert(
                        new UpsertTenantSlaSettingsUseCase.Command(TENANT, 90),
                        AccessContext.user("manager", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
    }

    private static AccessContext.User admin() {
        return AccessContext.user("admin", Set.of("TENANT_ADMIN"));
    }

    private static UpsertTenantSlaSettingsUseCase.Command command(
            boolean alerting,
            int alertMinutes,
            List<String> emails,
            List<UUID> webhooks,
            boolean bi,
            int biHours,
            int retentionDays) {
        return new UpsertTenantSlaSettingsUseCase.Command(
                TENANT, 90, alerting, alertMinutes, emails, webhooks, bi, biHours, retentionDays);
    }

    private static TenantSlaSettings existing(boolean alerting, Instant lastScan) {
        return new TenantSlaSettings(
                TENANT,
                90,
                alerting,
                60,
                List.of(),
                List.of(),
                false,
                24,
                30,
                alerting ? NOW.minus(Duration.ofDays(1)) : null,
                lastScan,
                null,
                null,
                NOW.minus(Duration.ofDays(2)),
                "admin");
    }

    private static NotificationWebhookConfig webhook(boolean enabled) {
        NotificationWebhookConfig config = mock(NotificationWebhookConfig.class);
        when(config.enabled()).thenReturn(enabled);
        return config;
    }
}
