package com.flowforge.domain.workflow.portal.model;

/**
 * Justificatif présenté au portail (TODO §103, D-BEAUTH-PROOF) : le jeton du lien ({@code X-Portal-Token}), ou la preuve
 * d'identité remise après une authentification forte ({@code X-Portal-Identity}), qui le remplace ensuite. Jamais
 * affiché ({@link #toString()}).
 */
public sealed interface PortalCredential permits PortalCredential.LinkToken, PortalCredential.IdentityProof {

    String value();

    /** La preuve, si elle est présente, l'emporte sur le jeton. */
    static PortalCredential of(String token, String proof) {
        if (proof != null && !proof.isBlank()) {
            return new IdentityProof(proof);
        }
        return new LinkToken(token);
    }

    record LinkToken(String value) implements PortalCredential {
        @Override
        public String toString() {
            return "LinkToken[***]";
        }
    }

    record IdentityProof(String value) implements PortalCredential {
        @Override
        public String toString() {
            return "IdentityProof[***]";
        }
    }
}
