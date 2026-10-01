package com.flowforge.domain.parcours.model;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * TODO §88 (LC5) — un parcours : une suite d'étapes lisible par un métier, compilée en BPMN (sens unique, D-PARCOURS-ROUNDTRIP).
 * Une étape sans {@code next} passe à la suivante de la liste ; la dernière termine le parcours. Une étape {@link Kind#DECISION}
 * choisit sa suite selon des comparaisons simples (la grammaire des conditions de passerelle de la liste blanche BPMN) et peut
 * revenir en arrière (boucle de correction). Les paramètres d'une étape portent le nom des paramètres de la brique correspondante.
 */
public record ParcoursDefinition(
        List<WorkflowStatusDefinition> statuses,
        List<DossierStatus> allowedDossierStatuses,
        DossierStatus onStartDossierStatus,
        List<Step> steps) {

    public static final int MAX_STEPS = 60;
    public static final int MAX_BRANCHES = 8;
    public static final int MAX_PARAM_CHARS = 8000;
    /** Identifiant d'étape = identifiant BPMN : camelCase court. */
    public static final Pattern STEP_ID = Pattern.compile("[a-z][A-Za-z0-9]{0,39}");
    public static final Pattern VARIABLE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,59}");
    /** Mots de l'Unified EL : ils ne sont pas des identifiants, une condition qui les nomme échouerait à la passerelle. */
    private static final java.util.Set<String> EL_RESERVED = java.util.Set.of(
            "true", "false", "null", "empty", "not", "and", "or", "eq", "ne", "lt", "gt", "le", "ge", "div", "mod", "instanceof");
    /** Ce que la grammaire de condition du parseur de déploiement sait écrire dans un littéral : ni point, ni parenthèse, crochet, @, &, <, >, ;. */
    private static final Pattern BRANCH_VALUE = Pattern.compile("[^'\"\\\\$#{}.()\\[\\]@&<>;\\p{Cntrl}]{1,100}");
    private static final Pattern SIGNED_OR_DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");

    public ParcoursDefinition {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        allowedDossierStatuses = allowedDossierStatuses == null ? List.of() : List.copyOf(allowedDossierStatuses);
        steps = steps == null ? List.of() : List.copyOf(steps);
        if (steps.size() > MAX_STEPS) {
            throw new InvalidParcoursException("a parcours has at most " + MAX_STEPS + " steps");
        }
    }

    public enum Kind {
        /** Tâche humaine : question d'un schéma. */
        QUESTION,
        /** Tâche externe : un tiers répond par le portail. */
        EXTERNAL,
        /** Génère un document, et le joint au dossier sauf {@code attach=false}. */
        DOCUMENT,
        NOTIFY,
        CONNECTOR,
        /** Évalue une table de décision DMN (ses sorties deviennent des variables). */
        DMN,
        /** Calcule une valeur (expression métier) dans une variable. */
        EXPRESSION,
        /** Enregistre des valeurs calculées dans des champs du dossier. */
        MAP_FIELDS,
        SET_WORKFLOW_STATUS,
        SET_DOSSIER_STATUS,
        /** Attente d'une durée (ISO 8601, ex. {@code PT24H}, {@code P2D}). */
        WAIT,
        /** Choix de la suite selon des comparaisons ; {@code defaultNext} obligatoire. */
        DECISION
    }

    public enum Operator {
        EQ("=="),
        NE("!="),
        GT(">"),
        GE(">="),
        LT("<"),
        LE("<=");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    /** Une branche de décision : {@code variable operator value} (valeur littérale), sinon la suivante. */
    public record Branch(String variable, Operator operator, String value, String next) {
        public Branch {
            Objects.requireNonNull(operator, "operator");
            if (variable == null || !VARIABLE.matcher(variable).matches()) {
                throw new InvalidParcoursException("a branch variable is a plain name (letters, digits, _): " + variable);
            }
            if (EL_RESERVED.contains(variable.toLowerCase(java.util.Locale.ROOT))) {
                throw new InvalidParcoursException("a branch variable may not be the reserved word '" + variable + "'");
            }
            if (value == null || !BRANCH_VALUE.matcher(value).matches()) {
                throw new InvalidParcoursException("a branch value is a short literal (max 100) without quotes, $, #, braces, dots, "
                        + "parentheses, brackets, @, &, <, > or ; : " + variable);
            }
            if (SIGNED_OR_DECIMAL.matcher(value).matches() && !value.chars().allMatch(Character::isDigit)) {
                throw new InvalidParcoursException("a branch compares with a whole number or a text: negative and decimal numbers "
                        + "are not expressible in a BPMN condition (" + variable + " " + value + ")");
            }
            if (next == null || next.isBlank()) {
                throw new InvalidParcoursException("a branch needs a destination step (next)");
            }
        }
    }

    public record Step(
            String id, String label, Kind kind, Map<String, String> params, String next, List<Branch> branches, String defaultNext) {
        public Step {
            if (id == null || !STEP_ID.matcher(id).matches()) {
                throw new InvalidParcoursException("a step id is camelCase (letters and digits, max 40, starting with a lowercase letter): " + id);
            }
            Objects.requireNonNull(kind, "kind");
            label = label == null || label.isBlank() ? id : label.strip();
            if (label.contains("${") || label.contains("#{")) {
                throw new InvalidParcoursException("a step label is plain text, never an expression (step " + id + ")");
            }
            if (label.length() > 200 || label.chars().anyMatch(Character::isISOControl)) {
                throw new InvalidParcoursException("a step label is a single line of at most 200 characters (step " + id + ")");
            }
            Map<String, String> copy = new java.util.LinkedHashMap<>();
            if (params != null) {
                params.forEach((k, v) -> {
                    if (k != null && v != null && !v.isBlank()) {
                        if (v.length() > MAX_PARAM_CHARS) {
                            throw new InvalidParcoursException("parameter " + k + " is too long (step " + id + ")");
                        }
                        copy.put(k, v);
                    }
                });
            }
            params = Map.copyOf(copy);
            branches = branches == null ? List.of() : List.copyOf(branches);
            if (branches.size() > MAX_BRANCHES) {
                throw new InvalidParcoursException("a decision has at most " + MAX_BRANCHES + " branches (step " + id + ")");
            }
            next = next == null || next.isBlank() ? null : next;
            defaultNext = defaultNext == null || defaultNext.isBlank() ? null : defaultNext;
        }
    }
}
