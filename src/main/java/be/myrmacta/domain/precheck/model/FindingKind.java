package be.myrmacta.domain.precheck.model;

/** TODO §106 — type d'un constat (fiche : MANQUANT, INCOHÉRENCE, À VÉRIFIER, OK). */
public enum FindingKind {
    MISSING,
    INCONSISTENT,
    TO_CHECK,
    OK
}
