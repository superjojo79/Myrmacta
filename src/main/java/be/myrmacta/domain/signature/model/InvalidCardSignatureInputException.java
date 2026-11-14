package be.myrmacta.domain.signature.model;

/**
 * Entrée illisible d'une signature par carte : certificat, chaîne ou valeur de signature illisible, ou hors bornes
 * (TODO §138, D-EID-BRIDGE). Le message est constant : jamais le contenu reçu (400 sans détail).
 */
public final class InvalidCardSignatureInputException extends RuntimeException {

    public InvalidCardSignatureInputException() {
        super("unreadable card signature input");
    }
}
