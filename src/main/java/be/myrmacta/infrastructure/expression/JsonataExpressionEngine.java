package be.myrmacta.infrastructure.expression;

import com.dashjoin.jsonata.AstInspector;
import com.dashjoin.jsonata.Functions;
import com.dashjoin.jsonata.JException;
import com.dashjoin.jsonata.Jsonata;
import be.myrmacta.domain.expression.model.ExpressionEvaluationException;
import be.myrmacta.domain.expression.model.ExpressionLimits;
import be.myrmacta.domain.expression.model.ExpressionResult;
import be.myrmacta.domain.expression.model.InvalidExpressionException;
import be.myrmacta.domain.expression.port.out.ExpressionEnginePort;
import java.lang.management.ManagementFactory;
import java.text.Normalizer;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO §75 (LC2) — moteur d'expressions métier JSONata, <b>borné</b> et sans accès au serveur.
 *
 * <p>Principe : les bornes dynamiques (délai, mémoire) ne sont contrôlées qu'aux callbacks d'évaluation de la
 * bibliothèque ; tout ce qui s'exécute <em>en un seul appel natif</em>, ou qui sait masquer les callbacks, leur échappe.
 * Le moteur est donc <b>fermé par défaut</b> (revue adversariale LC2 : deux failles critiques prouvées par sonde) :
 * <ul>
 *   <li><b>liste blanche de fonctions</b> ({@link #ALLOWED_FUNCTIONS}) : toute fonction native non listée — y compris une
 *       fonction ajoutée par une montée de version — est refusée ; {@code $pad}, {@code $replace}, {@code $toMillis} et
 *       {@code $fromMillis} sont remplacées par des versions bornées (texte littéral, sans format utilisateur) ;
 *       {@code $ascii} est ajoutée (D-EXPR-ASCII) : elle ne fait que rétrécir le texte, sans regex ;</li>
 *   <li><b>noms réservés</b> : toute variable nommée {@code __*}, référencée ou liée, est refusée — c'est sous ces noms
 *       que la bibliothèque lit ses callbacks, qu'une affectation {@code $__evaluate_entry := nope} masquait ;</li>
 *   <li><b>statique</b> : longueur, taille et profondeur de l'arbre, littéraux regex (le retour arrière Java n'est pas
 *       interruptible), {@code ${}/{@code #{}, caractères de contrôle ;</li>
 *   <li><b>dynamique</b> : délai, profondeur d'évaluations, mémoire allouée par le thread, et <b>chaque valeur
 *       intermédiaire</b> (liste, objet, chaîne) bornée au nœud qui la produit — un intervalle {@code [1..9999999]}
 *       est refusé avant d'être consommé par un appel natif ;</li>
 *   <li><b>données</b> : contexte copié en types JSON purs, taille bornée en entrée et en sortie, {@code now} injecté ;</li>
 *   <li><b>erreurs</b> : messages neutres (gabarit sans valeurs), aucune cause conservée (le message brut de la
 *       bibliothèque recopie le document racine) ; une {@link OutOfMemoryError} n'est jamais avalée.</li>
 * </ul>
 * Jamais évalué en JUEL ; jamais concaténé dans une expression UEL.
 */
public final class JsonataExpressionEngine implements ExpressionEnginePort {

    private static final Logger LOG = LoggerFactory.getLogger(JsonataExpressionEngine.class);

    /**
     * Fonctions natives permises : calcul, agrégats, texte littéral, structures. Exclues — {@code eval},
     * {@code random}, {@code shuffle}, {@code now}, {@code millis} (non déterministes ou évaluation de texte),
     * {@code match} (regex), {@code formatInteger}/{@code parseInteger} (formats utilisateur), {@code base64*} et
     * {@code encodeUrl*}/{@code decodeUrl*} (inutiles ici).
     */
    static final Set<String> ALLOWED_FUNCTIONS = Set.of(
            "abs", "ceil", "floor", "round", "sqrt", "power", "number", "formatNumber", "formatBase",
            "sum", "count", "max", "min", "average",
            "string", "substring", "substringBefore", "substringAfter", "lowercase", "uppercase", "length", "trim",
            "contains", "split", "join", "pad", "replace", "ascii",
            "boolean", "not", "exists", "type", "keys", "lookup", "append", "reverse", "sort", "distinct", "zip", "map",
            "filter", "reduce", "each", "sift", "single", "spread", "merge", "clone", "assert", "error",
            "toMillis", "fromMillis");

    private static final Set<String> FORBIDDEN_FUNCTIONS = forbiddenFunctions();

    private final ExpressionLimits limits;
    private final Clock clock;
    private final com.sun.management.ThreadMXBean allocations = allocationBean();

    public JsonataExpressionEngine(ExpressionLimits limits, Clock clock) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void validate(String expression) {
        inspect(expression);
    }

    @Override
    public ExpressionResult evaluate(String expression, Map<String, Object> context) {
        inspect(expression);
        Map<String, Object> input = JsonValues.copyContext(context == null ? Map.of() : context, limits.maxInputBytes());
        Map<String, Object> withClock = new LinkedHashMap<>(input);
        withClock.put("now", clock.instant().truncatedTo(ChronoUnit.SECONDS).toString());

        Object raw = run(expression, withClock);
        if (raw == null) {
            return ExpressionResult.undefined();
        }
        if (raw != Jsonata.NULL_VALUE && !isJson(raw)) {
            throw new ExpressionEvaluationException("expression must produce a JSON value, not a function");
        }
        return ExpressionResult.of(JsonValues.copyResult(raw, limits.maxResultBytes()));
    }

    // ---- statique ----------------------------------------------------------------------------------------------

    private void inspect(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new InvalidExpressionException("expression must not be blank");
        }
        if (expression.length() > limits.maxExpressionChars()) {
            throw new InvalidExpressionException(
                    "expression exceeds " + limits.maxExpressionChars() + " characters");
        }
        if (expression.contains("${") || expression.contains("#{")) {
            throw new InvalidExpressionException("expression must not contain ${ or #{ (not a UEL expression)");
        }
        if (expression.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw new InvalidExpressionException("expression contains control characters");
        }
        AstInspector.Report report;
        try {
            report = AstInspector.inspect(expression);
        } catch (JException e) {
            throw new InvalidExpressionException(syntaxMessage(e), Math.max(e.getLocation(), 0));
        } catch (StackOverflowError e) {
            throw new InvalidExpressionException("expression is too complex (nested too deeply)");
        }
        if (report.usesRegex()) {
            throw new InvalidExpressionException(
                    "regular expression literals are not allowed (use a plain string pattern)");
        }
        for (String name : new TreeSet<>(report.variables())) {
            if (name.startsWith("__")) {
                throw new InvalidExpressionException(
                        "variable names starting with __ are reserved for the engine: $" + name);
            }
            if (FORBIDDEN_FUNCTIONS.contains(name)) {
                throw new InvalidExpressionException("function $" + name + " is not allowed" + hint(name));
            }
        }
        if (report.nodes() > limits.maxAstNodes() || report.depth() > limits.maxAstDepth()) {
            throw new InvalidExpressionException("expression is too complex (max " + limits.maxAstNodes()
                    + " nodes, depth " + limits.maxAstDepth() + ")");
        }
    }

    private static String hint(String name) {
        return switch (name) {
            case "now", "millis" -> " (use the `now` context value)";
            case "random", "shuffle" -> " (not deterministic)";
            case "match", "formatInteger", "parseInteger" -> " (regular expressions and format pictures are not supported)";
            default -> "";
        };
    }

    private static Set<String> forbiddenFunctions() {
        Set<String> forbidden = new TreeSet<>(AstInspector.builtinFunctionNames());
        forbidden.removeAll(ALLOWED_FUNCTIONS);
        return Set.copyOf(forbidden);
    }

    private static String syntaxMessage(JException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "invalid expression (" + e.getError() + ")" : message;
    }

    // ---- dynamique ---------------------------------------------------------------------------------------------

    private Object run(String expression, Map<String, Object> input) {
        Jsonata jsonata = Jsonata.jsonata(expression);
        // Un null explicite doit rester distinct d'un résultat absent (NULL_VALUE vs null Java).
        jsonata.setOutputConvertNulls(false);
        Jsonata.Frame bindings = jsonata.createFrame();
        bindBoundedFunctions(bindings);
        bindGuards(bindings);
        try {
            return jsonata.evaluate(input, bindings);
        } catch (ExpressionEvaluationException e) {
            throw e;
        } catch (JException e) {
            throw new ExpressionEvaluationException(runtimeMessage(e));
        } catch (StackOverflowError e) {
            throw new ExpressionEvaluationException("expression is nested too deeply to evaluate");
        } catch (RuntimeException e) {
            Error fatal = fatalCause(e);
            if (fatal instanceof StackOverflowError) {
                throw new ExpressionEvaluationException("expression is nested too deeply to evaluate");
            }
            if (fatal != null) {
                throw fatal; // OutOfMemoryError & co : jamais avalées en « mauvaise requête »
            }
            throw new ExpressionEvaluationException("expression could not be evaluated");
        }
    }

    /** L'erreur JVM que la bibliothèque a enveloppée dans une exception d'exécution, s'il y en a une. */
    private static Error fatalCause(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof Error error) {
                return error;
            }
        }
        return null;
    }

    private static String runtimeMessage(JException e) {
        // `$error(...)` et `$assert(...)` portent un texte choisi par l'auteur : restitué tel quel. Toute autre erreur :
        // gabarit statique sans les valeurs (le message brut recopie les arguments, donc le document racine).
        if ("D3137".equals(e.getError()) || "D3141".equals(e.getError())) {
            String message = e.getMessage();
            return message == null || message.isBlank() ? "expression error (" + e.getError() + ")" : message;
        }
        return AstInspector.neutralMessage(e);
    }

    /**
     * Bornes dynamiques. À l'entrée d'un nœud : la profondeur d'évaluations imbriquées, qui arrête une récursion non
     * terminale avant le débordement de pile (un appel terminal est déroulé en boucle par la bibliothèque : seul le délai
     * l'arrête). À la sortie, juste après le travail natif du nœud : délai, mémoire, valeur produite, travail cumulé et
     * poids déplié. Contrôler aussi délai et mémoire à l'entrée ne gagnerait au plus que le travail d'un nœud (TODO §108 :
     * mutant sans effet observable, retiré).
     */
    private void bindGuards(Jsonata.Frame bindings) {
        long deadline = System.nanoTime() + limits.timeoutMillis() * 1_000_000L;
        long allocationStart = currentAllocated();
        int[] depth = {0};
        long[] work = {0};
        IdentityHashMap<Object, Long> weights = new IdentityHashMap<>();
        bindings.setEvaluateEntryCallback((expr, in, env) -> {
            if (++depth[0] > limits.maxEvalDepth()) {
                throw new ExpressionEvaluationException(
                        "expression nesting exceeds " + limits.maxEvalDepth() + " (non-terminating recursion?)");
            }
        });
        bindings.setEvaluateExitCallback((expr, in, env, result) -> {
            depth[0]--;
            if (System.nanoTime() - deadline > 0) {
                throw new ExpressionEvaluationException(
                        "expression evaluation timeout (" + limits.timeoutMillis() + " ms)");
            }
            if (allocations != null && currentAllocated() - allocationStart > limits.maxAllocatedBytes()) {
                throw new ExpressionEvaluationException(
                        "expression exceeded its memory budget (" + limits.maxAllocatedBytes() + " bytes)");
            }
            checkIntermediateValue(result);
            chargeWork(result, work);
            if (result instanceof List<?> || result instanceof Map<?, ?>) {
                weigh(result, weights); // lève si le poids déplié dépasse la borne
            }
        });
    }

    /**
     * Budget de travail cumulé : à chaque nœud, la taille de la valeur produite. Un aplatissement de chemin
     * ({@code [1..9999].$l}) ou un appel natif appliqué à des références partagées commence par <em>évaluer</em> chaque
     * référence — chacune est comptée ici avant que l'étape native ne s'exécute.
     */
    private void chargeWork(Object value, long[] work) {
        long cost = 1;
        if (value instanceof CharSequence text) {
            cost += text.length() / 8;
        } else if (value instanceof List<?> list) {
            cost += list.size();
        } else if (value instanceof Map<?, ?> map) {
            cost += map.size();
        }
        work[0] += cost;
        if (work[0] > limits.maxWork()) {
            throw new ExpressionEvaluationException(
                    "expression exceeds its work budget (" + limits.maxWork() + " units)");
        }
    }

    /**
     * Poids <b>déplié</b> d'une valeur : chaque occurrence d'un sous-arbre partagé est comptée (les sous-arbres partagés
     * ne sont calculés qu'une fois, par identité). {@code {"a":$c,"b":$c}} imbriqué n fois tient en n objets mais pèse 2^n ;
     * l'égalité et {@code $distinct} le parcourent en entier sans allouer, hors d'atteinte du délai et de la garde mémoire.
     * Refusé dès la construction de la valeur, donc avant tout parcours.
     */
    private long weigh(Object value, IdentityHashMap<Object, Long> memo) {
        if (value == null || value == Jsonata.NULL_VALUE || value instanceof Boolean || value instanceof Number) {
            return 1;
        }
        if (value instanceof CharSequence text) {
            return 1 + text.length() / 16;
        }
        if (AstInspector.isLazyRange(value)) {
            return 1 + ((List<?>) value).size();
        }
        if (!(value instanceof List<?>) && !(value instanceof Map<?, ?>)) {
            return 1;
        }
        Long known = memo.get(value);
        if (known != null) {
            return known;
        }
        long total = 1;
        if (value instanceof List<?> list) {
            for (Object item : list) {
                total += weigh(item, memo);
                requireWeight(total);
            }
        } else {
            for (Object item : ((Map<?, ?>) value).values()) {
                total += 1 + weigh(item, memo);
                requireWeight(total);
            }
        }
        memo.put(value, total);
        return total;
    }

    private void requireWeight(long total) {
        if (total > limits.maxValueWeight()) {
            throw new ExpressionEvaluationException(
                    "an intermediate value is too large once shared parts are counted each time (max "
                            + limits.maxValueWeight() + ")");
        }
    }

    /** Borne toute valeur intermédiaire au nœud qui la produit, avant qu'un appel natif ne la consomme. */
    private void checkIntermediateValue(Object value) {
        if (value instanceof CharSequence s && s.length() > limits.maxResultBytes()) {
            throw new ExpressionEvaluationException(
                    "an intermediate text exceeds " + limits.maxResultBytes() + " characters");
        }
        if (value instanceof List<?> list && list.size() > limits.maxCollectionSize()) {
            throw new ExpressionEvaluationException(
                    "an intermediate list exceeds " + limits.maxCollectionSize() + " items");
        }
        if (value instanceof Map<?, ?> map && map.size() > limits.maxCollectionSize()) {
            throw new ExpressionEvaluationException(
                    "an intermediate object exceeds " + limits.maxCollectionSize() + " entries");
        }
    }

    // ---- fonctions bornées ---------------------------------------------------------------------------------------

    private void bindBoundedFunctions(Jsonata.Frame bindings) {
        bindings.bind("pad", Jsonata.function("pad", (Jsonata.FnVarArgs<String>) this::pad, "<s-ns?:s>"));
        bindings.bind("replace", Jsonata.function("replace", (Jsonata.FnVarArgs<String>) this::replace, "<s-ssn?:s>"));
        bindings.bind("toMillis", Jsonata.function("toMillis", (Jsonata.FnVarArgs<Long>) this::toMillis, "<s-:n>"));
        bindings.bind("fromMillis", Jsonata.function("fromMillis", (Jsonata.FnVarArgs<String>) this::fromMillis, "<n-:s>"));
        // j : pas de coercition en texte (un nombre ne doit pas devenir un login).
        bindings.bind("ascii", Jsonata.function("ascii", (Jsonata.FnVarArgs<String>) this::ascii, "<j-:s>"));
    }

    /**
     * TODO §134 (D-EXPR-ASCII) — {@code $ascii} : NFD, marques combinatoires retirées, translittération des lettres
     * latines qui ne se décomposent pas ({@code ß ss}, {@code æ ae}, {@code œ oe}, {@code ø o}, {@code ł l}), minuscules,
     * seuls {@code [a-z0-9.-]}. {@code null} reste {@code null}. Un texte sans aucun caractère de login est refusé :
     * un login vide ne part pas. Pas de regex. Le résultat reste borné ({@code maxResultBytes}).
     */
    private String ascii(List<?> args) {
        if (args.isEmpty() || args.get(0) == null || args.get(0) == Jsonata.NULL_VALUE) {
            return null;
        }
        if (!(args.get(0) instanceof String text)) {
            throw new ExpressionEvaluationException("$ascii expects text");
        }
        if (text.length() > limits.maxResultBytes()) {
            throw new ExpressionEvaluationException("$ascii input exceeds " + limits.maxResultBytes() + " characters");
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < decomposed.length(); ) {
            int codePoint = decomposed.codePointAt(i);
            i += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK) {
                continue;
            }
            String mapped = latinLoginLetter(codePoint);
            if (mapped != null) {
                out.append(mapped);
                continue;
            }
            int lower = Character.toLowerCase(codePoint);
            if (lower < 128 && isLoginCharacter(lower)) {
                out.append((char) lower);
            }
        }
        if (out.isEmpty()) {
            throw new ExpressionEvaluationException("$ascii produced no login characters");
        }
        if (out.length() > limits.maxResultBytes()) {
            throw new ExpressionEvaluationException("$ascii result exceeds " + limits.maxResultBytes() + " characters");
        }
        return out.toString();
    }

    /** Lettres latines hors décomposition NFD, vers le jeu {@code [a-z0-9.-]}. */
    private static String latinLoginLetter(int codePoint) {
        return switch (codePoint) {
            case 'ß', 'ẞ' -> "ss";
            case 'æ', 'Æ' -> "ae";
            case 'œ', 'Œ' -> "oe";
            case 'ø', 'Ø' -> "o";
            case 'ł', 'Ł' -> "l";
            default -> null;
        };
    }

    private static boolean isLoginCharacter(int codePoint) {
        return (codePoint >= 'a' && codePoint <= 'z') || (codePoint >= '0' && codePoint <= '9')
                || codePoint == '.' || codePoint == '-';
    }

    /**
     * {@code $pad} linéaire : celui de la bibliothèque concatène {@code padding += padStr} dans une boucle, donc son coût
     * est <b>quadratique</b> en largeur (mesuré : 49 Mo alloués pour une largeur de 10 000, plus d'1 Go pour 50 000).
     * Même sémantique (largeur positive : complète à droite ; négative : à gauche ; motif répété puis coupé).
     */
    private String pad(List<?> args) {
        String text = (String) args.get(0);
        if (text == null) {
            return null;
        }
        Number widthArg = (Number) args.get(1);
        if (Math.abs(widthArg.doubleValue()) > limits.maxStringChars()) {
            throw new ExpressionEvaluationException("$pad width exceeds " + limits.maxStringChars() + " characters");
        }
        int width = widthArg.intValue();
        String padText = args.size() > 2 && args.get(2) instanceof String p && !p.isEmpty() ? p : " ";
        int pads = Math.abs(width) - text.codePointCount(0, text.length());
        if (pads <= 0) {
            return text;
        }
        int padLength = padText.codePointCount(0, padText.length());
        StringBuilder padding = new StringBuilder();
        for (int i = 0; i < (pads + padLength - 1) / padLength; i++) {
            padding.append(padText);
        }
        String cut = padding.substring(0, padding.offsetByCodePoints(0, pads));
        return width < 0 ? cut + text : text + cut;
    }

    /**
     * {@code $replace} à motif <b>littéral</b> (les regex sont refusées) : le {@code $replace} natif appelle
     * {@code String.replaceFirst} dès qu'une limite est donnée, donc interprète le motif en regex (ReDoS non
     * interruptible) et boucle nativement sur une limite démesurée.
     */
    private String replace(List<?> args) {
        if (args.get(0) == null) {
            return null;
        }
        if (!(args.get(0) instanceof String text) || !(args.get(1) instanceof String pattern)
                || !(args.get(2) instanceof String replacement)) {
            throw new ExpressionEvaluationException("$replace supports text patterns and replacements only");
        }
        if (pattern.isEmpty()) {
            throw new ExpressionEvaluationException("$replace pattern must not be empty");
        }
        long remaining = Long.MAX_VALUE;
        if (args.size() > 3 && args.get(3) instanceof Number limit) {
            if (limit.doubleValue() < 0) {
                throw new ExpressionEvaluationException("$replace limit must not be negative");
            }
            remaining = limit.longValue();
        }
        StringBuilder out = new StringBuilder();
        int from = 0;
        while (remaining > 0) {
            int at = text.indexOf(pattern, from);
            if (at < 0) {
                break;
            }
            out.append(text, from, at).append(replacement);
            from = at + pattern.length();
            remaining--;
            if (out.length() > limits.maxResultBytes()) {
                throw new ExpressionEvaluationException(
                        "$replace result exceeds " + limits.maxResultBytes() + " characters");
            }
        }
        out.append(text, from, text.length());
        if (out.length() > limits.maxResultBytes()) {
            throw new ExpressionEvaluationException("$replace result exceeds " + limits.maxResultBytes() + " characters");
        }
        return out.toString();
    }

    private Long toMillis(List<?> args) {
        if (args.get(0) == null) {
            return null;
        }
        try {
            return Functions.dateTimeToMillis((String) args.get(0), null);
        } catch (java.text.ParseException | RuntimeException e) {
            throw new ExpressionEvaluationException("$toMillis expects an ISO 8601 timestamp");
        }
    }

    private String fromMillis(List<?> args) {
        if (args.get(0) == null) {
            return null;
        }
        try {
            return Functions.dateTimeFromMillis((Number) args.get(0), null, null);
        } catch (RuntimeException e) {
            throw new ExpressionEvaluationException("$fromMillis expects a number of milliseconds");
        }
    }

    // ---- divers --------------------------------------------------------------------------------------------------

    private static boolean isJson(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Map
                || value instanceof List;
    }

    private long currentAllocated() {
        return allocations == null ? 0L : allocations.getCurrentThreadAllocatedBytes();
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported()) {
            bean.setThreadAllocatedMemoryEnabled(true);
            return bean;
        }
        // Propre à HotSpot : sans cette JVM la garde d'allocation est absente. Les bornes structurelles (taille, poids déplié,
        // budget de travail) restent actives et bornent la mémoire indépendamment.
        LOG.warn("Thread allocation accounting is unavailable on this JVM: the expression memory guard is disabled "
                + "(structural bounds remain in force)");
        return null;
    }
}
