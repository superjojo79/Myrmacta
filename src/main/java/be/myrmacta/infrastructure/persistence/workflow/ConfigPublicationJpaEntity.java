package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.model.ConfigPublication;
import be.myrmacta.domain.workflow.model.DraftArtifactType;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.domain.workflow.model.PublicationMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "config_publication")
class ConfigPublicationJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "artifact_type", nullable = false, length = 16)
    private String artifactType;

    @Column(name = "artifact_key", nullable = false, length = 255)
    private String artifactKey;

    @Column(name = "version")
    private Integer version;

    @Column(name = "draft_provenance", nullable = false, length = 16)
    private String draftProvenance;

    @Column(name = "draft_author", nullable = false, length = 128)
    private String draftAuthor;

    @Column(name = "published_by", nullable = false, length = 128)
    private String publishedBy;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "mode", nullable = false, length = 16)
    private String mode;

    @Column(name = "config_provenance", length = 16)
    private String configProvenance;

    @Column(name = "config_author", length = 128)
    private String configAuthor;

    protected ConfigPublicationJpaEntity() {
        // JPA
    }

    static ConfigPublicationJpaEntity fromDomain(ConfigPublication p) {
        ConfigPublicationJpaEntity e = new ConfigPublicationJpaEntity();
        e.id = p.id();
        e.tenantId = p.tenantId();
        e.artifactType = p.artifactType().name();
        e.artifactKey = p.artifactKey();
        e.version = p.version();
        e.draftProvenance = p.draftProvenance().name();
        e.draftAuthor = p.draftAuthor();
        e.publishedBy = p.publishedBy();
        e.publishedAt = p.publishedAt();
        e.mode = p.mode().name();
        e.configProvenance = p.configProvenance() == null ? null : p.configProvenance().name();
        e.configAuthor = p.configAuthor();
        return e;
    }

    ConfigPublication toDomain() {
        return new ConfigPublication(id, tenantId, DraftArtifactType.valueOf(artifactType), artifactKey, version,
                Provenance.valueOf(draftProvenance), draftAuthor, publishedBy, publishedAt, PublicationMode.valueOf(mode),
                configProvenance == null ? null : Provenance.valueOf(configProvenance), configAuthor);
    }
}
