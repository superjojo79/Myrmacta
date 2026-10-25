package be.myrmacta.domain.dossier.model;

public class DossierNotFoundException extends RuntimeException {
    public DossierNotFoundException(String message) {
        super(message);
    }
}
