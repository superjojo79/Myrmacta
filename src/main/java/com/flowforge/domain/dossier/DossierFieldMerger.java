package com.flowforge.domain.dossier;

import com.flowforge.domain.dossier.model.FieldDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure field-patch logic for {@code PATCH /api/dossiers/{id}/fields} — merges a partial patch
 * into a dossier's existing fields, and reports which {@link FieldDefinition#isSensitive()}
 * fields actually changed value (for audit logging). Mirrors {@link DossierFieldRedactor}: same
 * package, same static-utility shape, same "iterate {@code FieldDefinition}s" pattern.
 */
public final class DossierFieldMerger {

    private DossierFieldMerger() {
    }

    /**
     * A {@code null} value in {@code patch} removes the key from the result rather than storing
     * a null-valued entry — {@link com.flowforge.domain.dossier.model.Dossier}'s compact
     * constructor calls {@code Map.copyOf(fields)}, which throws on a null value, so this is the
     * only implementation that doesn't break that invariant (same reasoning as
     * {@link DossierFieldRedactor}, which removes rather than nulls for the read side).
     */
    public static Map<String, Object> merge(Map<String, Object> existing, Map<String, Object> patch) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(patch, "patch");
        Map<String, Object> merged = new HashMap<>(existing);
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            if (entry.getValue() == null) {
                merged.remove(entry.getKey());
            } else {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(merged);
    }

    /** Names of {@code sensitive} fields whose value differs between {@code before} and {@code after}. */
    public static List<String> changedSensitiveFieldNames(
            List<FieldDefinition> definitions, Map<String, Object> before, Map<String, Object> after) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        List<String> changed = new ArrayList<>();
        for (FieldDefinition definition : definitions) {
            if (!definition.isSensitive()) {
                continue;
            }
            Object previous = before.get(definition.name());
            Object next = after.get(definition.name());
            if (!Objects.equals(previous, next)) {
                changed.add(definition.name());
            }
        }
        return List.copyOf(changed);
    }
}
