package be.myrmacta.domain.promotion.model;

/** TODO §83 (D4) — ce que l'application du bundle ferait d'un artefact. */
public enum PlanAction {
    CREATE,
    UPDATE,
    UNCHANGED,
    /** Refus métier (ex. type incompatible avec des dossiers existants) : jamais forcé. */
    CONFLICT,
    /** Ne peut pas être appliqué maintenant (secret requis, dépendance non publiée, validation refusée) : ignoré, rapporté. */
    BLOCKED
}
