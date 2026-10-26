package be.myrmacta.domain.precheck.model;

import be.myrmacta.domain.dossier.model.PieceType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO §106 (issue #211, lot B, P5, C4, R2, R5, R6, HC4, D-PRECHECK-SPEC) — ce que la brique « Pré-vérification IA »
 * contrôle, lu dans les attributs littéraux du BPMN déployé (jamais dans une variable) :
 * <ul>
 *   <li>{@code flowable:precheckPieces} — une ligne par type de pièce : {@code clé : obligatoire, lire} (l'un, l'autre
 *       ou les deux) ;</li>
 *   <li>{@code flowable:precheckFields} — champs envoyés au modèle, séparés par des virgules ;</li>
 *   <li>{@code flowable:precheckQuestions} — une ligne par question fermée : {@code clé = question} ;</li>
 *   <li>{@code flowable:precheckRules} — une ligne par constat tiré d'une variable posée avant :
 *       {@code variable:<nom> <condition> => <TYPE> : <message>} ;</li>
 *   <li>{@code flowable:precheckResult} — variable qui reçoit les comptes déterministes (facultatif).</li>
 * </ul>
 * Les messages d'erreur nomment l'attribut et la ligne, jamais une valeur de dossier.
 */
public record PrecheckSpec(
        List<PieceRequirement> pieces,
        List<String> fields,
        List<Question> questions,
        List<Rule> rules,
        String resultVariable) {

    public static final String PIECES = "precheckPieces";
    public static final String FIELDS = "precheckFields";
    public static final String QUESTIONS = "precheckQuestions";
    public static final String RULES = "precheckRules";
    public static final String RESULT = "precheckResult";

    public static final int MAX_PIECES = 30;
    public static final int MAX_READ_PIECES = 10;
    public static final int MAX_FIELDS = 30;
    public static final int MAX_QUESTIONS = 20;
    public static final int MAX_QUESTION_CHARS = 300;
    public static final int MAX_RULES = 10;
    public static final int MAX_RULE_MESSAGE_CHARS = 200;

    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,59}");
    private static final Pattern PIECE_LINE = Pattern.compile("([^:]+):(.+)");
    private static final Pattern QUESTION_LINE = Pattern.compile("([^=]+)=(.+)");
    private static final Pattern RULE_LINE = Pattern.compile(
            "variable:([A-Za-z][A-Za-z0-9_]{0,59})\\s+(.+?)\\s*=>\\s*(MANQUANT|INCOHERENCE|A_VERIFIER)\\s*:\\s*(.+)");
    private static final Pattern NUMBER_CONDITION = Pattern.compile("([<>=])\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern TEXT_CONDITION = Pattern.compile("=\\s*\"([^\"]{0,200})\"");

    /** Pièce contrôlée : {@code required} = la passe déterministe vérifie sa présence ; {@code read} = texte au modèle. */
    public record PieceRequirement(String key, boolean required, boolean read) {
        public PieceRequirement {
            Objects.requireNonNull(key, "key");
            if (!required && !read) {
                throw new IllegalArgumentException("a piece requirement must be required, read or both");
            }
        }
    }

    public record Question(String key, String text) {
        public Question {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(text, "text");
        }
    }

    /** Condition d'une règle sur la valeur d'une variable de process. */
    public record Condition(Operator operator, BigDecimal number, String text) {
        public enum Operator { IS_TRUE, IS_FALSE, IS_EMPTY, IS_NOT_EMPTY, GREATER, LESS, EQUALS_NUMBER, EQUALS_TEXT }

        public Condition {
            Objects.requireNonNull(operator, "operator");
        }

        /** Vrai si la valeur satisfait la condition ; une valeur d'un autre type ne la satisfait jamais. */
        public boolean test(Object value) {
            return switch (operator) {
                case IS_TRUE -> Boolean.TRUE.equals(value) || "true".equals(value);
                case IS_FALSE -> Boolean.FALSE.equals(value) || "false".equals(value);
                case IS_EMPTY -> empty(value);
                case IS_NOT_EMPTY -> !empty(value);
                case GREATER, LESS, EQUALS_NUMBER -> {
                    BigDecimal actual = numberOf(value);
                    if (actual == null) {
                        yield false; // une valeur non numérique ne satisfait jamais une comparaison
                    }
                    int c = actual.compareTo(number);
                    yield operator == Operator.GREATER ? c > 0 : operator == Operator.LESS ? c < 0 : c == 0;
                }
                case EQUALS_TEXT -> value instanceof CharSequence chars && chars.toString().strip().equals(text);
            };
        }

        private static BigDecimal numberOf(Object value) {
            return switch (value) {
                case Double d when d.isNaN() || d.isInfinite() -> null;
                case Float f when f.isNaN() || f.isInfinite() -> null;
                case Number n -> new BigDecimal(n.toString());
                case CharSequence s when s.toString().strip().matches("-?\\d+(\\.\\d+)?") -> new BigDecimal(s.toString().strip());
                case null, default -> null;
            };
        }

        private static boolean empty(Object value) {
            return value == null
                    || value instanceof CharSequence s && s.toString().isBlank()
                    || value instanceof java.util.Collection<?> c && c.isEmpty()
                    || value instanceof Map<?, ?> m && m.isEmpty();
        }
    }

    public record Rule(String variable, Condition condition, FindingKind kind, String message) {
        public Rule {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(message, "message");
        }
    }

    public PrecheckSpec {
        pieces = pieces == null ? List.of() : List.copyOf(pieces);
        fields = fields == null ? List.of() : List.copyOf(fields);
        questions = questions == null ? List.of() : List.copyOf(questions);
        rules = rules == null ? List.of() : List.copyOf(rules);
        if (pieces.isEmpty() && questions.isEmpty() && rules.isEmpty()) {
            throw new IllegalArgumentException(
                    "an AI pre-check needs at least one piece, question or rule (precheckPieces, precheckQuestions, precheckRules)");
        }
    }

    /** Pièces dont le texte part au modèle. */
    public List<PieceRequirement> readPieces() {
        return pieces.stream().filter(PieceRequirement::read).toList();
    }

    /** Vrai si un appel au modèle est nécessaire (pièce à lire, champ ou question). */
    public boolean needsModel() {
        return !questions.isEmpty() || !fields.isEmpty() || !readPieces().isEmpty();
    }

    /** Spécification complète depuis les attributs {@code flowable:precheck*} d'une activité. */
    public static PrecheckSpec parse(Map<String, String> attributes, String activityId) {
        String owner = "BPMN activity " + activityId;
        try {
            return new PrecheckSpec(
                    parsePieces(attributes.get(PIECES), owner),
                    parseFields(attributes.get(FIELDS), owner),
                    parseQuestions(attributes.get(QUESTIONS), owner),
                    parseRules(attributes.get(RULES), owner),
                    blankToNull(attributes.get(RESULT)));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("BPMN")) {
                throw ex;
            }
            throw new IllegalArgumentException(owner + ": " + ex.getMessage(), ex);
        }
    }

    public static List<PieceRequirement> parsePieces(String raw, String owner) {
        List<PieceRequirement> pieces = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        int line = 0;
        for (String text : lines(raw)) {
            line++;
            String where = owner + " flowable:" + PIECES + " line " + line;
            Matcher m = PIECE_LINE.matcher(text);
            if (!m.matches()) {
                throw new IllegalArgumentException(where + ": expected 'key : obligatoire, lire'");
            }
            String key = m.group(1).strip();
            if (!PieceType.isValidKey(key)) {
                throw new IllegalArgumentException(where + ": invalid piece type key '" + key + "'");
            }
            if (!keys.add(key)) {
                throw new IllegalArgumentException(where + ": duplicate piece type key '" + key + "'");
            }
            boolean required = false;
            boolean read = false;
            for (String flag : m.group(2).split(",")) {
                switch (flag.strip()) {
                    case "obligatoire" -> required = true;
                    case "lire" -> read = true;
                    default -> throw new IllegalArgumentException(
                            where + ": unknown flag '" + flag.strip() + "' (obligatoire, lire)");
                }
            }
            pieces.add(new PieceRequirement(key, required, read));
        }
        if (pieces.size() > MAX_PIECES) {
            throw new IllegalArgumentException(owner + " flowable:" + PIECES + ": at most " + MAX_PIECES + " lines");
        }
        if (pieces.stream().filter(PieceRequirement::read).count() > MAX_READ_PIECES) {
            throw new IllegalArgumentException(
                    owner + " flowable:" + PIECES + ": at most " + MAX_READ_PIECES + " piece types to read");
        }
        return pieces;
    }

    public static List<String> parseFields(String raw, String owner) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> fields = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String name = part.strip();
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(owner + " flowable:" + FIELDS + ": invalid field name '" + name + "'");
            }
            if (!fields.add(name)) {
                throw new IllegalArgumentException(owner + " flowable:" + FIELDS + ": duplicate field '" + name + "'");
            }
        }
        if (fields.size() > MAX_FIELDS) {
            throw new IllegalArgumentException(owner + " flowable:" + FIELDS + ": at most " + MAX_FIELDS + " fields");
        }
        return List.copyOf(fields);
    }

    public static List<Question> parseQuestions(String raw, String owner) {
        List<Question> questions = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        int line = 0;
        for (String text : lines(raw)) {
            line++;
            String where = owner + " flowable:" + QUESTIONS + " line " + line;
            Matcher m = QUESTION_LINE.matcher(text);
            if (!m.matches()) {
                throw new IllegalArgumentException(where + ": expected 'key = question'");
            }
            String key = m.group(1).strip();
            String question = m.group(2).strip();
            if (!NAME.matcher(key).matches()) {
                throw new IllegalArgumentException(where + ": invalid question key '" + key + "'");
            }
            if (!keys.add(key)) {
                throw new IllegalArgumentException(where + ": duplicate question key '" + key + "'");
            }
            if (question.length() > MAX_QUESTION_CHARS) {
                throw new IllegalArgumentException(where + ": question exceeds " + MAX_QUESTION_CHARS + " characters");
            }
            questions.add(new Question(key, question));
        }
        if (questions.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException(owner + " flowable:" + QUESTIONS + ": at most " + MAX_QUESTIONS + " questions");
        }
        return questions;
    }

    public static List<Rule> parseRules(String raw, String owner) {
        List<Rule> rules = new ArrayList<>();
        int line = 0;
        for (String text : lines(raw)) {
            line++;
            String where = owner + " flowable:" + RULES + " line " + line;
            Matcher m = RULE_LINE.matcher(text);
            if (!m.matches()) {
                throw new IllegalArgumentException(where
                        + ": expected 'variable:<name> <condition> => MANQUANT|INCOHERENCE|A_VERIFIER : <message>'");
            }
            String message = m.group(4).strip();
            if (message.length() > MAX_RULE_MESSAGE_CHARS) {
                throw new IllegalArgumentException(where + ": message exceeds " + MAX_RULE_MESSAGE_CHARS + " characters");
            }
            FindingKind kind = switch (m.group(3)) {
                case "MANQUANT" -> FindingKind.MISSING;
                case "INCOHERENCE" -> FindingKind.INCONSISTENT;
                default -> FindingKind.TO_CHECK;
            };
            rules.add(new Rule(m.group(1), condition(m.group(2).strip(), where), kind, message));
        }
        if (rules.size() > MAX_RULES) {
            throw new IllegalArgumentException(owner + " flowable:" + RULES + ": at most " + MAX_RULES + " rules");
        }
        return rules;
    }

    private static Condition condition(String raw, String where) {
        switch (raw) {
            case "est vrai" -> {
                return new Condition(Condition.Operator.IS_TRUE, null, null);
            }
            case "est faux" -> {
                return new Condition(Condition.Operator.IS_FALSE, null, null);
            }
            case "est vide" -> {
                return new Condition(Condition.Operator.IS_EMPTY, null, null);
            }
            case "n'est pas vide", "n’est pas vide" -> {
                return new Condition(Condition.Operator.IS_NOT_EMPTY, null, null);
            }
            default -> {
                Matcher text = TEXT_CONDITION.matcher(raw);
                if (text.matches()) {
                    return new Condition(Condition.Operator.EQUALS_TEXT, null, text.group(1));
                }
                Matcher number = NUMBER_CONDITION.matcher(raw);
                if (number.matches()) {
                    Condition.Operator operator = switch (number.group(1)) {
                        case ">" -> Condition.Operator.GREATER;
                        case "<" -> Condition.Operator.LESS;
                        default -> Condition.Operator.EQUALS_NUMBER;
                    };
                    return new Condition(operator, new BigDecimal(number.group(2)), null);
                }
                throw new IllegalArgumentException(where + ": unknown condition '" + raw
                        + "' (est vrai, est faux, est vide, n'est pas vide, > N, < N, = N, = \"texte\")");
            }
        }
    }

    private static List<String> lines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return raw.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
