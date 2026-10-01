package com.flowforge.domain.workflow.model;

/**
 * TODO §82 (D6) — origine d'un brouillon ou d'une publication de configuration : l'éditeur ({@code UI}), un agent branché au
 * MCP ({@code MCP}) ou l'assistant de conception embarqué ({@code ASSISTANT}, LC3). Un brouillon {@code UI} est un travail
 * humain en cours, jamais purgé ni écrasé par un agent sans demande explicite.
 */
public enum Provenance {
    UI,
    MCP,
    ASSISTANT;

    /** {@code true} pour une origine automatisée (agent ou assistant). */
    public boolean automated() {
        return this != UI;
    }
}
