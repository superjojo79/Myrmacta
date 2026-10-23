package com.flowforge.infrastructure.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dashjoin.jsonata.AstInspector;
import com.dashjoin.jsonata.JException;
import com.dashjoin.jsonata.Jsonata;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TODO §95 (P9, risque 4 de la revue du 22 octobre) : non-régression de {@link AstInspector} face aux montées de
 * version de JSONata (D-EXPR-AST-BRIDGE). Le pont accède aux champs internes de l'arbre : un champ renommé casse la
 * compilation, mais un nœud enfant rangé dans un <em>nouveau</em> champ échapperait en silence au parcours, et une
 * expression régulière ou une variable cachée à cet endroit ne serait plus vue. Ce corpus place une regex et une
 * variable dans chaque position syntaxique connue ; si l'une n'est plus détectée, la bibliothèque a changé de forme.
 */
class AstInspectorRegressionTest {

    private static final List<String> REGEX_POSITIONS = List.of(
            "$match(x, /a/)",                                    // argument de fonction
            "items[$contains(name, /a/)]",                       // prédicat
            "x ? $match(y, /a/) : 1",                            // branche « alors »
            "x ? 1 : $match(y, /a/)",                            // branche « sinon »
            "$exists($match(y, /a/)) ? 1 : 2",                   // condition
            "{\"k\": $match(x, /a/)}",                           // valeur d'objet
            "[$match(x, /a/)]",                                  // élément de tableau
            "($v := 1; $match(x, /a/))",                         // bloc
            "function($s) { $match($s, /a/) }",                  // corps de lambda
            "items^($count($match(name, /a/)))",                 // terme de tri
            "items{name: $count($match(name, /a/))}",            // regroupement
            "x ~> $match(/a/)",                                  // chaîne d'application
            "$ ~> | items | {\"k\": $match(name, /a/)} |",       // transformation (mise à jour)
            "$match(?, /a/)",                                    // application partielle
            "-$count($match(x, /a/))",                           // moins unaire
            "$count($match(x, /a/)) + 1",                        // opérande binaire
            "a.b.($match(c, /a/))",                              // étape de chemin
            "$v := $match(x, /a/)");                             // affectation

    private static final List<String> VARIABLE_POSITIONS = List.of(
            "$v",
            "$f($v)",
            "items[name = $v]",
            "x ? $v : 1",
            "{\"k\": $v}",
            "[$v]",
            "($w := 1; $v)",
            "function($s) { $s & $v }",
            "items^($v)",
            "items{name: $v}",
            "x ~> $f($v)",
            "$ ~> | items | {\"k\": $v} |",
            "a.b[$v].c");

    @Test
    void regexIsDetectedInEverySyntacticPosition() {
        for (String expression : REGEX_POSITIONS) {
            assertThat(AstInspector.inspect(expression).usesRegex()).as(expression).isTrue();
        }
    }

    @Test
    void variableIsDetectedInEverySyntacticPosition() {
        for (String expression : VARIABLE_POSITIONS) {
            assertThat(AstInspector.inspect(expression).variables()).as(expression).contains("v");
        }
    }

    @Test
    void focusAndIndexBindingsAreReportedAsVariables() {
        assertThat(AstInspector.inspect("items@$it.name").variables()).contains("it");
        assertThat(AstInspector.inspect("items#$i[$i > 0]").variables()).contains("i");
    }

    @Test
    void sizeAndDepthGrowWithTheExpression() {
        AstInspector.Report small = AstInspector.inspect("1 + 1");
        AstInspector.Report large = AstInspector.inspect("1" + " + 1".repeat(50));

        assertThat(small.nodes()).isPositive();
        assertThat(large.nodes()).isGreaterThan(small.nodes());
        assertThat(large.depth()).isGreaterThan(small.depth());
        assertThat(AstInspector.inspect("\"no regex\"").usesRegex()).isFalse();
    }

    @Test
    void invalidSyntaxIsReportedAsAJsonataError() {
        assertThatThrownBy(() -> AstInspector.inspect("$match(")).isInstanceOf(JException.class);
    }

    @Test
    void builtinFunctionNamesExposeTheLibraryCatalogue() {
        assertThat(AstInspector.builtinFunctionNames()).contains("sum", "eval", "match", "replace", "now", "random");
    }

    @Test
    void lazyRangesAreRecognisedWithoutBeingTraversed() throws Exception {
        Object range = Jsonata.jsonata("[1..1000000]").evaluate(null); // plafond de la bibliothèque : 1e6

        assertThat(AstInspector.isLazyRange(range)).isTrue();
        assertThat(AstInspector.isLazyRange(List.of(1, 2))).isFalse();
    }

    @Test
    void neutralMessageNeverCopiesTheValues() {
        JException error = null;
        try {
            // T0412 : le message brut recopie l'argument (« Argument "secret-value-42" of … must be an array »).
            Jsonata.jsonata("$sum(\"secret-value-42\")").evaluate(null);
        } catch (JException e) {
            error = e;
        }

        assertThat(error).as("$sum sur une chaîne doit échouer").isNotNull();
        assertThat(error.getMessage()).contains("secret-value-42");
        assertThat(AstInspector.neutralMessage(error)).doesNotContain("secret-value-42").contains(error.getError());
    }
}
