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
 * Moteur HTML string + {@link StandardDialect} sans SpEL (DD-THYME-B2 = E).
 */
final class SafeHtmlTemplateEngineFactory {

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

    static TemplateEngine htmlStringEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        StandardDialect dialect = new StandardDialect() {
            @Override
            public Set<IProcessor> getProcessors(String dialectPrefix) {
                return super.getProcessors(dialectPrefix).stream()
                        .filter(p -> ALLOWED_PROCESSORS.contains(p.getClass().getSimpleName()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
        };
        dialect.setVariableExpressionEvaluator(SafeThymeleafVariableExpressionEvaluator.INSTANCE);
        TemplateEngine engine = new TemplateEngine();
        engine.setDialect(dialect);
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
