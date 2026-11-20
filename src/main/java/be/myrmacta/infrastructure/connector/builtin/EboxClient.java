package be.myrmacta.infrastructure.connector.builtin;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * TODO §150 — accès en lecture à l'eBox Enterprise (e-Box REST 3.8, Document Provider Registry 1.2), rôle
 * <em>Document Consumer</em>. Les URL reçues ont déjà passé les contrôles du connecteur (https, suffixe d'hôte autorisé) ;
 * l'implémentation les repasse par {@code OutboundUrlGuard} à chaque appel.
 */
interface EboxClient {

    /** Jeton d'accès et, pour un prestataire technique, le numéro BCE de l'organisation représentée (mandat). */
    record Access(String token, String onBehalfOf) {}

    /** Une page de {@code GET /ebox/messages}. */
    record MessagePage(List<JsonNode> items, int totalItems) {}

    /** {@code GET /messageRegistries} : les registres annoncés, tels quels. */
    List<JsonNode> registries(String registryUrl, Access access);

    /** {@code GET /ebox/messages}, filtré sur une partition et une date de réception. */
    MessagePage messages(String endpointUrl, Access access, String partition, LocalDate receivedAfter, int page, int pageSize);

    /** {@code GET /ebox/messages/{id}/attachments/{attachmentId}/content}, borné à {@code maxBytes}. */
    byte[] attachment(String endpointUrl, Access access, String messageId, String attachmentId, long maxBytes);

    /** {@code GET /ebox/messages/{id}/body} : une chaîne par langue ; vide si le registre n'en sert pas (404). */
    Optional<JsonNode> body(String endpointUrl, Access access, String messageId);

    /** Jeton refusé (401) : le connecteur le renouvelle une fois. */
    final class EboxUnauthorizedException extends IllegalStateException {
        EboxUnauthorizedException() {
            super("ebox answered HTTP 401");
        }
    }

    /** Panne du service ou réponse inexploitable : la lecture de ce registre s'arrête pour ce passage. */
    final class EboxTransportException extends IllegalStateException {
        EboxTransportException(String message) {
            super(message);
        }
    }

    /** Refus propre à une ressource (message ou pièce introuvable, interdit) : le message est laissé pour le passage suivant. */
    final class EboxResourceException extends RuntimeException {
        final int status;

        EboxResourceException(int status) {
            super("ebox answered HTTP " + status);
            this.status = status;
        }
    }
}
