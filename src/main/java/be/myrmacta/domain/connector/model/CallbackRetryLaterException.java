package be.myrmacta.domain.connector.model;

/**
 * Le callback est valide mais ne peut pas encore être appliqué (ex. le fournisseur rappelle avant que la référence
 * qu'il vient de renvoyer soit enregistrée). Contrairement aux autres échecs du callback, l'échec n'est <b>pas</b>
 * mémorisé sous la clé d'idempotence : le fournisseur rejoue avec la même clé et sera traité (HTTP 503).
 */
public class CallbackRetryLaterException extends RuntimeException {

    public CallbackRetryLaterException(String message) {
        super(message);
    }
}
