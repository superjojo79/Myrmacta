package com.flowforge.infrastructure.document;

import com.flowforge.domain.document.model.InvalidTemplateSyntaxException;
import com.flowforge.domain.document.port.out.TemplateSyntaxChecker;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.cache.AlwaysValidCacheEntryValidity;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresource.StringTemplateResource;

@Component
public class ThymeleafTemplateSyntaxChecker implements TemplateSyntaxChecker {

    /**
     * Revue adversariale LC4b : la recherche de sous-chaînes littérales (« th:utext », « [(${ ») se contournait par la casse
     * (TH:UTEXT), la forme HTML5 (data-th-utext) ou un blanc dans l'inline ([( ${a} )], saut de ligne). Toute interpolation
     * non échappée est refusée, quelle que soit sa graphie : attribut {@code utext}/{@code inline} et séquence {@code [(}.
     */
    private static final Pattern UNESCAPED_ATTRIBUTE =
            Pattern.compile("(?:data-)?th[:-](?:utext|inline)", Pattern.CASE_INSENSITIVE);

    private static final Pattern UNESCAPED_INLINE = Pattern.compile("\\[\\(");

    private final TemplateEngine templateEngine;

    public ThymeleafTemplateSyntaxChecker() {
        this.templateEngine = SafeHtmlTemplateEngineFactory.htmlStringEngine();
    }

    @Override
    public void check(String htmlContent) {
        if (htmlContent != null
                && (UNESCAPED_ATTRIBUTE.matcher(htmlContent).find() || UNESCAPED_INLINE.matcher(htmlContent).find())) {
            throw new InvalidTemplateSyntaxException(
                    "Unescaped Thymeleaf interpolation (th:utext, th:inline, [(...)]) is not allowed");
        }
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
