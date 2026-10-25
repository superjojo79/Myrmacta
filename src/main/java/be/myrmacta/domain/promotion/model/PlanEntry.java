package be.myrmacta.domain.promotion.model;

import java.util.List;
import java.util.Objects;

/**
 * TODO §83 (D4) — une ligne du plan à blanc.
 *
 * @param secretsRequired pour un connecteur : noms des champs secrets à fournir à l'import (jamais leurs valeurs)
 * @param connectorName pour un connecteur : son nom exact (la clé du fichier n'en est qu'un slug) — c'est lui qui indexe les secrets
 */
public record PlanEntry(
        ArtifactType type, String key, PlanAction action, String reason, List<String> secretsRequired, String connectorName) {

    public PlanEntry {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(action, "action");
        reason = reason == null ? "" : reason;
        secretsRequired = secretsRequired == null ? List.of() : List.copyOf(secretsRequired);
    }

    public PlanEntry(ArtifactType type, String key, PlanAction action, String reason) {
        this(type, key, action, reason, List.of(), null);
    }

    public PlanEntry(ArtifactType type, String key, PlanAction action, String reason, List<String> secretsRequired) {
        this(type, key, action, reason, secretsRequired, null);
    }

    public boolean applicable() {
        return action == PlanAction.CREATE || action == PlanAction.UPDATE;
    }
}
