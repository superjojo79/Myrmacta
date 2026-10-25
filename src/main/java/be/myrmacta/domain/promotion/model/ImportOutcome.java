package be.myrmacta.domain.promotion.model;

/** TODO §83 (D4) — issue de l'application d'un artefact. */
public enum ImportOutcome {
    /** Créé ou mis à jour (BPMN, DMN : brouillon déposé). */
    APPLIED,
    UNCHANGED,
    /** Bloqué ou en conflit au plan : ignoré. */
    SKIPPED,
    /** Refusé à l'écriture : rapporté, les autres artefacts continuent. */
    FAILED
}
