package com.flowforge.infrastructure.document;

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
}
