package be.myrmacta.infrastructure.persistence.signature;

import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerKind;
import be.myrmacta.domain.signature.model.SignerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signature_signer")
class SignerJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "signer_rank", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private SignerKind kind;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SignerStatus status;

    @Column(name = "invite_sent_at")
    private Instant inviteSentAt;

    @Column(name = "invite_attempts", nullable = false)
    private int inviteAttempts;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    @Column(name = "version", nullable = false)
    private long version;

    protected SignerJpaEntity() {}

    static SignerJpaEntity fromDomain(Signer s) {
        SignerJpaEntity e = new SignerJpaEntity();
        e.id = s.id();
        e.tenantId = s.tenantId();
        e.requestId = s.requestId();
        e.position = s.position();
        e.kind = s.kind();
        e.required = s.required();
        e.userId = s.userId();
        e.displayName = s.displayName();
        e.email = s.email();
        e.tokenHash = s.tokenHash();
        e.status = s.status();
        e.inviteSentAt = s.inviteSentAt();
        e.inviteAttempts = s.inviteAttempts();
        e.decidedAt = s.decidedAt();
        e.declineReason = s.declineReason();
        e.version = s.version();
        return e;
    }

    Signer toDomain() {
        return new Signer(id, tenantId, requestId, position, kind, required, userId, displayName, email, tokenHash,
                status, inviteSentAt, inviteAttempts, decidedAt, declineReason, version);
    }
}
