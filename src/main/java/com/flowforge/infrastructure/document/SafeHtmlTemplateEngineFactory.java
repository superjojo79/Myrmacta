package com.flowforge.infrastructure.document;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Moteur HTML string + {@link StandardDialect} sans SpEL (DD-THYME-B2 = E).
 */
final class SafeHtmlTemplateEngineFactory {

    private SafeHtmlTemplateEngineFactory() {}

    static TemplateEngine htmlStringEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        StandardDialect dialect = new StandardDialect();
        dialect.setVariableExpressionEvaluator(SafeThymeleafVariableExpressionEvaluator.INSTANCE);
        TemplateEngine engine = new TemplateEngine();
        engine.setDialect(dialect);
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
