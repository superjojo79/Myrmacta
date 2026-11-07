package be.myrmacta.infrastructure.document;

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

    @Test
    void evaluate_nestedVarsAndMapNullAndBeanGetter() {
        Context context = context();
        context.setVariable("dossier", Map.of("reference", "DOS-9"));
        context.setVariable("named", new NamedBean("Ada"));
        java.util.HashMap<String, Object> withNull = new java.util.HashMap<>();
        withNull.put("empty", null);
        context.setVariable("hole", withNull);
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "#vars.dossier.reference"))
                .isEqualTo("DOS-9");
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "named.label")).isEqualTo("Ada");
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context, "hole.empty"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context, "named.missing"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context, "#vars.dossier.reference()"))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void evaluate_recordCanonicalAccessor_worksViaBareName() {
        Context context = context();
        context.setVariable("dossier", new ReferenceRecord("DOS-42"));
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "dossier.reference")).isEqualTo("DOS-42");
    }

    @Test
    void evaluate_nonRecordBareNameMethod_isRejected_evenIfPublic() {
        // Follow-up to D-40 Phase 9 finding: readProperty must not call an arbitrary
        // public no-arg method by its exact name on a plain (non-record) object — only
        // get/is-convention getters, or a record's own canonical accessors.
        Context context = context();
        context.setVariable("target", new DangerousBean());
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context, "target.delete"))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void evaluate_privateGetter_isNeverInvoked() {
        // Follow-up to D-40 Phase 9 finding: no setAccessible(true) — a private method
        // must stay unreachable regardless of naming convention.
        Context context = context();
        context.setVariable("target", new PrivateSecretBean());
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context, "target.secret"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "target.publicField")).isEqualTo("ok");
    }

    @Test
    void extract_nestedBracesInStringsAndStarOnly() {
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions(
                        "<p th:text=\"${#dates.format(now, '{yyyy}')}\">x</p>"))
                .containsExactly("#dates.format(now, '{yyyy}')");
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions(
                        "<span th:text=\"*{ref}\">y</span>"))
                .containsExactly("ref");
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions(
                        "<p th:text=\"${'a\\\"b'}\">x</p>"))
                .containsExactly("'a\\\"b'");
    }

    @Test
    void evaluate_unclosedStringAndBareMinusAndLongLiteral() {
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context(), "'oops"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context(), "-"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThat(SafeThymeleafExpressionSupport.evaluate(context(), "2147483648")).isEqualTo(2147483648L);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("title.wait"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("title.notify"))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void evaluate_datesFromDateAndListsContains() {
        Context context = context();
        Date now = Date.from(Instant.parse("2026-06-15T12:00:00Z"));
        context.setVariable("now", now);
        context.setVariable("items", List.of("a", "b"));
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "#dates.format(now, 'yyyy')")).isEqualTo("2026");
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "#lists.contains(items, 'a')")).isEqualTo(true);
        assertThat(SafeThymeleafExpressionSupport.evaluate(context, "#strings.concat('x', 'y')")).isEqualTo("xy");
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

    public static final class NamedBean {
        private final String label;

        public NamedBean(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public record ReferenceRecord(String reference) {}

    public static final class DangerousBean {
        public boolean delete() {
            throw new AssertionError("must never be invoked by the template evaluator");
        }
    }

    public static final class PrivateSecretBean {
        private String getSecret() {
            throw new AssertionError("must never be invoked by the template evaluator");
        }

        public String getPublicField() {
            return "ok";
        }
    }

    @Test
    void f5_anErrorRaisedByAUtility_isNotSwallowed() {
        Context context = context();
        context.setVariable("bomb", new Object() {
            @Override
            public String toString() {
                throw new OutOfMemoryError("simulated");
            }
        });
        ExpressionLimits limits = new ExpressionLimits(1000, 100_000, 100_000, 2_000);

        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.evaluate(context, "#strings.length(bomb)", limits))
                .isInstanceOf(OutOfMemoryError.class);
    }
}
