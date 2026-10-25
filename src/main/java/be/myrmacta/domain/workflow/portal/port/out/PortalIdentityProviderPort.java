package be.myrmacta.domain.workflow.portal.port.out;

import be.myrmacta.domain.workflow.portal.model.VerifiedIdentity;
import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * TODO §104 (D-BEAUTH-PORTAL-CLIENT) — fournisseurs d'identité du portail (FAS, itsme), dont l'instance est elle-même
 * cliente OIDC : aucun compte n'est créé pour un tiers. Le protocole (PKCE, {@code private_key_jwt}, validation de
 * l'ID token) est un détail d'infrastructure.
 */
public interface PortalIdentityProviderPort {

    /** Authentification forte désactivée : aucun fournisseur ; toute tentative échoue. */
    PortalIdentityProviderPort NONE = new PortalIdentityProviderPort() {
        @Override
        public List<ProviderOption> providers() {
            return List.of();
        }

        @Override
        public AuthorizationRequest begin(String provider, String state, String nonce) {
            throw new IdentityProviderException("strong authentication is disabled");
        }

        @Override
        public VerifiedIdentity complete(String provider, String code, String pkceVerifier, String nonce) {
            throw new IdentityProviderException("strong authentication is disabled");
        }
    };

    /** Fournisseurs proposés au tiers, dans l'ordre de la configuration (D-BEAUTH-PROVIDER-CHOICE). */
    List<ProviderOption> providers();

    /** URL d'autorisation du fournisseur, et vérificateur PKCE à garder (chiffré) jusqu'au retour. */
    AuthorizationRequest begin(String provider, String state, String nonce);

    /**
     * Échange le code et valide l'ID token (signature, émetteur, audience, expiration, {@code nonce}). Lève
     * {@link IdentityProviderException} sur tout échec : le tiers reçoit le refus neutre.
     */
    VerifiedIdentity complete(String provider, String code, String pkceVerifier, String nonce);

    record ProviderOption(String id, String label) {
        public ProviderOption {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
        }
    }

    record AuthorizationRequest(URI authorizationUri, String pkceVerifier) {
        public AuthorizationRequest {
            Objects.requireNonNull(authorizationUri, "authorizationUri");
            Objects.requireNonNull(pkceVerifier, "pkceVerifier");
        }

        @Override
        public String toString() {
            return "AuthorizationRequest[***]";
        }
    }

    /** Échec du fournisseur ou d'une validation ; le message est un motif interne, jamais montré au tiers. */
    final class IdentityProviderException extends RuntimeException {
        public IdentityProviderException(String reason) {
            super(reason);
        }

        public IdentityProviderException(String reason, Throwable cause) {
            super(reason, cause);
        }
    }
}
