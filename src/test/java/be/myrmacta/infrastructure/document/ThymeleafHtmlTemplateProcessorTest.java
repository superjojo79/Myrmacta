package be.myrmacta.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.document.model.TemplateProcessingException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ThymeleafHtmlTemplateProcessorTest {

    private final ThymeleafHtmlTemplateProcessor processor = new ThymeleafHtmlTemplateProcessor();

    @Test
    void evaluatesThTextWithVariables() {
        String html = "<html><body><span th:text=\"${title}\">x</span></body></html>";
        String out = processor.process(html, Map.of("title", "Hello"));
        assertThat(out).contains("Hello");
    }

    @Test
    void evaluatesVarsUtility() {
        String html = "<html><body><span th:text=\"${#vars.title}\">x</span></body></html>";
        String out = processor.process(html, Map.of("title", "ViaVars"));
        assertThat(out).contains("ViaVars");
    }

    @Test
    void evaluatesDatesFormat() {
        Date now = Date.from(Instant.parse("2026-06-15T12:00:00Z"));
        String html = "<html><body><span th:text=\"${#dates.format(now, 'yyyy')}\">x</span></body></html>";
        String out = processor.process(html, Map.of("now", now));
        assertThat(out).contains("2026");
    }

    @Test
    void evaluatesStringsToUpperCase() {
        String html = "<html><body><span th:text=\"${#strings.toUpperCase(title)}\">x</span></body></html>";
        String out = processor.process(html, Map.of("title", "hello"));
        assertThat(out).contains("HELLO");
    }

    @Test
    void evaluatesNumbersFormatInteger() {
        String html = "<html><body><span th:text=\"${#numbers.formatInteger(amount, 1)}\">x</span></body></html>";
        String out = processor.process(html, Map.of("amount", 7));
        assertThat(out).contains("7");
    }

    @Test
    void evaluatesListsSize() {
        String html = "<html><body><span th:text=\"${#lists.size(items)}\">x</span></body></html>";
        String out = processor.process(html, Map.of("items", List.of("a", "b", "c")));
        assertThat(out).contains("3");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "${T(java.lang.Runtime).getRuntime()}",
                "${T(java.lang.Math).abs(-42)}",
                "${#beans}",
                "${#request}",
                "${new java.lang.String('x')}",
                "${title.class}",
                "${title.getClass}"
            })
    void rejectedExpressions_throwTemplateProcessingException(String expression) {
        String html = "<html><body><span th:text=\"" + expression + "\">x</span></body></html>";
        assertThatThrownBy(() -> processor.process(html, Map.of("title", "Hello")))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void inlinedIfAndPreprocessedSpel_throwTemplateProcessingException() {
        assertThatThrownBy(() -> processor.process(
                        "<html><body><p>[[${T(java.lang.Runtime).getRuntime()}]]</p></body></html>", Map.of()))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> processor.process(
                        "<html><body><p th:if=\"${T(java.lang.Runtime)}\">x</p></body></html>", Map.of()))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> processor.process(
                        "<html><body><p th:text=\"__${T(java.lang.Runtime)}__\">x</p></body></html>", Map.of()))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void invalidExpression_throwsTemplateProcessingException() {
        String html = "<html><body><span th:text=\"${missing.property.x}\">x</span></body></html>";
        assertThatThrownBy(() -> processor.process(html, Map.of()))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void process_nullVariables_stillRendersStaticMarkup() {
        String out = processor.process("<html><body><p>static</p></body></html>", null);
        assertThat(out).contains("static");
    }

    @Test
    void thEachAndThText_iterateModelList() {
        String html =
                """
                <html><body>
                  <ul>
                    <li th:each="line : ${lines}" th:text="${line}">x</li>
                  </ul>
                </body></html>
                """;
        String out = processor.process(html, Map.of("lines", List.of("alpha", "beta")));
        assertThat(out).contains("alpha").contains("beta");
    }

    /** TODO §76 (D1) : un montant, une référence et les lignes d'un groupe se rendent avec l'évaluateur existant. */
    @Test
    void structuredDossierFields_renderThroughPropertyPathsAndIteration() {
        String html =
                """
                <html><body>
                  <p id="m" th:text="${dossierFields.montant.amount} + ' ' + ${dossierFields.montant.currency}">x</p>
                  <p id="r" th:text="${dossierFields.fournisseur.system} + ':' + ${dossierFields.fournisseur.id}">x</p>
                  <table><tr th:each="l : ${dossierFields.lignes}">
                    <td th:text="${l.poste}">p</td><td th:text="${l.prix.amount}">a</td>
                  </tr></table>
                </body></html>
                """;
        Map<String, Object> fields = Map.of(
                "montant", Map.of("amount", "1234.50", "currency", "EUR"),
                "fournisseur", Map.of("system", "d365fo", "id", "V-0042"),
                "lignes", List.of(
                        Map.of("poste", "Toiture", "prix", Map.of("amount", "1000.00", "currency", "EUR")),
                        Map.of("poste", "Cuisine", "prix", Map.of("amount", "234.50", "currency", "EUR"))));

        String out = processor.process(html, Map.of("dossierFields", fields));

        assertThat(out).contains("1234.50 EUR").contains("d365fo:V-0042").contains("Toiture").contains("1000.00")
                .contains("Cuisine").contains("234.50");
    }

    /** Revue adversariale LC4b, passe 2 (Haut) : défense en profondeur — fragments et th:utext sont sans effet au rendu, même sur un template déjà actif. */
    @org.junit.jupiter.api.Test
    void fragmentsAndUnescapedTextAreNeverExecutedAtRenderTime() {
        var data = Map.<String, Object>of("x", "<i>X</i>");
        for (String html : new String[] {
            "<div th:replace=\"${x}\">a</div>",
            "<div th:insert=\"${x}\">a</div>",
            "<p th:utext=\"${x}\">a</p>"
        }) {
            String out = processor.process(html, data);
            org.assertj.core.api.Assertions.assertThat(out).as(html).doesNotContain("<i>X</i>");
        }
    }
}
