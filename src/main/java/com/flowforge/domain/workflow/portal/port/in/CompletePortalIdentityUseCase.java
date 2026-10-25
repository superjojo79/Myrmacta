package com.flowforge.domain.workflow.portal.port.in;

/**
 * TODO §104 (T4-T7, D-BEAUTH-PROOF) — retour du fournisseur : consomme la tentative, valide l'identité, la compare au
 * champ d'identité et, si elle convient, remet une preuve d'identité liée au lien.
 */
public interface CompletePortalIdentityUseCase {

    Outcome complete(String state, String code, String error);

    /** {@code proof} : à remettre au navigateur, jamais journalisé. {@code refusal} : motif interne, jamais montré. */
    record Outcome(String proof, String refusal) {

        public static Outcome verified(String proof) {
            return new Outcome(proof, null);
        }

        public static Outcome refused(String refusal) {
            return new Outcome(null, refusal);
        }

        public boolean isVerified() {
            return proof != null;
        }

        @Override
        public String toString() {
            return isVerified() ? "Outcome[verified]" : "Outcome[refused: " + refusal + "]";
        }
    }
}
