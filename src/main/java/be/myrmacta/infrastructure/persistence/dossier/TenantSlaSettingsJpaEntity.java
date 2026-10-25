package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.reporting.model.TenantSlaSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_sla_settings")
class TenantSlaSettingsJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "compliance_target_percent", nullable = false)
    private int complianceTargetPercent;

    @Column(name = "alerting_enabled", nullable = false)
    private boolean alertingEnabled;

    @Column(name = "alert_interval_minutes", nullable = false)
    private int alertIntervalMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alert_emails", nullable = false, columnDefinition = "json")
    private List<String> alertEmails = List.of();

    @Column(name = "bi_export_enabled", nullable = false)
    private boolean biExportEnabled;

    @Column(name = "bi_interval_hours", nullable = false)
    private int biIntervalHours;

    @Column(name = "bi_retention_days", nullable = false)
    private int biRetentionDays;

    @Column(name = "alerting_enabled_at")
    private Instant alertingEnabledAt;

    @Column(name = "last_alert_scan_at")
    private Instant lastAlertScanAt;

    @Column(name = "bi_enabled_at")
    private Instant biEnabledAt;

    @Column(name = "last_bi_export_at")
    private Instant lastBiExportAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    protected TenantSlaSettingsJpaEntity() {
        // JPA
    }

    private TenantSlaSettingsJpaEntity(
            String tenantId,
            int complianceTargetPercent,
            boolean alertingEnabled,
            int alertIntervalMinutes,
            List<String> alertEmails,
            boolean biExportEnabled,
            int biIntervalHours,
            int biRetentionDays,
            Instant alertingEnabledAt,
            Instant lastAlertScanAt,
            Instant biEnabledAt,
            Instant lastBiExportAt,
            Instant updatedAt,
            String updatedBy) {
        this.tenantId = tenantId;
        this.complianceTargetPercent = complianceTargetPercent;
        this.alertingEnabled = alertingEnabled;
        this.alertIntervalMinutes = alertIntervalMinutes;
        this.alertEmails = List.copyOf(alertEmails);
        this.biExportEnabled = biExportEnabled;
        this.biIntervalHours = biIntervalHours;
        this.biRetentionDays = biRetentionDays;
        this.alertingEnabledAt = alertingEnabledAt;
        this.lastAlertScanAt = lastAlertScanAt;
        this.biEnabledAt = biEnabledAt;
        this.lastBiExportAt = lastBiExportAt;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    static TenantSlaSettingsJpaEntity fromDomain(TenantSlaSettings settings) {
        return new TenantSlaSettingsJpaEntity(
                settings.tenantId(),
                settings.complianceTargetPercent(),
                settings.alertingEnabled(),
                settings.alertIntervalMinutes(),
                settings.alertEmails(),
                settings.biExportEnabled(),
                settings.biIntervalHours(),
                settings.biRetentionDays(),
                settings.alertingEnabledAt(),
                settings.lastAlertScanAt(),
                settings.biEnabledAt(),
                settings.lastBiExportAt(),
                settings.updatedAt(),
                settings.updatedBy());
    }

    TenantSlaSettings toDomain(List<UUID> webhookIds) {
        return new TenantSlaSettings(
                tenantId,
                complianceTargetPercent,
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
                updatedAt,
                updatedBy);
    }
}
