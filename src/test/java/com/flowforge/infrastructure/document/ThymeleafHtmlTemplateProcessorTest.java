package com.flowforge.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.document.model.TemplateProcessingException;
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
    void invalidExpression_throwsTemplateProcessingException() {
        String html = "<html><body><span th:text=\"${missing.property.x}\">x</span></body></html>";
        assertThatThrownBy(() -> processor.process(html, Map.of()))
                .isInstanceOf(TemplateProcessingException.class);
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
}
