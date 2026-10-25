package be.myrmacta.domain.precheck.model;

import java.util.Objects;

/**
 * TODO §106 (lot A, H1) — un appel unique au modèle, <b>sans outils</b> : consignes, contenu à examiner et schéma JSON
 * fermé de la réponse attendue. La réponse est un texte JSON non encore validé (le domaine le valide).
 */
public final class ModelExchange {

    private ModelExchange() {}

    /**
     * @param system consignes (versionnées par le prompt)
     * @param user contenu à examiner : champs et extraits de pièces, encadrés par des bornes
     * @param jsonSchema schéma JSON de la réponse ({@code additionalProperties: false})
     */
    public record Request(String system, String user, String jsonSchema, int maxOutputTokens) {
        public Request {
            Objects.requireNonNull(system, "system");
            Objects.requireNonNull(user, "user");
            Objects.requireNonNull(jsonSchema, "jsonSchema");
            if (maxOutputTokens < 1) {
                throw new IllegalArgumentException("maxOutputTokens must be positive");
            }
        }
    }

    /** @param json texte rendu par le modèle, à valider ; jetons consommés tels que déclarés par le fournisseur */
    public record Reply(String json, long tokensIn, long tokensOut, String provider, String model) {
        public Reply {
            json = json == null ? "" : json;
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(model, "model");
        }
    }
}
