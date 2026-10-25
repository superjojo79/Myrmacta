package be.myrmacta.domain.dossier.model;

public class InvalidDossierElementException extends RuntimeException {

    public InvalidDossierElementException(String message) {
        super(message);
    }
}
