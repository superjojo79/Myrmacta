package com.flowforge.domain.workflow.portal.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Trace d'une authentification forte (TODO §104, T8) : fournisseur, niveau, nom vérifié et résultat, jamais le NRN.
 * Montrée à l'instructeur avec le lien portail ; conservée après la soumission, supprimée avec le dossier.
 */
public record IdentityVerification(
        String provider, IdentityAssuranceLevel level, String givenName, String familyName, IdentityMatch result,
        Instant verifiedAt) {

    public IdentityVerification {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
    }

    public static IdentityVerification of(VerifiedIdentity identity, IdentityMatch result, Instant verifiedAt) {
        return new IdentityVerification(identity.provider(), identity.level(), identity.givenName(),
                identity.familyName(), result, verifiedAt);
    }
}
