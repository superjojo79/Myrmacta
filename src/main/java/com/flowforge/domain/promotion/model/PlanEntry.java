package com.flowforge.domain.promotion.model;

import java.util.List;
import java.util.Objects;

/**
 * TODO §83 (D4) — une ligne du plan à blanc.
 *
 * @param secretsRequired pour un connecteur : noms des champs secrets à fournir à l'import (jamais leurs valeurs)
 */
public record PlanEntry(ArtifactType type, String key, PlanAction action, String reason, List<String> secretsRequired) {

    public PlanEntry {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(action, "action");
        reason = reason == null ? "" : reason;
        secretsRequired = secretsRequired == null ? List.of() : List.copyOf(secretsRequired);
    }

    public PlanEntry(ArtifactType type, String key, PlanAction action, String reason) {
        this(type, key, action, reason, List.of());
    }

    public boolean applicable() {
        return action == PlanAction.CREATE || action == PlanAction.UPDATE;
    }
}
