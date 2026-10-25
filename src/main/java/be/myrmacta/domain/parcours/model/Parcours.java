package be.myrmacta.domain.parcours.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** TODO §88 (LC5) — parcours enregistré ; sa clé est celle du processus BPMN qu'il génère. */
public record Parcours(
        UUID id,
        String tenantId,
        String key,
        String label,
        ParcoursDefinition definition,
        int version,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant lastCompiledAt,
        String lastCompiledSha256) {

    /** Identique à la clé d'un processus : camelCase. */
    public static final Pattern KEY = Pattern.compile("[a-z][A-Za-z0-9]{0,59}");

    public Parcours {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        if (key == null || !KEY.matcher(key).matches()) {
            throw new InvalidParcoursException("a parcours key is camelCase (letters and digits, max 60, starting with a lowercase letter)");
        }
        if (label == null || label.isBlank() || label.length() > 200) {
            throw new InvalidParcoursException("a parcours needs a label of at most 200 characters");
        }
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
