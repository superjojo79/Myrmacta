package be.myrmacta.domain.dossier.model;

public class DuplicateDossierRelationException extends RuntimeException {
    public DuplicateDossierRelationException(String message) {
        super(message);
    }

    public DuplicateDossierRelationException(String message, Throwable cause) {
        super(message, cause);
    }
}
