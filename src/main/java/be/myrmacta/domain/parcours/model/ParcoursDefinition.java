package be.myrmacta.domain.parcours.model;

import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * TODO §88 (LC5) — un parcours : une suite d'étapes lisible par un métier, compilée en BPMN (sens unique, D-PARCOURS-ROUNDTRIP).
 * Une étape sans {@code next} passe à la suivante de la liste ; la dernière termine le parcours. Une étape {@link Kind#DECISION}
 * choisit sa suite selon des comparaisons simples (la grammaire des conditions de passerelle de la liste blanche BPMN) et peut
 * revenir en arrière (boucle de correction). Les paramètres d'une étape portent le nom des paramètres de la brique correspondante.
 *
 * <p>{@code dossierTypeCode} (facultatif) nomme le type de dossier sur lequel le concepteur travaille : il sert à proposer les champs
 * de ce type dans les formules et les correspondances. Il n'entre pas dans le BPMN compilé.
 */
public record ParcoursDefinition(
        List<WorkflowStatusDefinition> statuses,
        List<DossierStatus> allowedDossierStatuses,
        DossierStatus onStartDossierStatus,
        List<Step> steps,
        String dossierTypeCode) {

    /** Même forme que {@code DossierType.code}. */
    private static final Pattern DOSSIER_TYPE_CODE = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public ParcoursDefinition(
            List<WorkflowStatusDefinition> statuses, List<DossierStatus> allowedDossierStatuses, DossierStatus onStartDossierStatus,
            List<Step> steps) {
        this(statuses, allowedDossierStatuses, onStartDossierStatus, steps, null);
    }

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
    /** Variables ou objets du moteur : une condition qui les nomme compare un objet de Flowable, pas une réponse de formulaire. */
    private static final java.util.Set<String> ENGINE_VARIABLES = java.util.Set.of(
            "execution", "task", "initiator", "authenticatedUserId", "processInstanceId", "processDefinitionId", "executionId",
            "taskId", "dossierId", "dossierWorkflowLinkId", "connectorInput", "connectorResult", "now", "variables", "dossier");
    private static final Pattern SIGNED_OR_DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");

    /**
     * Vrai si chaque point de code est un caractère XML 1.0 valide (tab, saut de ligne, retour chariot, U+0020–D7FF, U+E000–FFFD, plans
     * supplémentaires) : un surrogate isolé deviendrait {@code ?} à l'encodage UTF-8, la valeur lue à l'exécution ne serait plus celle saisie.
     */
    public static boolean xmlSafe(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            boolean ok = cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF) || (cp >= 0xE000 && cp <= 0xFFFD)
                    || (cp >= 0x10000 && cp <= 0x10FFFF);
            if (!ok || (Character.isSurrogate(text.charAt(i)) && Character.charCount(cp) == 1)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    public ParcoursDefinition {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        allowedDossierStatuses = allowedDossierStatuses == null ? List.of() : List.copyOf(allowedDossierStatuses);
        steps = steps == null ? List.of() : List.copyOf(steps);
        if (steps.size() > MAX_STEPS) {
            throw new InvalidParcoursException("a parcours has at most " + MAX_STEPS + " steps");
        }
        dossierTypeCode = dossierTypeCode == null || dossierTypeCode.isBlank() ? null : dossierTypeCode.strip();
        if (dossierTypeCode != null && !DOSSIER_TYPE_CODE.matcher(dossierTypeCode).matches()) {
            throw new InvalidParcoursException("a dossier type code holds letters, digits, _ or - (max 64)");
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
            if (ENGINE_VARIABLES.contains(variable)) {
                throw new InvalidParcoursException("a branch variable may not be the engine variable '" + variable + "': name a question field or an expression result");
            }
            if (EL_RESERVED.contains(variable.toLowerCase(java.util.Locale.ROOT))) {
                throw new InvalidParcoursException("a branch variable may not be the reserved word '" + variable + "'");
            }
            if (value == null || value.isBlank() || !xmlSafe(value) || !BRANCH_VALUE.matcher(value).matches()) {
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
            if (!xmlSafe(label)) {
                throw new InvalidParcoursException("a step label holds only valid XML characters (step " + id + ")");
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
