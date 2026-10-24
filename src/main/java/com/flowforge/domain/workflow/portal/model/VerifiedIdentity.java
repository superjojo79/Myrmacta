package com.flowforge.domain.workflow.portal.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Identité qu'un fournisseur (FAS, itsme) vient de vérifier pour un tiers du portail (TODO §103). Le NRN est absent
 * pour une identité eIDAS étrangère (T7). {@link #toString()} ne montre jamais le NRN.
 */
public record VerifiedIdentity(
        String provider, IdentityAssuranceLevel level, String givenName, String familyName, NationalRegisterNumber nrnOrNull) {

    public VerifiedIdentity {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(level, "level");
    }

    public Optional<NationalRegisterNumber> nrn() {
        return Optional.ofNullable(nrnOrNull);
    }
}
