package com.flowforge.domain.reporting.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.reporting.model.SlaAlertCandidate;
import com.flowforge.domain.reporting.model.SlaAlertState;
import com.flowforge.domain.reporting.model.SlaClock;
import com.flowforge.domain.reporting.model.SlaStatus;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.port.in.UpsertTenantSlaSettingsUseCase;
import com.flowforge.domain.reporting.port.out.SlaAlertCandidatePort;
import com.flowforge.domain.reporting.port.out.SlaAlertStateRepository;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class UpsertTenantSlaSettingsUseCaseImpl implements UpsertTenantSlaSettingsUseCase {

    private final TenantSlaSettingsRepository repository;
    private final TenantAdminGuard tenantAdminGuard;
    private final Clock clock;
    private final NotificationWebhookRepository webhookRepository;
    private final SlaAlertCandidatePort candidatePort;
    private final SlaAlertStateRepository alertStateRepository;

    public UpsertTenantSlaSettingsUseCaseImpl(
            TenantSlaSettingsRepository repository,
            TenantAdminGuard tenantAdminGuard,
            Clock clock,
            NotificationWebhookRepository webhookRepository,
            SlaAlertCandidatePort candidatePort,
            SlaAlertStateRepository alertStateRepository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.webhookRepository = Objects.requireNonNull(webhookRepository, "webhookRepository");
        this.candidatePort = Objects.requireNonNull(candidatePort, "candidatePort");
        this.alertStateRepository = Objects.requireNonNull(alertStateRepository, "alertStateRepository");
    }

    @Override
    public TenantSlaSettings upsert(Command command, AccessContext.User actor) {
        Objects.requireNonNull(command, "command");
        tenantAdminGuard.requireTenantAdmin(actor);
        boolean alertingEnabled = Boolean.TRUE.equals(command.alertingEnabled());
        int alertIntervalMinutes = command.alertIntervalMinutes() == null
                ? TenantSlaSettings.DEFAULT_ALERT_INTERVAL_MINUTES
                : command.alertIntervalMinutes();
        List<String> alertEmails = command.alertEmails() == null ? List.of() : command.alertEmails();
        List<UUID> requestedWebhooks =
                command.alertWebhookIds() == null ? List.of() : command.alertWebhookIds();
        boolean biExportEnabled = Boolean.TRUE.equals(command.biExportEnabled());
        int biIntervalHours = command.biIntervalHours() == null
                ? TenantSlaSettings.DEFAULT_BI_INTERVAL_HOURS
                : command.biIntervalHours();
        int biRetentionDays = command.biRetentionDays() == null
                ? TenantSlaSettings.DEFAULT_BI_RETENTION_DAYS
                : command.biRetentionDays();
        // Validates bounds and recipients before any persistence, including a rejected percent.
        new TenantSlaSettings(
                command.tenantId(),
                command.complianceTargetPercent(),
                alertingEnabled,
                alertIntervalMinutes,
                alertEmails,
                requestedWebhooks,
                biExportEnabled,
                biIntervalHours,
                biRetentionDays,
                null,
                null,
                null,
                null,
                Instant.EPOCH,
                actor.userId());

        List<UUID> webhookIds = null;
        if (command.alertWebhookIds() != null) {
            webhookIds = TenantSlaSettings.normalizeWebhookIds(command.alertWebhookIds());
            for (UUID webhookId : webhookIds) {
                NotificationWebhookConfig config = webhookRepository
                        .findByIdAndTenantId(webhookId, command.tenantId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "alert webhook not found or disabled: " + webhookId));
                if (!config.enabled()) {
                    throw new IllegalArgumentException("alert webhook not found or disabled: " + webhookId);
                }
            }
        }

        Instant now = clock.instant();
        Optional<TenantSlaSettings> existing = repository.findByTenantId(command.tenantId());
        if (command.alertingEnabled() == null) {
            alertingEnabled = existing.map(TenantSlaSettings::alertingEnabled).orElse(false);
        }
        if (command.alertIntervalMinutes() == null) {
            alertIntervalMinutes = existing
                    .map(TenantSlaSettings::alertIntervalMinutes)
                    .orElse(TenantSlaSettings.DEFAULT_ALERT_INTERVAL_MINUTES);
        }
        if (command.alertEmails() == null) {
            alertEmails = existing.map(TenantSlaSettings::alertEmails).orElse(List.of());
        }
        if (webhookIds == null) {
            webhookIds = existing.map(TenantSlaSettings::alertWebhookIds).orElse(List.of());
        }
        if (command.biExportEnabled() == null) {
            biExportEnabled = existing.map(TenantSlaSettings::biExportEnabled).orElse(false);
        }
        if (command.biIntervalHours() == null) {
            biIntervalHours = existing
                    .map(TenantSlaSettings::biIntervalHours)
                    .orElse(TenantSlaSettings.DEFAULT_BI_INTERVAL_HOURS);
        }
        if (command.biRetentionDays() == null) {
            biRetentionDays = existing
                    .map(TenantSlaSettings::biRetentionDays)
                    .orElse(TenantSlaSettings.DEFAULT_BI_RETENTION_DAYS);
        }
        boolean wasAlerting = existing.map(TenantSlaSettings::alertingEnabled).orElse(false);
        boolean wasBi = existing.map(TenantSlaSettings::biExportEnabled).orElse(false);
        Instant alertingEnabledAt = existing.map(TenantSlaSettings::alertingEnabledAt).orElse(null);
        Instant lastAlertScanAt = existing.map(TenantSlaSettings::lastAlertScanAt).orElse(null);
        Instant biEnabledAt = existing.map(TenantSlaSettings::biEnabledAt).orElse(null);
        Instant lastBiExportAt = existing.map(TenantSlaSettings::lastBiExportAt).orElse(null);

        boolean activatingAlerts = alertingEnabled && !wasAlerting;
        if (activatingAlerts) {
            alertingEnabledAt = now;
            lastAlertScanAt = null;
            snapshot(command.tenantId(), now);
        }
        if (biExportEnabled && !wasBi) {
            biEnabledAt = now;
        }

        TenantSlaSettings settings = new TenantSlaSettings(
                command.tenantId(),
                command.complianceTargetPercent(),
                alertingEnabled,
                alertIntervalMinutes,
                alertEmails,
                webhookIds,
                biExportEnabled,
                biIntervalHours,
                biRetentionDays,
                alertingEnabledAt,
                lastAlertScanAt,
                biEnabledAt,
                lastBiExportAt,
                now,
                actor.userId());
        return repository.save(settings);
    }

    private void snapshot(String tenantId, Instant now) {
        List<SlaAlertState> states = new ArrayList<>();
        for (SlaAlertCandidate candidate : candidatePort.listCandidates(tenantId)) {
            if (candidate.slaTargetHours() == null) {
                continue;
            }
            SlaStatus status = SlaStatus.of(
                    SlaClock.elapsed(
                            candidate.createdAt(),
                            candidate.closedAt(),
                            candidate.updatedAt(),
                            now,
                            candidate.closed()),
                    candidate.slaTargetHours(),
                    candidate.closed());
            if (status == null) {
                continue;
            }
            states.add(new SlaAlertState(tenantId, candidate.id(), status, now));
        }
        alertStateRepository.replaceAll(tenantId, states);
    }
}
