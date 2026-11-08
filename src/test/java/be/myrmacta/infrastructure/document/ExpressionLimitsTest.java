package be.myrmacta.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class ExpressionLimitsTest {

    private static ExpressionLimits limits(long budget) {
        return new ExpressionLimits(10, 10, budget, 60_000L);
    }

    @Test
    void charge_outsideARender_costsNothing() {
        assertThatCode(() -> ExpressionLimits.charge(Long.MAX_VALUE / 2)).doesNotThrowAnyException();
    }

    @Test
    void charge_spendsTheBudgetExactly() {
        try (ExpressionLimits.Scope ignored = limits(10).begin()) {
            assertThatCode(() -> ExpressionLimits.charge(4)).doesNotThrowAnyException();
            assertThatCode(() -> ExpressionLimits.charge(6)).doesNotThrowAnyException();
            assertThatThrownBy(() -> ExpressionLimits.charge(1))
                    .isInstanceOf(TemplateProcessingException.class)
                    .hasMessage("Template cost limit exceeded (evaluation budget exhausted)");
        }
    }

    @Test
    void charge_aSingleOverspend_isRefused() {
        try (ExpressionLimits.Scope ignored = limits(10).begin()) {
            assertThatThrownBy(() -> ExpressionLimits.charge(11))
                    .isInstanceOf(TemplateProcessingException.class)
                    .hasMessageContaining("evaluation budget exhausted");
        }
    }

    @Test
    void charge_afterTheDeadline_isRefused() throws InterruptedException {
        ExpressionLimits instant = new ExpressionLimits(10, 10, 1_000_000L, 1L);
        try (ExpressionLimits.Scope ignored = instant.begin()) {
            Thread.sleep(20);
            assertThatThrownBy(() -> ExpressionLimits.charge(1))
                    .isInstanceOf(TemplateProcessingException.class)
                    .hasMessage("Template cost limit exceeded (render time limit exceeded)");
        }
    }

    @Test
    void closingTheScope_endsTheBudget() {
        try (ExpressionLimits.Scope ignored = limits(1).begin()) {
            assertThatThrownBy(() -> ExpressionLimits.charge(2)).isInstanceOf(TemplateProcessingException.class);
        }

        assertThatCode(() -> ExpressionLimits.charge(1_000)).doesNotThrowAnyException();
    }

    @Test
    void closingANestedScope_restoresTheBudgetOfTheOuterOne() {
        try (ExpressionLimits.Scope outer = limits(10).begin()) {
            ExpressionLimits.charge(6);
            try (ExpressionLimits.Scope inner = limits(1_000).begin()) {
                assertThatCode(() -> ExpressionLimits.charge(500)).doesNotThrowAnyException();
            }
            assertThatCode(() -> ExpressionLimits.charge(4)).doesNotThrowAnyException();
            assertThatThrownBy(() -> ExpressionLimits.charge(1)).isInstanceOf(TemplateProcessingException.class);
        }

        assertThatCode(() -> ExpressionLimits.charge(1_000)).doesNotThrowAnyException();
    }

    @Test
    void everyBeginGivesAFreshBudget() {
        ExpressionLimits limits = limits(5);
        try (ExpressionLimits.Scope ignored = limits.begin()) {
            ExpressionLimits.charge(5);
        }
        try (ExpressionLimits.Scope ignored = limits.begin()) {
            assertThatCode(() -> ExpressionLimits.charge(5)).doesNotThrowAnyException();
        }
    }

    @Test
    void theStringEnginesDoNotCacheTheirTemplates() {
        for (TemplateEngine engine : new TemplateEngine[] {
            SafeHtmlTemplateEngineFactory.htmlStringEngine(),
            SafeHtmlTemplateEngineFactory.textStringEngine(),
            SafeHtmlTemplateEngineFactory.htmlStringEngine(limits(5)),
            SafeHtmlTemplateEngineFactory.textStringEngine(limits(5))
        }) {
            assertThat(engine.getTemplateResolvers())
                    .singleElement()
                    .isInstanceOfSatisfying(StringTemplateResolver.class, resolver -> assertThat(resolver.isCacheable()).isFalse());
        }
    }
}
