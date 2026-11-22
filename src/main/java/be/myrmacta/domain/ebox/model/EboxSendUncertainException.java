package be.myrmacta.domain.ebox.model;

/**
 * TODO §155 (D-EBOX-SEND-IDEMPOTENCE) — la publication a peut-être abouti (délai dépassé, erreur 5xx). L'appel est journalisé ;
 * l'étape échoue et se rejoue avec la même clé d'idempotence, que l'eBox reconnaît (409) si le premier appel avait abouti.
 */
public class EboxSendUncertainException extends RuntimeException {

    public EboxSendUncertainException(String idempotencyKey) {
        super("ebox publication outcome is unknown for idempotency key " + idempotencyKey + "; retry with the same key");
    }
}
