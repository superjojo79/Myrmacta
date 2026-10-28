package be.myrmacta.infrastructure.signature;

/** Échec d'un appel Yousign. Le message ne contient ni secret ni URL de signature. */
public final class YousignCallException extends RuntimeException {

    YousignCallException(String message) {
        super(message);
    }
}
