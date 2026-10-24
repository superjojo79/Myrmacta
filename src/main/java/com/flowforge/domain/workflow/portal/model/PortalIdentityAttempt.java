package com.flowforge.domain.workflow.portal.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tentative d'authentification forte d'un tiers (TODO §103, D-BEAUTH-STORE) : ouverte par « S'identifier », close par
 * le retour du fournisseur. {@code stateHash} : SHA-256 du {@code state} envoyé au fournisseur (jamais le state en
 * clair) ; {@code pkceVerifierEnc} : vérificateur PKCE chiffré. À usage unique, quelques minutes.
 */
public record PortalIdentityAttempt(
        UUID id, String tenantId, UUID linkId, String stateHash, String provider, String nonce, String pkceVerifierEnc,
        Instant createdAt, Instant expiresAt) {

    public PortalIdentityAttempt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(linkId, "linkId");
        Objects.requireNonNull(stateHash, "stateHash");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(pkceVerifierEnc, "pkceVerifierEnc");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public String toString() {
        return "PortalIdentityAttempt[id=" + id + ", linkId=" + linkId + ", provider=" + provider + "]";
    }
}
