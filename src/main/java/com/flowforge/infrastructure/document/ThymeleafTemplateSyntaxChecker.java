package com.flowforge.infrastructure.document;

import com.flowforge.domain.document.model.InvalidTemplateSyntaxException;
import com.flowforge.domain.document.port.out.TemplateSyntaxChecker;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.cache.AlwaysValidCacheEntryValidity;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresource.StringTemplateResource;

@Component
public class ThymeleafTemplateSyntaxChecker implements TemplateSyntaxChecker {

    private final TemplateEngine templateEngine;

    public ThymeleafTemplateSyntaxChecker() {
        this.templateEngine = SafeHtmlTemplateEngineFactory.htmlStringEngine();
    }

    @Override
    public void check(String htmlContent) {
        try {
            TemplateData templateData = new TemplateData(
                    "document-template-syntax-check",
                    null,
                    new StringTemplateResource(htmlContent),
                    TemplateMode.HTML,
                    AlwaysValidCacheEntryValidity.INSTANCE);
            templateEngine
                    .getConfiguration()
                    .getTemplateManager()
                    .parseString(templateData, htmlContent, 0, 0, TemplateMode.HTML, false);
            for (String expression : SafeThymeleafExpressionSupport.extractVariableExpressions(htmlContent)) {
                SafeThymeleafExpressionSupport.assertAllowed(expression);
            }
        } catch (InvalidTemplateSyntaxException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new InvalidTemplateSyntaxException(
                    "Invalid Thymeleaf template syntax: " + ex.getMessage(), ex);
        }
    }
}
