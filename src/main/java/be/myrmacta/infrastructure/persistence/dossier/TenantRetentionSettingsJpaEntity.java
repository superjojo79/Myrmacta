package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.model.TenantRetentionSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tenant_retention_settings")
class TenantRetentionSettingsJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "default_retention_days", nullable = false)
    private int defaultRetentionDays;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    protected TenantRetentionSettingsJpaEntity() {
        // JPA
    }

    private TenantRetentionSettingsJpaEntity(
            String tenantId, int defaultRetentionDays, Instant updatedAt, String updatedBy) {
        this.tenantId = tenantId;
        this.defaultRetentionDays = defaultRetentionDays;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    static TenantRetentionSettingsJpaEntity fromDomain(TenantRetentionSettings settings) {
        return new TenantRetentionSettingsJpaEntity(
                settings.tenantId(),
                settings.defaultRetentionDays(),
                settings.updatedAt(),
                settings.updatedBy());
    }

    TenantRetentionSettings toDomain() {
        return new TenantRetentionSettings(tenantId, defaultRetentionDays, updatedAt, updatedBy);
    }
}
