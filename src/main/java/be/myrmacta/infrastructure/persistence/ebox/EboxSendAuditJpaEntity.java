package be.myrmacta.infrastructure.persistence.ebox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * TODO §155 (D-EBOX-SEND-AUDIT) — ligne du journal d'audit des envois eBox (changeset 054). {@link Immutable} : Hibernate n'émet
 * jamais d'{@code update} ; la base refuse de toute façon modification, suppression et vidage.
 */
@Entity
@Immutable
@Table(name = "ebox_send_audit")
class EboxSendAuditJpaEntity implements Persistable<UUID> {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "sender_instance_id", nullable = false)
    private UUID senderInstanceId;

    @Column(nullable = false, length = 16)
    private String route;

    @Column(name = "recipient_ref", nullable = false, length = 64)
    private String recipientRef;

    @Column(name = "recipient_ref_kind", nullable = false, length = 16)
    private String recipientRefKind;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "dossier_id")
    private UUID dossierId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "sender_application_id", nullable = false, length = 100)
    private String senderApplicationId;

    @Column(name = "message_type_id", nullable = false, length = 100)
    private String messageTypeId;

    @Column(nullable = false, length = 2)
    private String language;

    @Column(nullable = false)
    private boolean registered;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> documents;

    @Column(name = "consent_level", nullable = false, length = 32)
    private String consentLevel;

    @Column(name = "consent_checked_at", nullable = false)
    private Instant consentCheckedAt;

    @Column(name = "consent_source", nullable = false, length = 50)
    private String consentSource;

    @Column(nullable = false, length = 32)
    private String delivery;

    @Column(name = "fallback_channel", nullable = false, length = 16)
    private String fallbackChannel;

    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_url", length = 2000)
    private String requestUrl;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "client_id", length = 200)
    private String clientId;

    @Column(name = "on_behalf_of", length = 10)
    private String onBehalfOf;

    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "proof_signature_sha256", length = 64)
    private String proofSignatureSha256;

    @Transient
    private boolean fresh;

    protected EboxSendAuditJpaEntity() {}

    EboxSendAuditJpaEntity(UUID id, String tenantId, Instant recordedAt, UUID senderInstanceId, String route, String recipientRef,
            String recipientRefKind, String idempotencyKey, UUID dossierId, String correlationId, String senderApplicationId,
            String messageTypeId, String language, boolean registered, List<Map<String, Object>> documents, String consentLevel,
            Instant consentCheckedAt, String consentSource, String delivery, String fallbackChannel, String outcome,
            String requestMethod, String requestUrl, Integer responseCode, String clientId, String onBehalfOf, String tokenHash,
            String providerMessageId, String proofSignatureSha256) {
        this.id = id;
        this.tenantId = tenantId;
        this.recordedAt = recordedAt;
        this.senderInstanceId = senderInstanceId;
        this.route = route;
        this.recipientRef = recipientRef;
        this.recipientRefKind = recipientRefKind;
        this.idempotencyKey = idempotencyKey;
        this.dossierId = dossierId;
        this.correlationId = correlationId;
        this.senderApplicationId = senderApplicationId;
        this.messageTypeId = messageTypeId;
        this.language = language;
        this.registered = registered;
        this.documents = documents;
        this.consentLevel = consentLevel;
        this.consentCheckedAt = consentCheckedAt;
        this.consentSource = consentSource;
        this.delivery = delivery;
        this.fallbackChannel = fallbackChannel;
        this.outcome = outcome;
        this.requestMethod = requestMethod;
        this.requestUrl = requestUrl;
        this.responseCode = responseCode;
        this.clientId = clientId;
        this.onBehalfOf = onBehalfOf;
        this.tokenHash = tokenHash;
        this.providerMessageId = providerMessageId;
        this.proofSignatureSha256 = proofSignatureSha256;
        this.fresh = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    /** Toujours une insertion : pas de {@code select} préalable de {@code merge}. */
    @Override
    public boolean isNew() {
        return fresh;
    }

    String recipientRef() {
        return recipientRef;
    }

    String outcome() {
        return outcome;
    }

    String delivery() {
        return delivery;
    }

    String fallbackChannel() {
        return fallbackChannel;
    }

    String providerMessageId() {
        return providerMessageId;
    }
}
