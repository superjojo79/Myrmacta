package com.flowforge.infrastructure.document;

import com.flowforge.domain.document.model.TemplateProcessingException;
import com.flowforge.domain.document.port.out.HtmlTemplateProcessor;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class ThymeleafHtmlTemplateProcessor implements HtmlTemplateProcessor {

    private final TemplateEngine templateEngine;

    public ThymeleafHtmlTemplateProcessor() {
        this.templateEngine = SafeHtmlTemplateEngineFactory.htmlStringEngine();
    }

    @Override
    public String process(String htmlContent, Map<String, Object> variables) {
        Objects.requireNonNull(htmlContent, "htmlContent");
        try {
            Context context = new Context();
            if (variables != null) {
                context.setVariables(variables);
            }
            return templateEngine.process(htmlContent, context);
        } catch (RuntimeException ex) {
            throw new TemplateProcessingException(
                    "Failed to process Thymeleaf template: " + ex.getMessage(), ex);
        }
    }
}
