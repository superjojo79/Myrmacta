package com.flowforge.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.document.model.TemplateProcessingException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThymeleafHtmlTemplateProcessorTest {

    private final ThymeleafHtmlTemplateProcessor processor = new ThymeleafHtmlTemplateProcessor();

    @Test
    void evaluatesThTextWithVariables() {
        String html = "<html><body><span th:text=\"${title}\">x</span></body></html>";
        String out = processor.process(html, Map.of("title", "Hello"));
        assertThat(out).contains("Hello");
    }

    @Test
    void invalidExpression_throwsTemplateProcessingException() {
        String html = "<html><body><span th:text=\"${missing.property.x}\">x</span></body></html>";
        assertThatThrownBy(() -> processor.process(html, Map.of()))
                .isInstanceOf(TemplateProcessingException.class);
    }
}
