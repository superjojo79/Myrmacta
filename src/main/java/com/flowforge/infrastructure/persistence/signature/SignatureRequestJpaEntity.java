package com.flowforge.infrastructure.persistence.signature;

import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.SignatureStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signature_request")
class SignatureRequestJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    @Column(name = "activity_id", nullable = false, length = 128)
    private String activityId;

    @Column(name = "source_element_id")
    private UUID sourceElementId;

    @Column(name = "source_storage_ref", length = 512)
    private String sourceStorageRef;

    @Column(name = "source_digest", length = 64)
    private String sourceDigest;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "provider_reference", length = 256)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 12)
    private SignatureMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SignatureStatus status;

    @Column(name = "status_reason", length = 48)
    private String statusReason;

    @Column(name = "simulated", nullable = false)
    private boolean simulated;

    @Column(name = "assurance_level", length = 16)
    private String assuranceLevel;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "outcome_dispatched_at")
    private Instant outcomeDispatchedAt;

    @Column(name = "dispatch_attempts", nullable = false)
    private int dispatchAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "version", nullable = false)
    private long version;

    protected SignatureRequestJpaEntity() {}

    static SignatureRequestJpaEntity fromDomain(SignatureRequest r) {
        SignatureRequestJpaEntity e = new SignatureRequestJpaEntity();
        e.id = r.id();
        e.tenantId = r.tenantId();
        e.dossierId = r.dossierId();
        e.processInstanceId = r.processInstanceId();
        e.activityId = r.activityId();
        e.sourceElementId = r.sourceElementId();
        e.sourceStorageRef = r.sourceStorageRef();
        e.sourceDigest = r.sourceDigest();
        e.providerCode = r.providerCode();
        e.providerReference = r.providerReference();
        e.mode = r.mode();
        e.status = r.status();
        e.statusReason = r.statusReason();
        e.simulated = r.simulated();
        e.assuranceLevel = r.assuranceLevel();
        e.deadlineAt = r.deadlineAt();
        e.outcomeDispatchedAt = r.outcomeDispatchedAt();
        e.dispatchAttempts = r.dispatchAttempts();
        e.createdAt = r.createdAt();
        e.completedAt = r.completedAt();
        e.createdBy = r.createdBy();
        e.version = r.version();
        return e;
    }

    SignatureRequest toDomain() {
        return new SignatureRequest(id, tenantId, dossierId, processInstanceId, activityId, sourceElementId,
                sourceStorageRef, sourceDigest, providerCode, providerReference, mode, status, statusReason,
                simulated, assuranceLevel, deadlineAt, outcomeDispatchedAt, dispatchAttempts, createdAt,
                completedAt, createdBy, version);
    }
}
