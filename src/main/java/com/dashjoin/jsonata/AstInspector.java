package com.dashjoin.jsonata;

import com.dashjoin.jsonata.Parser.Symbol;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * TODO §75 (LC2) — inspection de l'arbre syntaxique JSONata <b>sans l'évaluer</b>.
 *
 * <p>Cette classe est volontairement placée dans le package {@code com.dashjoin.jsonata} : l'arbre
 * ({@code Jsonata.ast}, {@code Parser.Symbol}) n'expose aucun accès public, et la réflexion casserait silencieusement
 * à la première montée de version. Ici, l'accès est vérifié à la compilation : si la bibliothèque renomme un champ, le
 * build échoue au lieu de laisser passer une expression non inspectée. Les tests du moteur figent le comportement
 * attendu (regex, fonctions interdites, bornes). Elle ne doit être appelée que par
 * {@code com.flowforge.infrastructure.expression}.
 */
public final class AstInspector {

    /** @param variables noms des variables référencées ({@code $eval} donne {@code eval}) */
    public record Report(int nodes, int depth, boolean usesRegex, Set<String> variables) {}

    private AstInspector() {}

    /** Noms de toutes les fonctions natives de la bibliothèque ({@code $sum}, {@code $eval}…), pour une liste blanche fermée. */
    public static Set<String> builtinFunctionNames() {
        return Set.copyOf(Jsonata.staticFrame.bindings.keySet());
    }

    /**
     * Message d'erreur d'exécution <b>sans les valeurs</b> : le gabarit statique de la bibliothèque dont les
     * emplacements {@code {{valeur}}} sont remplacés par « … ». Les messages bruts de la bibliothèque recopient les
     * arguments (jusqu'au document racine entier) ; ils ne doivent jamais atteindre un client, un journal ou un job.
     */
    public static String neutralMessage(JException e) {
        String template = e.getError() == null ? null : Jsonata.errorCodes.get(e.getError());
        String code = e.getError() == null ? "unknown" : e.getError();
        if (template == null || template.isBlank()) {
            return "expression error " + code;
        }
        String neutral = template.replaceAll("\\{\\{\\{?[^}]*}}}?", "…");
        return neutral + " (" + code + (e.getLocation() >= 0 ? ", position " + e.getLocation() : "") + ")";
    }

    /** @throws JException si l'expression est syntaxiquement invalide */
    public static Report inspect(String expression) {
        Jsonata parsed = Jsonata.jsonata(expression);
        Set<Symbol> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> variables = new LinkedHashSet<>();
        boolean regex = false;
        int maxDepth = 0;
        Deque<Symbol> stack = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        stack.push(parsed.ast);
        depths.push(1);
        while (!stack.isEmpty()) {
            Symbol node = stack.pop();
            int depth = depths.pop();
            if (node == null || !seen.add(node)) {
                continue;
            }
            maxDepth = Math.max(maxDepth, depth);
            if ("regex".equals(node.type)) {
                regex = true;
            }
            if ("variable".equals(node.type) && node.value != null) {
                variables.add(node.value.toString());
            }
            // Liaisons de focus (`x@$v`) et d'index (`x#$i`) : des noms de variable qui ne sont pas des nœuds.
            if (node.focus instanceof String name) {
                variables.add(stripDollar(name));
            }
            if (node.index instanceof String name) {
                variables.add(stripDollar(name));
            }
            for (Symbol child : children(node)) {
                stack.push(child);
                depths.push(depth + 1);
            }
        }
        return new Report(seen.size(), maxDepth, regex, Set.copyOf(variables));
    }

    private static String stripDollar(String name) {
        return name.startsWith("$") ? name.substring(1) : name;
    }

    private static List<Symbol> children(Symbol n) {
        java.util.ArrayList<Symbol> out = new java.util.ArrayList<>();
        add(out, n.lhs);
        add(out, n.rhs);
        add(out, n.expression);
        add(out, n.procedure);
        add(out, n.body);
        add(out, n.condition);
        add(out, n.then);
        add(out, n._else);
        add(out, n.pattern);
        add(out, n.update);
        add(out, n.delete);
        add(out, n.group);
        add(out, n.slot);
        add(out, n.nextFunction);
        if (n.expr instanceof Symbol s) {
            out.add(s);
        }
        for (List<Symbol> list : java.util.Arrays.asList(
                n.arguments, n.steps, n.stages, n.predicate, n.expressions, n.terms, n.rhsTerms)) {
            if (list != null) {
                list.forEach(s -> add(out, s));
            }
        }
        for (List<Symbol[]> pairs : java.util.Arrays.asList(n.lhsObject, n.rhsObject)) {
            if (pairs != null) {
                for (Symbol[] pair : pairs) {
                    for (Symbol s : pair) {
                        add(out, s);
                    }
                }
            }
        }
        return out;
    }

    private static void add(List<Symbol> out, Symbol s) {
        if (s != null) {
            out.add(s);
        }
    }
}
