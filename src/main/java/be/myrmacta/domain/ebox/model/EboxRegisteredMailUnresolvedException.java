package be.myrmacta.domain.ebox.model;

/**
 * TODO §155 (D-EBOX-SEND-REGISTERED) — une tentative de recommandé a déjà été faite avec cette clé (issue incertaine, ou
 * tentative concurrente en cours) et l'eBox ne connaît encore aucune preuve de dépôt pour elle. Rien n'est renvoyé : l'étape
 * échoue et se rejoue ; si la preuve n'apparaît jamais, un agent vérifie auprès de l'eBox avant d'envoyer avec une autre clé.
 */
public class EboxRegisteredMailUnresolvedException extends RuntimeException {

    public EboxRegisteredMailUnresolvedException(String idempotencyKey) {
        super("a registered mail attempt already used idempotency key " + idempotencyKey
                + " and no proof of dispatch is known yet; retry later, never resend with this key");
    }
}
