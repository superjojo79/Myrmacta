package be.myrmacta.domain.precheck.model;

/**
 * TODO §106 (revue adversariale de #211, F3) — relance refusée pour la protéger du plafond de l'instance : trop de relances
 * pour ce dossier sur 24 heures, ou réserve des workflows atteinte (429).
 */
public class PrecheckRateLimitedException extends RuntimeException {
    public PrecheckRateLimitedException(String message) {
        super(message);
    }
}
