package be.myrmacta.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.standard.StandardDialect;

/**
 * TODO §95 (P9, risque 4 de la revue du 22 octobre) : la liste des processeurs Thymeleaf chargés est une liste
 * blanche. Une montée de version qui ajoute un processeur au dialecte standard fait échouer ce test : il faut
 * alors le relire et le classer (autorisé ou désactivé) au lieu de le laisser entrer en silence.
 */
class SafeHtmlTemplateEngineFactoryTest {

    private static Set<String> names(Set<? extends Object> processors) {
        return processors.stream().map(p -> p.getClass().getSimpleName()).collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void everyStandardDialectProcessor_isExplicitlyClassified() {
        Set<String> upstream = names(new StandardDialect().getProcessors("th"));
        Set<String> unclassified = new TreeSet<>(upstream);
        unclassified.removeAll(SafeHtmlTemplateEngineFactory.ALLOWED_PROCESSORS);
        unclassified.removeAll(SafeHtmlTemplateEngineFactory.DISABLED_PROCESSORS);

        assertThat(unclassified)
                .as("processeurs Thymeleaf non classés : les relire, puis les ajouter à ALLOWED_PROCESSORS ou "
                        + "DISABLED_PROCESSORS de SafeHtmlTemplateEngineFactory")
                .isEmpty();
    }

    @Test
    void allowedAndDisabledLists_areDisjoint() {
        Set<String> both = new TreeSet<>(SafeHtmlTemplateEngineFactory.ALLOWED_PROCESSORS);
        both.retainAll(SafeHtmlTemplateEngineFactory.DISABLED_PROCESSORS);

        assertThat(both).isEmpty();
    }

    @Test
    void engine_loadsExactlyTheAllowedProcessors() {
        TemplateEngine engine = SafeHtmlTemplateEngineFactory.htmlStringEngine();
        IDialect dialect = engine.getDialects().iterator().next();

        Set<String> loaded = names(((StandardDialect) dialect).getProcessors("th"));

        assertThat(loaded).containsExactlyInAnyOrderElementsOf(SafeHtmlTemplateEngineFactory.ALLOWED_PROCESSORS);
        assertThat(loaded).doesNotContainAnyElementsOf(SafeHtmlTemplateEngineFactory.DISABLED_PROCESSORS);
    }

    /**
     * Gabarits de notification (TODO §123) : le moteur TEXT charge, dans ce Thymeleaf, exactement ces processeurs de
     * mode TEXT (relevés dans {@code StandardDialect.getProcessors}). Une montée de version qui en ajoute un fait échouer
     * ce test : le relire avant de le classer.
     */
    private static final Set<String> TEXT_MODE_PROCESSORS = new TreeSet<>(Set.of(
            "StandardAssertTagProcessor", "StandardBlockTagProcessor", "StandardCaseTagProcessor",
            "StandardEachTagProcessor", "StandardIfTagProcessor", "StandardInlineEnablementTemplateBoundariesProcessor",
            "StandardInlineTextualTagProcessor", "StandardInliningTextProcessor", "StandardInsertTagProcessor",
            "StandardObjectTagProcessor", "StandardRemoveTagProcessor", "StandardReplaceTagProcessor",
            "StandardSwitchTagProcessor", "StandardTextTagProcessor", "StandardUnlessTagProcessor",
            "StandardUtextTagProcessor", "StandardWithTagProcessor"));

    private static Set<String> textModeNames(Set<org.thymeleaf.processor.IProcessor> processors) {
        return processors.stream()
                .filter(p -> p.getTemplateMode() == org.thymeleaf.templatemode.TemplateMode.TEXT)
                .map(p -> p.getClass().getSimpleName())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void textModeProcessors_ofTheUpstreamDialect_areTheReviewedSet() {
        assertThat(textModeNames(new StandardDialect().getProcessors("th")))
                .containsExactlyInAnyOrderElementsOf(TEXT_MODE_PROCESSORS);
    }

    @Test
    void textEngine_loadsOnlyAllowedTextProcessors() {
        TemplateEngine engine = SafeHtmlTemplateEngineFactory.textStringEngine();
        StandardDialect dialect = (StandardDialect) engine.getDialects().iterator().next();

        assertThat(textModeNames(dialect.getProcessors("th")))
                .containsExactlyInAnyOrder(
                        "StandardAssertTagProcessor", "StandardBlockTagProcessor", "StandardCaseTagProcessor",
                        "StandardEachTagProcessor", "StandardIfTagProcessor",
                        "StandardInlineEnablementTemplateBoundariesProcessor", "StandardInlineTextualTagProcessor",
                        "StandardInliningTextProcessor", "StandardObjectTagProcessor", "StandardRemoveTagProcessor",
                        "StandardSwitchTagProcessor", "StandardTextTagProcessor", "StandardUnlessTagProcessor",
                        "StandardWithTagProcessor");
    }

    /**
     * Vérifié (registre §123, point « à vérifier » n° 2) : contrairement à l'hypothèse de conception, {@code [[${x}]]}
     * échappe en mode TEXT, en entités HTML (&amp;, &lt;, &gt;, &quot;, &#39;). {@code NotificationTemplateRenderer}
     * défait cet échappement sur l'objet et le corps texte ; ce test fait échouer le build si Thymeleaf change.
     */
    @Test
    void textEngine_rendersInlineAndConditionals_escapingValuesAsHtmlEntities() {
        TemplateEngine engine = SafeHtmlTemplateEngineFactory.textStringEngine();
        org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
        context.setVariable("a", java.util.Map.of("name", "A & <B> l'\"x\""));

        String out = engine.process("Bonjour [[${a.name}]]\n[# th:if=\"${a.name}\"]oui[/]", context);

        assertThat(out).isEqualTo("Bonjour A &amp; &lt;B&gt; l&#39;&quot;x&quot;\noui");
    }

    @Test
    void textEngine_doesNotExecuteUtextReplaceOrInsert() {
        TemplateEngine engine = SafeHtmlTemplateEngineFactory.textStringEngine();
        org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
        context.setVariable("secret", "SECRET-VALUE");

        String out = engine.process(
                "[# th:utext=\"${secret}\"]x[/][# th:replace=\"${secret}\"]y[/][# th:insert=\"${secret}\"]z[/]", context);

        assertThat(out).doesNotContain("SECRET-VALUE");
    }
}
