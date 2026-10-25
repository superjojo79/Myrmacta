package be.myrmacta.domain.dossier;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.FieldDefinition;
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
 * <p>{@link AccessContext.User} (non-admin) and {@link AccessContext.External} (portal third
 * party, TODO §68) are redacted. {@link AccessContext.System} callers (document
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
        boolean privileged = switch (ctx) {
            case AccessContext.User user -> user.hasRole("TENANT_ADMIN");
            case AccessContext.System ignored -> true;
            case AccessContext.Service ignored -> true;
            // TODO §68 D-PORTAL-ACTOR : un tiers externe n'est jamais privilégié (défense en
            // profondeur — le portail ne renvoie de toute façon aucun champ du dossier).
            case AccessContext.External ignored -> false;
        };
        if (privileged) {
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

    /**
     * Fields that are <b>declared and not sensitive</b> — an allowlist, unlike {@link #withoutSensitiveKeys}, which only
     * removes what is currently declared sensitive and therefore exposes a key that is still stored but no longer (or
     * never) declared (a renamed or removed field, a field of a type that allows unknown fields). Used where a
     * value would be copied into an arbitrary computation (business expressions, TODO §75). Null values are kept out
     * of the result (immutable-map contract), not turned into a failure.
     */
    public static Map<String, Object> declaredNonSensitiveFields(Dossier dossier, List<FieldDefinition> fieldDefinitions) {
        Objects.requireNonNull(dossier, "dossier");
        Objects.requireNonNull(fieldDefinitions, "fieldDefinitions");
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (FieldDefinition definition : fieldDefinitions) {
            Object value = dossier.fields().get(definition.name());
            if (!definition.isSensitive() && value != null) {
                out.put(definition.name(), value);
            }
        }
        return out;
    }

    /**
     * Strips sensitive keys for a shared artifact (BI CSV). Unlike {@link #redact}, this does not
     * depend on the caller: a system job must not keep values that a later non-admin download
     * would expose.
     */
    public static Map<String, Object> withoutSensitiveKeys(Dossier dossier, List<FieldDefinition> fieldDefinitions) {
        Objects.requireNonNull(dossier, "dossier");
        Objects.requireNonNull(fieldDefinitions, "fieldDefinitions");
        Map<String, Object> copy = null;
        for (FieldDefinition definition : fieldDefinitions) {
            if (definition.isSensitive() && dossier.fields().containsKey(definition.name())) {
                if (copy == null) {
                    copy = new HashMap<>(dossier.fields());
                }
                copy.remove(definition.name());
            }
        }
        return copy == null ? dossier.fields() : Map.copyOf(copy);
    }
}
