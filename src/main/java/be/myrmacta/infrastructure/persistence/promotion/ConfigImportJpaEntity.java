package be.myrmacta.infrastructure.persistence.promotion;

import be.myrmacta.domain.promotion.model.ConfigImportRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "config_import")
class ConfigImportJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "performed_by", nullable = false, length = 128)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "channel", nullable = false, length = 16)
    private String channel;

    @Column(name = "bundle_sha256", nullable = false, length = 64)
    private String bundleSha256;

    @Column(name = "format_version", nullable = false)
    private int formatVersion;

    @Column(name = "source_label", length = 200)
    private String sourceLabel;

    @Column(name = "applied", nullable = false)
    private int applied;

    @Column(name = "unchanged", nullable = false)
    private int unchanged;

    @Column(name = "skipped", nullable = false)
    private int skipped;

    @Column(name = "failed", nullable = false)
    private int failed;

    @Column(name = "report", nullable = false)
    private String report;

    protected ConfigImportJpaEntity() {
        // JPA
    }

    static ConfigImportJpaEntity fromDomain(ConfigImportRecord r) {
        ConfigImportJpaEntity e = new ConfigImportJpaEntity();
        e.id = r.id();
        e.tenantId = r.tenantId();
        e.performedBy = r.performedBy();
        e.performedAt = r.performedAt();
        e.channel = r.channel();
        e.bundleSha256 = r.bundleSha256();
        e.formatVersion = r.formatVersion();
        e.sourceLabel = r.sourceLabel();
        e.applied = r.applied();
        e.unchanged = r.unchanged();
        e.skipped = r.skipped();
        e.failed = r.failed();
        e.report = r.reportJson();
        return e;
    }

    ConfigImportRecord toDomain() {
        return new ConfigImportRecord(id, tenantId, performedBy, performedAt, channel, bundleSha256, formatVersion,
                sourceLabel, applied, unchanged, skipped, failed, report);
    }
}
