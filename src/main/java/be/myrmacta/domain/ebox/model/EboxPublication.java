package be.myrmacta.domain.ebox.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * TODO §155 (D-EBOX-SEND-IDEMPOTENCE, D-EBOX-SEND-AUDIT) — résultat d'un appel de publication, avec la trace que le journal
 * d'audit garde (champs ATREQ de l'appel : méthode, URL, code de réponse, client OAuth, mandat, empreinte du jeton).
 *
 * @param providerMessageId identifiant rendu par l'eBox ; nul pour un doublon (409) ou un appel incertain
 * @param responseCode code HTTP, ou {@link #TIMEOUT} quand la réponse n'est jamais arrivée
 * @param proof preuve de dépôt d'un recommandé, sinon nulle
 */
public record EboxPublication(Status status, String providerMessageId, int responseCode, Trace trace, EboxProofOfDispatch proof) {

    /** Code générique d'un appel sans réponse (délai dépassé, connexion coupée après l'envoi). */
    public static final int TIMEOUT = 599;

    public enum Status {
        /** L'eBox a accepté le message. */
        PUBLISHED,
        /** L'eBox a déjà reçu cette clé d'idempotence (409) ou cette référence de recommandé : le premier envoi a abouti. */
        DUPLICATE,
        /** Issue inconnue : l'appel est journalisé (ATREQ04) et sera rejoué avec la même clé. */
        UNCERTAIN
    }

    /**
     * @param tokenHash SHA-256 hexadécimal du jeton d'accès (ATREQ : jamais le jeton lui-même), nul sans jeton
     */
    public record Trace(String method, String url, String clientId, String onBehalfOf, String tokenHash) {

        private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");

        public Trace {
            method = EboxTexts.line(method, "method", 10);
            url = EboxTexts.line(url, "url", 2000);
            clientId = EboxTexts.optionalLine(clientId, "clientId", 200);
            onBehalfOf = EboxTexts.optionalLine(onBehalfOf, "onBehalfOf", 10);
            if (tokenHash != null && !HEX64.matcher(tokenHash).matches()) {
                throw new IllegalArgumentException("tokenHash must be a lowercase SHA-256 hex digest");
            }
        }
    }

    public EboxPublication {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(trace, "trace");
        providerMessageId = EboxTexts.optionalLine(providerMessageId, "providerMessageId", 200);
        if (status == Status.PUBLISHED && providerMessageId == null) {
            throw new IllegalArgumentException("a published message has a providerMessageId");
        }
        if (responseCode < 100 || responseCode > TIMEOUT) {
            throw new IllegalArgumentException("responseCode must be an HTTP status");
        }
    }
}
