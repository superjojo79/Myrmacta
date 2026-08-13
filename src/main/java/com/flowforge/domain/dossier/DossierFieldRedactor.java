package com.flowforge.domain.dossier;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.FieldDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * D-SENSITIVE = A: fields marked {@link FieldDefinition#isSensitive()} are readable in the
 * clear only by TENANT_ADMIN — every other viewer gets the key stripped from {@code fields}
 * instead of the stored value. Mirrors the masking already enforced client-side in
 * {@code SensitiveValue.vue} (which keys off the field's {@code sensitive} flag, not its
 * value, so an absent key still renders masked), but as a server-side guarantee: the value
 * must never leave the domain toward a human-facing response for a non-admin, regardless of
 * what the frontend does with it. The key is removed rather than nulled because {@link Dossier}
 * stores an immutable map that rejects null values.
 *
 * <p>Only {@link AccessContext.User} is redacted. {@link AccessContext.System} callers (document
 * generation, connector triggers) need the real values to do their job and never render them to
 * a browser, so they pass through unchanged.
 */
public final class DossierFieldRedactor {

    private DossierFieldRedactor() {
    }

    public static Dossier redact(Dossier dossier, List<FieldDefinition> fieldDefinitions, AccessContext ctx) {
        Objects.requireNonNull(dossier, "dossier");
        Objects.requireNonNull(fieldDefinitions, "fieldDefinitions");
        Objects.requireNonNull(ctx, "ctx");
        if (!(ctx instanceof AccessContext.User user) || user.hasRole("TENANT_ADMIN")) {
            return dossier;
        }
        Map<String, Object> redacted = null;
        for (FieldDefinition definition : fieldDefinitions) {
            if (definition.isSensitive() && dossier.fields().containsKey(definition.name())) {
                if (redacted == null) {
                    redacted = new HashMap<>(dossier.fields());
                }
                redacted.remove(definition.name());
            }
        }
        return redacted == null ? dossier : dossier.withFields(redacted);
    }
}
