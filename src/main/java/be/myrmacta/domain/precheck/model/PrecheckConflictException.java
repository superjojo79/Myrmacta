package be.myrmacta.domain.precheck.model;

/** TODO §106 (R7) — relance impossible : aucune exécution antérieure, une déjà en cours ou dossier clos (409). */
public class PrecheckConflictException extends RuntimeException {
    public PrecheckConflictException(String message) {
        super(message);
    }
}
