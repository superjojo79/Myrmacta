package be.myrmacta.infrastructure.document;

import be.myrmacta.domain.document.model.InvalidTemplateSyntaxException;
import be.myrmacta.domain.document.port.out.TemplateSyntaxChecker;
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

    /** Fragments : le nom est évalué comme un template (voir {@link SafeHtmlTemplateEngineFactory}). */
    private static final Pattern FRAGMENT_ATTRIBUTE =
            Pattern.compile("(?:data-)?th[:-](?:replace|insert|include|substituteby)", Pattern.CASE_INSENSITIVE);

    private static final Pattern UNESCAPED_INLINE = Pattern.compile("\\[\\(");

    private final TemplateEngine templateEngine;

    public ThymeleafTemplateSyntaxChecker() {
        this.templateEngine = SafeHtmlTemplateEngineFactory.htmlStringEngine();
    }

    @Override
    public void check(String htmlContent) {
        check(htmlContent, TemplateMode.HTML);
    }

    /**
     * Même contrôle pour un autre mode (TEXT : objet et corps texte des gabarits de notification, registre §123) :
     * mêmes interpolations non échappées refusées, mêmes fragments refusés, même grammaire d'expression (D-40).
     */
    public void check(String htmlContent, TemplateMode mode) {
        if (htmlContent != null
                && (UNESCAPED_ATTRIBUTE.matcher(htmlContent).find() || UNESCAPED_INLINE.matcher(htmlContent).find())) {
            throw new InvalidTemplateSyntaxException(
                    "Unescaped Thymeleaf interpolation (th:utext, th:inline, [(...)]) is not allowed");
        }
        if (htmlContent != null && FRAGMENT_ATTRIBUTE.matcher(htmlContent).find()) {
            throw new InvalidTemplateSyntaxException(
                    "Thymeleaf fragments (th:replace, th:insert, th:include) are not allowed in a document template");
        }
        try {
            TemplateData templateData = new TemplateData(
                    "document-template-syntax-check",
                    null,
                    new StringTemplateResource(htmlContent),
                    mode,
                    AlwaysValidCacheEntryValidity.INSTANCE);
            templateEngine
                    .getConfiguration()
                    .getTemplateManager()
                    .parseString(templateData, htmlContent, 0, 0, mode, false);
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
