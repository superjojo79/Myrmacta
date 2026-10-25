package be.myrmacta.domain.precheck.model;

/** TODO §106 — erreurs de la pré-vérification ; aucun message ne reprend une valeur de dossier ni un secret. */
public final class PrecheckExceptions {

    private PrecheckExceptions() {}

    /** La pré-vérification n'est pas activée sur cette instance ({@code app.precheck.enabled=false}). */
    public static class PrecheckDisabledException extends RuntimeException {
        public PrecheckDisabledException() {
            super("AI pre-check is not enabled on this instance (APP_PRECHECK_ENABLED=true)");
        }
    }

    /** Le plafond de jetons du jour est atteint (H2). */
    public static class PrecheckBudgetExceededException extends RuntimeException {
        public PrecheckBudgetExceededException(long used, long max) {
            super("AI pre-check daily token budget reached (" + used + " / " + max + ")");
        }
    }

    /** Le fournisseur est injoignable ou a refusé la requête ; sa réponse n'est jamais relayée. */
    public static class PrecheckModelUnavailableException extends RuntimeException {
        public PrecheckModelUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        public PrecheckModelUnavailableException(String message) {
            super(message);
        }
    }
}
