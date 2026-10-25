package be.myrmacta.domain.workflow.portal.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Identité vérifiée rattachée à un lien portail (TODO §104, T9, D-BEAUTH-PROOF), une au plus par lien. Tant qu'elle
 * n'est pas soumise, {@code proofHash} (SHA-256 de la preuve remise au navigateur) en fait un justificatif valable
 * jusqu'à {@code expiresAt}, et {@code nrnEnc} garde le NRN chiffré pour la soumission. À la soumission, preuve et NRN
 * sont effacés : il ne reste que la trace ({@link #trace()}).
 */
public record PortalLinkIdentity(
        UUID linkId, String tenantId, String proofHash, String provider, IdentityAssuranceLevel level, String givenName,
        String familyName, String nrnEnc, IdentityMatch match, Instant verifiedAt, Instant expiresAt,
        Instant submittedAt) {

    public PortalLinkIdentity {
        Objects.requireNonNull(linkId, "linkId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
    }

    /** Justificatif encore valable : non soumis, preuve présente, non expiré. */
    public boolean isUsable(Instant now) {
        return submittedAt == null && proofHash != null && expiresAt != null && now.isBefore(expiresAt);
    }

    public IdentityVerification trace() {
        return new IdentityVerification(provider, level, givenName, familyName, match, verifiedAt);
    }

    @Override
    public String toString() {
        return "PortalLinkIdentity[linkId=" + linkId + ", provider=" + provider + ", level=" + level + ", match="
                + match + "]";
    }
}
