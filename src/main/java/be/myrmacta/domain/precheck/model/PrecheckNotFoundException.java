package be.myrmacta.domain.precheck.model;

/** TODO §106 — exécution ou constat introuvable pour ce dossier (404). */
public class PrecheckNotFoundException extends RuntimeException {
    public PrecheckNotFoundException(String message) {
        super(message);
    }
}
