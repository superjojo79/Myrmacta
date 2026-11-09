package be.myrmacta.infrastructure.document;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Moteurs Thymeleaf « string » (HTML, et TEXT pour les gabarits de notification, registre §123) sur un
 * {@link StandardDialect} filtré, sans SpEL (DD-THYME-B2 = E). Public pour que les gabarits de notification réutilisent
 * la même liste blanche ; les constantes de classement restent propres au paquet.
 */
public final class SafeHtmlTemplateEngineFactory {

    /**
     * TODO §95 (P9) : liste blanche des processeurs du dialecte standard (Thymeleaf 3.1.5), relue un par un. Un
     * processeur ajouté par une montée de version n'est pas chargé tant qu'il n'est pas classé ici ou dans
     * {@link #DISABLED_PROCESSORS} ; {@code SafeHtmlTemplateEngineFactoryTest} fait alors échouer le build.
     */
    static final Set<String> ALLOWED_PROCESSORS = Set.of(
            "StandardActionTagProcessor", "StandardAltTitleTagProcessor",
            "StandardAssertTagProcessor", "StandardAttrTagProcessor",
            "StandardAttrappendTagProcessor", "StandardAttrprependTagProcessor",
            "StandardBlockTagProcessor", "StandardCaseTagProcessor",
            "StandardClassappendTagProcessor", "StandardConditionalCommentProcessor",
            "StandardConditionalFixedValueTagProcessor", "StandardDOMEventAttributeTagProcessor",
            "StandardDefaultAttributesTagProcessor", "StandardEachTagProcessor",
            "StandardFragmentTagProcessor", "StandardHrefTagProcessor",
            "StandardIfTagProcessor", "StandardInlineEnablementTemplateBoundariesProcessor",
            "StandardInlineHTMLTagProcessor", "StandardInlineTextualTagProcessor",
            "StandardInlineXMLTagProcessor", "StandardInliningCDATASectionProcessor",
            "StandardInliningCommentProcessor", "StandardInliningTextProcessor",
            "StandardLangXmlLangTagProcessor", "StandardMethodTagProcessor",
            "StandardNonRemovableAttributeTagProcessor", "StandardObjectTagProcessor",
            "StandardRefAttributeTagProcessor", "StandardRemovableAttributeTagProcessor",
            "StandardRemoveTagProcessor", "StandardSrcTagProcessor",
            "StandardStyleappendTagProcessor", "StandardSwitchTagProcessor",
            "StandardTextTagProcessor", "StandardTranslationDocTypeProcessor",
            "StandardUnlessTagProcessor", "StandardValueTagProcessor",
            "StandardWithTagProcessor", "StandardXmlBaseTagProcessor",
            "StandardXmlLangTagProcessor", "StandardXmlNsTagProcessor",
            "StandardXmlSpaceTagProcessor");

    /**
     * Revue adversariale LC4b (Haut) : {@code th:replace}/{@code th:insert}/{@code th:include} traitent le nom de fragment
     * comme un <em>template</em> (StringTemplateResolver) — une concaténation de chaînes reconstruit {@code [(}, {@code th:utext}
     * après le contrôle d'activation, et {@code th:replace="${x}"} exécute une donnée comme template. Ces processeurs, et
     * {@code th:utext}, ne sont donc pas chargés : un template qui les contiendrait les laisse sans effet.
     */
    static final Set<String> DISABLED_PROCESSORS = Set.of(
            "StandardInsertTagProcessor",
            "StandardReplaceTagProcessor",
            "StandardIncludeTagProcessor",
            "StandardUtextTagProcessor");

    private SafeHtmlTemplateEngineFactory() {}

    public static TemplateEngine htmlStringEngine() {
        return stringEngine(TemplateMode.HTML);
    }

    /** Même dialecte filtré et même évaluateur, en mode TEXT : {@code [[${x}]]} n'échappe rien (texte brut). */
    public static TemplateEngine textStringEngine() {
        return stringEngine(TemplateMode.TEXT);
    }

    /** Moteur HTML dont l'évaluateur borne le coût (gabarits de notification). */
    public static TemplateEngine htmlStringEngine(ExpressionLimits limits) {
        return stringEngine(TemplateMode.HTML, new SafeThymeleafVariableExpressionEvaluator(limits));
    }

    /** Moteur TEXT dont l'évaluateur borne le coût (gabarits de notification). */
    public static TemplateEngine textStringEngine(ExpressionLimits limits) {
        return stringEngine(TemplateMode.TEXT, new SafeThymeleafVariableExpressionEvaluator(limits));
    }

    private static TemplateEngine stringEngine(TemplateMode mode) {
        return stringEngine(mode, SafeThymeleafVariableExpressionEvaluator.INSTANCE);
    }

    private static TemplateEngine stringEngine(TemplateMode mode, SafeThymeleafVariableExpressionEvaluator evaluator) {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(mode);
        resolver.setCacheable(false);
        StandardDialect dialect = new StandardDialect() {
            @Override
            public Set<IProcessor> getProcessors(String dialectPrefix) {
                return super.getProcessors(dialectPrefix).stream()
                        .filter(p -> ALLOWED_PROCESSORS.contains(p.getClass().getSimpleName()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
        };
        dialect.setVariableExpressionEvaluator(evaluator);
        // Pas un StandardExpressionParser : Thymeleaf n'applique plus le pré-traitement __…__ (issue #43).
        dialect.setExpressionParser(new GuardedExpressionParser());
        TemplateEngine engine = new TemplateEngine();
        engine.setDialect(dialect);
        if (evaluator.limited()) {
            engine.addDialect(new RenderDeadlineDialect());
        }
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
