package be.myrmacta.infrastructure.persistence.notification.template;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_template")
class NotificationTemplateJpaEntity {

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
    @Column(nullable = false, length = 16)
    private TemplateStatus status;

    @Column(nullable = false, columnDefinition = "text")
    private String subject;

    @Column(name = "text_body", nullable = false, columnDefinition = "text")
    private String textBody;

    @Column(name = "html_body", columnDefinition = "text")
    private String htmlBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    protected NotificationTemplateJpaEntity() {}

    static NotificationTemplateJpaEntity fromDomain(NotificationTemplate template) {
        NotificationTemplateJpaEntity entity = new NotificationTemplateJpaEntity();
        entity.id = template.id();
        entity.tenantId = template.tenantId();
        entity.templateKey = template.templateKey();
        entity.variantKey = template.variantKey();
        entity.locale = template.locale();
        entity.createdAt = template.createdAt();
        entity.applyDomain(template);
        return entity;
    }

    /** Champs modifiables d'un gabarit existant : la clé, la version et la date de création ne bougent jamais. */
    void applyDomain(NotificationTemplate template) {
        this.label = template.label();
        this.version = template.version();
        this.status = template.status();
        this.subject = template.subject();
        this.textBody = template.textBody();
        this.htmlBody = template.htmlBody();
        this.updatedAt = template.updatedAt();
        this.updatedBy = template.updatedBy();
    }

    NotificationTemplate toDomain() {
        return new NotificationTemplate(
                id,
                templateKey,
                variantKey,
                locale,
                label,
                version,
                status,
                subject,
                textBody,
                htmlBody,
                tenantId,
                createdAt,
                updatedAt,
                updatedBy);
    }

    TemplateStatus getStatus() {
        return status;
    }
}
