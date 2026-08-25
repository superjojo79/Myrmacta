package com.flowforge.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;

class SafeThymeleafExpressionSupportTest {

    @Test
    void evaluatesLiteralsAndArithmeticTokens() {
        Context context = context();
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "true")).isEqualTo(Boolean.TRUE);
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "false")).isEqualTo(Boolean.FALSE);
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "null")).isNull();
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "42")).isEqualTo(42);
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "-7")).isEqualTo(-7);
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "1.5")).isEqualTo(1.5d);
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "'hello'")).isEqualTo("hello");
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "'it''s'")).isEqualTo("it's");
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "'a\\'b'")).isEqualTo("a'b");
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "\"double\"")).isEqualTo("double");
    }

    @Test
    void evaluatesMapAndBeanProperties() {
        Context context = context();
        context.setVariable("dossier", Map.of("reference", "DOS-1"));
        context.setVariable("flag", new FlagBean(true));
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "dossier.reference")).isEqualTo("DOS-1");
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "flag.active")).isEqualTo(true);
    }

    @Test
    void evaluatesNestedUtilityAndVars() {
        Context context = context();
        context.setVariable("title", "ab");
        context.setVariable("now", Date.from(Instant.parse("2026-06-15T12:00:00Z")));
        context.setVariable("amount", 9);
        context.setVariable("items", List.of("a"));
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "#vars.title")).isEqualTo("ab");
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "#strings.concat('x', title)")).isEqualTo("xab");
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "#dates.format(#dates.createNow(), 'yyyy')")
                        .toString())
                .isNotBlank();
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "#numbers.formatInteger(amount, 2)")
                        .toString())
                .contains("9");
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "#lists.isEmpty(items)")).isEqualTo(false);
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "#dates.format(now, 'yyyy')")).isEqualTo("2026");
        Instant instant = Instant.parse("2026-06-15T12:00:00Z");
        context.setVariable("instant", instant);
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "#dates.format(instant, 'yyyy')")).isEqualTo("2026");
    }

    @Test
    void assertAllowed_acceptsB2AndRejectsTheRest() {
        SafeThymeleafExpressionSupport.assertAllowed("title");
        SafeThymeleafExpressionSupport.assertAllowed("#dates.format(now, 'dd/MM/yyyy')");
        SafeThymeleafExpressionSupport.assertAllowed("#vars.dossier.reference");
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("T(java.lang.Runtime)"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("#beans"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("new java.lang.String()"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("#dates.notAMethod()"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("#strings.toUpperCase(title).x"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("#vars"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("#vars.title()"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("title.foo()"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed(""))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("title leftover"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context(), "@oops"))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void extract_handlesDollarStarAndUnclosed() {
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions(
                        "<p th:text=\"${title}\">x</p><span th:text=\"*{ref}\">y</span>"))
                .containsExactly("title", "ref");
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions("")).isEmpty();
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.extractVariableExpressions("${unclosed"))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void evaluate_nullExpression_isRejected() {
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context(), null))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void evaluator_rejectsNullAndBlankRawExpression() {
        var evaluator = SafeThymeleafVariableExpressionEvaluator.INSTANCE;
        assertThatThrownBy(() -> evaluator.evaluate(null, expression(null), null))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> evaluator.evaluate(null, expression("  "), null))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void evaluate_unknownVariableOrProperty_throws() {
        Context context = context();
        context.setVariable("dossier", Map.of("ok", "v"));
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context, "missing"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context, "dossier.absent"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context, "#vars.missing"))
                .isInstanceOf(TemplateProcessingException.class);
    }

    private static Context context() {
        return new Context();
    }

    private static org.thymeleaf.standard.expression.IStandardVariableExpression expression(String raw) {
        return new org.thymeleaf.standard.expression.IStandardVariableExpression() {
            @Override
            public String getExpression() {
                return raw;
            }

            @Override
            public boolean getUseSelectionAsRoot() {
                return false;
            }
        };
    }

    public static final class FlagBean {
        private final boolean active;

        public FlagBean(boolean active) {
            this.active = active;
        }

        public boolean isActive() {
            return active;
        }
    }
}
