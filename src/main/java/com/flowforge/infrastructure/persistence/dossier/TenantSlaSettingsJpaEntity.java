package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.reporting.model.TenantSlaSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tenant_sla_settings")
class TenantSlaSettingsJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "compliance_target_percent", nullable = false)
    private int complianceTargetPercent;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    protected TenantSlaSettingsJpaEntity() {
        // JPA
    }

    private TenantSlaSettingsJpaEntity(
            String tenantId, int complianceTargetPercent, Instant updatedAt, String updatedBy) {
        this.tenantId = tenantId;
        this.complianceTargetPercent = complianceTargetPercent;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    static TenantSlaSettingsJpaEntity fromDomain(TenantSlaSettings settings) {
        return new TenantSlaSettingsJpaEntity(
                settings.tenantId(),
                settings.complianceTargetPercent(),
                settings.updatedAt(),
                settings.updatedBy());
    }

    TenantSlaSettings toDomain() {
        return new TenantSlaSettings(tenantId, complianceTargetPercent, updatedAt, updatedBy);
    }
}
