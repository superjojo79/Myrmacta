package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatus;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatusReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portal_link")
class PortalLinkJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    @Column(name = "schema_key", nullable = false, length = 128)
    private String schemaKey;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PortalLinkStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_reason", length = 32)
    private PortalLinkStatusReason statusReason;

    @Column(name = "ttl_days", nullable = false)
    private int ttlDays;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "send_attempts", nullable = false)
    private int sendAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** D5 : bail de prise pour l'envoi de l'invitation (non domaine) ; {@code null} = non pris. */
    @Column(name = "claimed_until")
    private Instant claimedUntil;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "revoked_by", length = 128)
    private String revokedBy;

    protected PortalLinkJpaEntity() {}

    static PortalLinkJpaEntity fromDomain(PortalLink link) {
        PortalLinkJpaEntity e = new PortalLinkJpaEntity();
        e.id = link.id();
        e.tenantId = link.tenantId();
        e.dossierId = link.dossierId();
        e.taskId = link.taskId();
        e.processInstanceId = link.processInstanceId();
        e.schemaKey = link.schemaKey();
        e.recipientEmail = link.recipientEmail();
        e.tokenHash = link.tokenHash();
        e.status = link.status();
        e.statusReason = link.statusReason();
        e.ttlDays = link.ttlDays();
        e.expiresAt = link.expiresAt();
        e.sendAttempts = link.sendAttempts();
        e.createdAt = link.createdAt();
        e.sentAt = link.sentAt();
        e.lastAccessedAt = link.lastAccessedAt();
        e.completedAt = link.completedAt();
        e.revokedAt = link.revokedAt();
        e.createdBy = link.createdBy();
        e.revokedBy = link.revokedBy();
        return e;
    }

    PortalLink toDomain() {
        return new PortalLink(id, tenantId, dossierId, taskId, processInstanceId, schemaKey, recipientEmail,
                tokenHash, status, statusReason, ttlDays, expiresAt, sendAttempts, createdAt, sentAt,
                lastAccessedAt, completedAt, revokedAt, createdBy, revokedBy);
    }
}
