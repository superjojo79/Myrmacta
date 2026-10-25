package be.myrmacta.domain.connector.model;

/**
 * Second contrat de connecteur {@code CALLBACK} (TODO §72) : au lieu de décrire une corrélation de
 * message à effectuer ({@link CallbackConnector}), le connecteur <strong>traite lui-même</strong> le
 * retour authentifié (HMAC, idempotence, plafonds déjà appliqués par le contrôleur générique). Utilisé
 * par la signature, dont le retour ferme une demande avant de reprendre le process par son propre
 * répartiteur (D-SIGNATURE-DISPATCH).
 */
public interface CallbackHandlingConnector {

    void handleCallback(ConnectorInstanceData instance, Object rawPayload);
}
