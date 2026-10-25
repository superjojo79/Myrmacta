package be.myrmacta.domain.workflow.model;

/** Source de références de la palette inconnue ou non servie par l'endpoint de références (TODO §73). */
public class UnknownBlockSourceException extends RuntimeException {

    public UnknownBlockSourceException(String source) {
        super("Unknown block reference source: " + source);
    }
}
