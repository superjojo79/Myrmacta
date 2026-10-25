package be.myrmacta.infrastructure.persistence.document;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_template")
class DocumentTemplateJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "template_key", nullable = false, length = 128)
    private String templateKey;

    @Column(name = "variant_key", length = 128)
    private String variantKey;

    @Column(length = 32)
    private String locale;

    @Column(length = 255)
    private String label;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TemplateStatus status;

    @Lob
    @Column(name = "html_content", nullable = false)
    private String htmlContent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    protected DocumentTemplateJpaEntity() {}

    static DocumentTemplateJpaEntity fromDomain(DocumentTemplate template) {
        DocumentTemplateJpaEntity entity = new DocumentTemplateJpaEntity();
        entity.id = template.id();
        entity.tenantId = template.tenantId();
        entity.templateKey = template.templateKey();
        entity.variantKey = template.variantKey();
        entity.locale = template.locale();
        entity.label = template.label();
        entity.version = template.version();
        entity.status = template.status();
        entity.htmlContent = template.htmlContent();
        entity.createdAt = template.createdAt();
        entity.updatedAt = template.updatedAt();
        entity.updatedBy = template.updatedBy();
        return entity;
    }

    void applyDomain(DocumentTemplate template) {
        this.label = template.label();
        this.htmlContent = template.htmlContent();
        this.status = template.status();
        this.version = template.version();
        this.updatedAt = template.updatedAt();
        this.updatedBy = template.updatedBy();
    }

    DocumentTemplate toDomain() {
        return new DocumentTemplate(
                id,
                templateKey,
                variantKey,
                locale,
                label,
                version,
                status,
                htmlContent,
                tenantId,
                createdAt,
                updatedAt,
                updatedBy);
    }

    TemplateStatus getStatus() {
        return status;
    }
}
