package be.myrmacta.infrastructure.signature;

/** Échec d'un appel DocuSign. Le message ne contient ni secret, ni assertion JWT, ni URL de signature. */
public class DocusignCallException extends RuntimeException {

    DocusignCallException(String message) {
        super(message);
    }
}
