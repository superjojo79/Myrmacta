package com.flowforge.infrastructure.document;

import com.flowforge.domain.document.model.InvalidTemplateSyntaxException;
import com.flowforge.domain.document.port.out.TemplateSyntaxChecker;
import org.springframework.stereotype.Component;
import org.thymeleaf.cache.AlwaysValidCacheEntryValidity;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresource.StringTemplateResource;
import org.thymeleaf.templateresolver.StringTemplateResolver;

@Component
public class ThymeleafTemplateSyntaxChecker implements TemplateSyntaxChecker {

    private final SpringTemplateEngine templateEngine;

    public ThymeleafTemplateSyntaxChecker() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        this.templateEngine = engine;
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
        } catch (RuntimeException ex) {
            throw new InvalidTemplateSyntaxException(
                    "Invalid Thymeleaf template syntax: " + ex.getMessage(), ex);
        }
    }
}
