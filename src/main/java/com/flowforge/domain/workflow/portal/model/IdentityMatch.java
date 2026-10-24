package com.flowforge.domain.workflow.portal.model;

/**
 * Correspondance entre l'identité vérifiée et le champ d'identité du dossier (TODO §103, T5-T7, D-BEAUTH-NRN-FIELD) :
 * champ rempli, il est attendu ; champ vide, le NRN vérifié y sera écrit à la soumission.
 */
public enum IdentityMatch {
    /** Champ rempli, même NRN. */
    CONFORME,
    /** Champ vide, NRN vérifié : écrit à la soumission (T6). */
    PREMIER_ENREGISTREMENT,
    /** Champ vide, identité sans NRN (eIDAS étranger) : rien n'est écrit (T7). */
    SANS_NRN,
    /** Champ rempli et NRN différent, absent ou illisible : refus neutre (T5, T7). */
    NON_CONFORME;

    public static IdentityMatch of(Object identityFieldValue, NationalRegisterNumber verified) {
        boolean empty = identityFieldValue == null
                || (identityFieldValue instanceof String text && text.isBlank());
        if (empty) {
            return verified == null ? SANS_NRN : PREMIER_ENREGISTREMENT;
        }
        if (verified == null) {
            return NON_CONFORME;
        }
        return NationalRegisterNumber.parse(identityFieldValue).filter(verified::equals).isPresent()
                ? CONFORME
                : NON_CONFORME;
    }

    public boolean allowsSubmission() {
        return this != NON_CONFORME;
    }

    public boolean writesTheField() {
        return this == PREMIER_ENREGISTREMENT;
    }
}
