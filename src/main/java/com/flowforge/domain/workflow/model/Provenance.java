package com.flowforge.domain.workflow.model;

/**
 * TODO §82 (D6) — origine d'un brouillon ou d'une publication de configuration : l'éditeur ({@code UI}), un agent branché au
 * MCP ({@code MCP}) l'assistant de conception embarqué ({@code ASSISTANT}, LC3) ou un import de bundle ({@code IMPORT}). Un brouillon {@code UI} est un travail
 * humain en cours, jamais purgé ni écrasé par un agent sans demande explicite.
 */
public enum Provenance {
    UI,
    MCP,
    ASSISTANT,
    /** TODO §83 (D4) : brouillon créé par l'import d'un bundle de configuration (promotion entre environnements). */
    IMPORT,
    /** TODO §88 (LC5) : brouillon BPMN compilé depuis un parcours (concepteur de parcours) — relu puis publié comme les autres. */
    PARCOURS;

    /** {@code true} pour une origine automatisée (agent ou assistant). */
    public boolean automated() {
        return this != UI;
    }
}
