package be.myrmacta.infrastructure.connector.builtin;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * TODO §150 — accès à l'eBox Enterprise (e-Box REST 3.8, Document Provider Registry 1.2), rôle
 * <em>Document Consumer</em> : lecture, et confirmation d'une action demandée par l'expéditeur. Les URL reçues ont déjà passé les contrôles du connecteur (https, suffixe d'hôte autorisé) ;
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

    /**
     * TODO §150 (D-EBOX-ACTION) — {@code GET /ebox/messages/{id}/messageActions} : l'état de chaque action du message ; vide si
     * le registre n'en déclare pas (404, opération facultative).
     */
    List<JsonNode> actionStatuses(String endpointUrl, Access access, String messageId);

    /** {@code PATCH /ebox/messages/{id}/messageActions/{actionId}} avec {@code isExecuted: true}. */
    void markActionExecuted(String endpointUrl, Access access, String messageId, String actionId);

    /**
     * TODO §151 (D-EBOX-PARTITION-CHECK) — {@code GET /ebox/{enterpriseNumber}/partitions} (API Partition 1.3), page par page
     * ({@code page}, {@code pageSize}, {@code totalItems}, comme les messages) : les identifiants. Une réponse sans tableau
     * {@code items}, ou une liste qui reste incomplète, lève {@link EboxTransportException} : la liste n'est pas vérifiable.
     */
    List<String> partitions(String partitionApiUrl, Access access, String enterpriseNumber);

    /** {@code GET /ebox/messages/{id}} : le message tel que l'eBox le voit maintenant ; vide s'il n'y est plus (404, 410). */
    Optional<JsonNode> message(String endpointUrl, Access access, String messageId);

    /**
     * TODO §151 (D-EBOX-MOVE) — {@code POST /ebox/messages/changePartition} : déplace un message vers une autre partition de l'eBox
     * destinataire ({@code targetRecipientPartitionId}). Succès : 200, 202 ou 204 ; un corps qui signale l'échec du message lève
     * {@link EboxResourceException} de statut 409, comme un refus.
     */
    void moveToPartition(String endpointUrl, Access access, String messageId, String targetPartition);

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
            this(status, "ebox answered HTTP " + status);
        }

        EboxResourceException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
