package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.portal.model.PortalIdentityAttempt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * TODO §104 (D-BEAUTH-STORE) — tentative d'authentification forte d'un tiers du portail (changeset 038) : state haché, nonce,
 * vérificateur PKCE chiffré. À usage unique ({@code consumedAt}) et de courte durée. Supprimée avec le lien (cascade de la base).
 */
@Entity
@Table(name = "portal_identity_attempt")
class PortalIdentityAttemptJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    @Column(name = "portal_link_id", nullable = false)
    private UUID linkId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 128)
    private String nonce;

    @Column(name = "pkce_verifier_enc", nullable = false, length = 512)
    private String pkceVerifierEnc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected PortalIdentityAttemptJpaEntity() {}

    static PortalIdentityAttemptJpaEntity fromDomain(PortalIdentityAttempt attempt) {
        PortalIdentityAttemptJpaEntity entity = new PortalIdentityAttemptJpaEntity();
        entity.id = attempt.id();
        entity.tenantId = attempt.tenantId();
        entity.stateHash = attempt.stateHash();
        entity.linkId = attempt.linkId();
        entity.provider = attempt.provider();
        entity.nonce = attempt.nonce();
        entity.pkceVerifierEnc = attempt.pkceVerifierEnc();
        entity.createdAt = attempt.createdAt();
        entity.expiresAt = attempt.expiresAt();
        return entity;
    }

    PortalIdentityAttempt toDomain() {
        return new PortalIdentityAttempt(id, tenantId, linkId, stateHash, provider, nonce, pkceVerifierEnc, createdAt, expiresAt);
    }
}
