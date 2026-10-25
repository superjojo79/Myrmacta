package be.myrmacta.domain.dossier.model;

public class DossierWorkflowLinkNotFoundException extends RuntimeException {
    public DossierWorkflowLinkNotFoundException(String message) {
        super(message);
    }
}
