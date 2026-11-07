package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.portal.model.IdentityAssuranceLevel;
import be.myrmacta.domain.workflow.portal.model.IdentityMatch;
import be.myrmacta.domain.workflow.portal.model.PortalLinkIdentity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * TODO §104 (D-BEAUTH-STORE) — identité vérifiée d'un lien du portail (changeset 038) : preuve hachée et numéro national
 * chiffré, puis, après la soumission, la seule trace. Une identité par lien. Supprimée avec le lien (cascade de la base).
 */
@Entity
@Table(name = "portal_link_identity")
class PortalLinkIdentityJpaEntity {

    @Id
    @Column(name = "portal_link_id", nullable = false)
    private UUID linkId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "proof_hash", length = 64)
    private String proofHash;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "assurance_level", nullable = false, length = 16)
    private String assuranceLevel;

    @Column(name = "given_name")
    private String givenName;

    @Column(name = "family_name")
    private String familyName;

    @Column(name = "nrn_enc", length = 512)
    private String nrnEnc;

    @Column(name = "match_result", nullable = false, length = 32)
    private String matchResult;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected PortalLinkIdentityJpaEntity() {}

    static PortalLinkIdentityJpaEntity fromDomain(PortalLinkIdentity identity) {
        PortalLinkIdentityJpaEntity entity = new PortalLinkIdentityJpaEntity();
        entity.linkId = identity.linkId();
        entity.apply(identity);
        return entity;
    }

    /** Remplace l'identité entière : une nouvelle authentification efface la précédente, soumission comprise. */
    void apply(PortalLinkIdentity identity) {
        this.tenantId = identity.tenantId();
        this.proofHash = identity.proofHash();
        this.provider = identity.provider();
        this.assuranceLevel = identity.level().name();
        this.givenName = identity.givenName();
        this.familyName = identity.familyName();
        this.nrnEnc = identity.nrnEnc();
        this.matchResult = identity.match().name();
        this.verifiedAt = identity.verifiedAt();
        this.expiresAt = identity.expiresAt();
        this.submittedAt = identity.submittedAt();
    }

    PortalLinkIdentity toDomain() {
        return new PortalLinkIdentity(linkId, tenantId, proofHash, provider, IdentityAssuranceLevel.valueOf(assuranceLevel), givenName,
                familyName, nrnEnc, IdentityMatch.valueOf(matchResult), verifiedAt, expiresAt, submittedAt);
    }
}
