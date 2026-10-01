package com.flowforge.domain.dossier;

import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldValidationError;
import com.flowforge.domain.expression.model.ExpressionEvaluationException;
import com.flowforge.domain.expression.model.ExpressionResult;
import com.flowforge.domain.expression.model.InvalidExpressionException;
import com.flowforge.domain.expression.port.out.ExpressionEnginePort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO §80 (D1, règles sur LC2) — champs calculés, visibilité et obligation conditionnelles d'un type de dossier.
 *
 * <p>Toutes les expressions (JSONata borné, jamais JUEL) s'évaluent sur {@code {dossier: {fields: …}}} où {@code fields} ne
 * contient que les champs <b>déclarés, simples (non calculés) et non sensibles</b> : un champ sensible n'alimente jamais une
 * règle (D-EXPR-CONTEXT), et l'absence de chaînage (une formule ne lit pas un autre champ calculé) rend l'évaluation
 * déterministe et sans cycle. Ordre : (1) visibilité, (2) obligation, (3) calcul sur ce qui reste visible.
 *
 * <ul>
 *   <li>un champ <b>calculé</b> est toujours recalculé ; une valeur fournie est refusée ({@code COMPUTED_READ_ONLY}) ; un calcul
 *       sans valeur retire le champ, un calcul en erreur refuse l'enregistrement ({@code RULE_FAILED}) ;</li>
 *   <li>un champ <b>non applicable</b> ({@code visibleWhen} faux) n'est jamais obligatoire ; une valeur <em>fournie</em> pour lui
 *       est refusée ({@code NOT_APPLICABLE}), une valeur <em>déjà stockée</em> qui devient sans objet est retirée ;</li>
 *   <li>{@code requiredWhen} vrai rend le champ obligatoire en plus de {@code required}.</li>
 * </ul>
 */
public final class DossierFieldRules {

    /** Nombre maximal d'expressions (calculs + visibilité + obligation) par type de dossier. */
    public static final int MAX_EXPRESSIONS_PER_TYPE = 40;

    private static final Pattern FIELD_REFERENCE = Pattern.compile("\\bfields\\.([A-Za-z_][A-Za-z0-9_]*)");

    private static final DossierFieldRules DISABLED = new DossierFieldRules(null);

    private final ExpressionEnginePort engine;

    /** @param engine {@code null} : règles désactivées (les définitions qui en portent sont refusées à la validation) */
    public DossierFieldRules(ExpressionEnginePort engine) {
        this.engine = engine;
    }

    /** Sans moteur : aucune règle évaluée (tests et assemblages qui n'en utilisent pas). */
    public static DossierFieldRules disabled() {
        return DISABLED;
    }

    /**
     * @param fields champs fusionnés après la saisie (valeurs stockées + saisies)
     * @param effectiveDefinitions définitions à passer au validateur : obligations ajustées par les règles
     * @param errors erreurs de règle (à ajouter à celles du validateur)
     * @param hidden noms des champs que la visibilité rend sans objet
     * @param requiredByRule noms des champs rendus obligatoires par {@code requiredWhen} (et applicables)
     */
    public record Outcome(
            Map<String, Object> fields,
            List<FieldDefinition> effectiveDefinitions,
            List<FieldValidationError> errors,
            Set<String> hidden,
            Set<String> requiredByRule) {}

    public static boolean hasRules(List<FieldDefinition> definitions) {
        return definitions.stream().anyMatch(d -> d.formula() != null || d.visibleWhen() != null || d.requiredWhen() != null);
    }

    /**
     * @param explicitKeys clés que l'appelant vient de <b>fournir</b> (corps de création, patch de mise à jour) — les autres
     *     viennent du stockage ou des valeurs par défaut
     */
    public Outcome apply(List<FieldDefinition> definitions, Map<String, Object> fields, Set<String> explicitKeys) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(fields, "fields");
        if (!hasRules(definitions)) {
            return new Outcome(fields, definitions, List.of(), Set.of(), Set.of());
        }
        if (engine == null) {
            throw new IllegalStateException("the dossier type carries rules but no expression engine is configured");
        }
        List<FieldValidationError> errors = new ArrayList<>();
        Map<String, Object> merged = new LinkedHashMap<>(fields);

        Set<String> computedNames = new HashSet<>();
        for (FieldDefinition d : definitions) {
            if (d.hasFormula()) {
                computedNames.add(d.name());
                if (explicitKeys != null && explicitKeys.contains(d.name())) {
                    errors.add(FieldValidationError.computedReadOnly(d.name()));
                }
                merged.remove(d.name());
            }
        }

        // 1. Visibilité et obligation : évaluées sur les champs simples tels que fournis (aucune règle ne dépend d'une autre).
        Map<String, Object> context = context(definitions, merged);
        Set<String> hidden = new HashSet<>();
        Set<String> requiredNow = new HashSet<>();
        for (FieldDefinition d : definitions) {
            if (d.visibleWhen() != null && !evaluateBoolean(d, d.visibleWhen(), "visibleWhen", context, errors)) {
                hidden.add(d.name());
            }
            if (d.requiredWhen() != null && evaluateBoolean(d, d.requiredWhen(), "requiredWhen", context, errors)) {
                requiredNow.add(d.name());
            }
        }
        for (String name : hidden) {
            if (explicitKeys != null && explicitKeys.contains(name) && merged.containsKey(name)) {
                errors.add(FieldValidationError.notApplicable(name));
            }
            merged.remove(name);
        }

        // 2. Calcul, sur les champs simples restés applicables.
        Map<String, Object> computeContext = context(definitions, merged);
        for (FieldDefinition d : definitions) {
            if (!d.hasFormula()) {
                continue;
            }
            try {
                ExpressionResult result = engine.evaluate(d.formula(), computeContext);
                if (result.defined() && result.value() != null) {
                    merged.put(d.name(), result.value());
                }
            } catch (InvalidExpressionException | ExpressionEvaluationException e) {
                errors.add(FieldValidationError.ruleFailed(d.name(), "The formula of this field could not be evaluated: " + e.getMessage()));
            }
        }

        List<FieldDefinition> effective = new ArrayList<>(definitions.size());
        for (FieldDefinition d : definitions) {
            if (hidden.contains(d.name())) {
                effective.add(d.withRequired(false));
            } else if (requiredNow.contains(d.name()) && !d.required()) {
                effective.add(d.withRequired(true));
            } else {
                effective.add(d);
            }
        }
        requiredNow.removeAll(hidden);
        return new Outcome(merged, List.copyOf(effective), List.copyOf(errors), Set.copyOf(hidden), Set.copyOf(requiredNow));
    }

    /**
     * Valeurs des champs qu'une règle peut lire — pour l'aperçu des règles (SPA, MCP) : mêmes champs que l'évaluation réelle.
     */
    Map<String, Object> context(List<FieldDefinition> definitions, Map<String, Object> fields) {
        Map<String, Object> readable = new LinkedHashMap<>();
        for (FieldDefinition d : definitions) {
            Object value = fields.get(d.name());
            if (!d.isSensitive() && !d.hasFormula() && value != null) {
                readable.put(d.name(), value);
            }
        }
        return Map.of("dossier", Map.of("fields", readable));
    }

    private boolean evaluateBoolean(
            FieldDefinition field, String expression, String which, Map<String, Object> context, List<FieldValidationError> errors) {
        try {
            ExpressionResult result = engine.evaluate(expression, context);
            if (!result.defined()) {
                return false;
            }
            if (result.value() instanceof Boolean value) {
                return value;
            }
            errors.add(FieldValidationError.ruleFailed(field.name(), "The " + which + " rule of this field must evaluate to true or false"));
            return false;
        } catch (InvalidExpressionException | ExpressionEvaluationException e) {
            errors.add(FieldValidationError.ruleFailed(field.name(), "The " + which + " rule of this field could not be evaluated: " + e.getMessage()));
            return false;
        }
    }

    /**
     * Validation d'un type à sa définition : syntaxe et constructions permises (moteur), références aux seuls champs simples non
     * sensibles du type, nombre d'expressions. Sans moteur, toute règle est refusée.
     *
     * @throws IllegalArgumentException définition refusée
     */
    public void validateDefinitions(List<FieldDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        if (!hasRules(definitions)) {
            return;
        }
        if (engine == null) {
            throw new IllegalArgumentException("field rules (formula, visibleWhen, requiredWhen) are not available on this instance");
        }
        Set<String> readable = new HashSet<>();
        for (FieldDefinition d : definitions) {
            if (!d.isSensitive() && !d.hasFormula()) {
                readable.add(d.name());
            }
        }
        int count = 0;
        for (FieldDefinition d : definitions) {
            for (String[] rule : new String[][] {{"formula", d.formula()}, {"visibleWhen", d.visibleWhen()}, {"requiredWhen", d.requiredWhen()}}) {
                String expression = rule[1];
                if (expression == null) {
                    continue;
                }
                if (++count > MAX_EXPRESSIONS_PER_TYPE) {
                    throw new IllegalArgumentException("a dossier type carries at most " + MAX_EXPRESSIONS_PER_TYPE + " rules");
                }
                try {
                    engine.validate(expression);
                } catch (InvalidExpressionException e) {
                    throw new IllegalArgumentException(
                            "field '" + d.name() + "' " + rule[0] + " is invalid: " + e.getMessage());
                }
                Matcher m = FIELD_REFERENCE.matcher(expression);
                while (m.find()) {
                    if (!readable.contains(m.group(1))) {
                        throw new IllegalArgumentException("field '" + d.name() + "' " + rule[0] + " reads '" + m.group(1)
                                + "', which is not a plain (non-computed, non-sensitive) field of this type: rules can only "
                                + "read those");
                    }
                }
            }
        }
    }
}
