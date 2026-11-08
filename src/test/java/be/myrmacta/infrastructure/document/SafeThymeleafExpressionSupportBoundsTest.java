package be.myrmacta.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;

/**
 * Bornes de coût, grammaire et conversions de l'évaluateur D-40 (registre §123) : chaque test fixe une valeur, une borne
 * ou un message exact, pour qu'un opérateur déplacé ou un contrôle retiré fasse échouer le test.
 */
class SafeThymeleafExpressionSupportBoundsTest {

    private static final long HUGE_BUDGET = 1_000_000L;

    private static ExpressionLimits limits(int utilityMax, int stringMax) {
        return new ExpressionLimits(utilityMax, stringMax, HUGE_BUDGET, 60_000L);
    }

    private static Object eval(Context context, String raw, ExpressionLimits limits) {
        return SafeThymeleafExpressionSupport.evaluate(context, raw, limits);
    }

    private static Object eval(Context context, String raw) {
        return SafeThymeleafExpressionSupport.evaluate(context, raw);
    }

    private static void assertRefused(Context context, String raw, ExpressionLimits limits, String message) {
        assertThatThrownBy(() -> eval(context, raw, limits))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining(message);
    }

    /** Le coût exact d'une évaluation : elle passe avec ce budget et échoue avec un de moins. */
    private static void assertCost(long expected, Context context, String raw) {
        ExpressionLimits enough = new ExpressionLimits(1000, 1000, expected, 60_000L);
        try (ExpressionLimits.Scope ignored = enough.begin()) {
            eval(context, raw, enough);
        }
        ExpressionLimits tooSmall = new ExpressionLimits(1000, 1000, expected - 1, 60_000L);
        try (ExpressionLimits.Scope ignored = tooSmall.begin()) {
            assertThatThrownBy(() -> eval(context, raw, tooSmall))
                    .isInstanceOf(TemplateProcessingException.class)
                    .hasMessageContaining("evaluation budget exhausted");
        }
    }

    // ---- coût d'une évaluation -------------------------------------------------------------------------------------

    @Test
    void cost_ofAStringLiteral_isOnePlusTheExpressionAndTheValueDividedBy64() {
        // expression de 130 caractères (2), valeur de 128 caractères (2) : 1 + 2 + 2
        assertCost(5, new Context(), "'" + "x".repeat(128) + "'");
    }

    @Test
    void cost_ofAVariable_followsTheWeightOfItsValue() {
        Context context = new Context();
        context.setVariable("d", new BigDecimal("1".repeat(200)));
        context.setVariable("i", BigInteger.ONE.shiftLeft(1000));
        context.setVariable("s", "y".repeat(130));
        context.setVariable("l", List.of(1, 2, 3, 4, 5, 6, 7));
        context.setVariable("m", Map.of("a", 1, "b", 2, "c", 3, "d", 4, "e", 5));
        context.setVariable("a", new Integer[] {1, 2, 3, 4, 5, 6, 7, 8, 9});
        context.setVariable("o", new Object());

        assertCost(1 + 200 / 64, context, "d");
        assertCost(1 + 1001 / 200, context, "i");
        assertCost(1 + 130 / 64, context, "s");
        assertCost(1 + 7, context, "l");
        assertCost(1 + 5, context, "m");
        assertCost(1 + 9, context, "a");
        assertCost(1, context, "o");
    }

    @Test
    void cost_ofAUtilityCall_chargesTheOperandsAndTheResultOnceMore() {
        // operandes (1) + résultat de 10 éléments (10) + évaluation (1 + 0 + 10)
        assertCost(22, new Context(), "#numbers.sequence(1,10)");

        Context context = new Context();
        context.setVariable("items", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        // operandes (1 + 10) + résultat entier (0) + évaluation (1)
        assertCost(12, context, "#lists.size(items)");
    }

    @Test
    void cost_withoutALimit_isNeverCharged() {
        ExpressionLimits tiny = new ExpressionLimits(1000, 1000, 1, 60_000L);
        try (ExpressionLimits.Scope ignored = tiny.begin()) {
            assertThat(SafeThymeleafExpressionSupport.evaluate(new Context(), "'x'")).isEqualTo("x");
        }
    }

    // ---- plafond des chaînes et des nombres --------------------------------------------------------------------------

    @Test
    void aStringAtTheLimit_passes_andOneMoreCharacterFails() {
        Context context = new Context();
        context.setVariable("ok", "a".repeat(10));
        context.setVariable("ko", "a".repeat(11));

        assertThat(eval(context, "ok", limits(100, 10))).isEqualTo("a".repeat(10));
        assertRefused(context, "ko", limits(100, 10), "string longer than 10 characters");
    }

    @Test
    void aBigDecimalAtTheLimit_passes_andOneMoreDigitFails() {
        Context context = new Context();
        context.setVariable("ok", new BigDecimal("1".repeat(300)));
        context.setVariable("ko", new BigDecimal("1".repeat(301)));

        assertThat(eval(context, "ok", limits(100, 300))).isEqualTo(new BigDecimal("1".repeat(300)));
        assertRefused(context, "ko", limits(100, 300), "number longer than 300 digits");
    }

    @Test
    void aBigIntegerIsBoundedByTheBitsOfItsDigits() {
        Context context = new Context();
        context.setVariable("ok", BigInteger.ONE.shiftLeft(999)); // 1000 bits : 300 chiffres x 10 / 3
        context.setVariable("ko", BigInteger.ONE.shiftLeft(1000)); // 1001 bits

        assertThat(eval(context, "ok", limits(100, 300))).isEqualTo(BigInteger.ONE.shiftLeft(999));
        assertRefused(context, "ko", limits(100, 300), "number longer than 300 digits");
    }

    // ---- bornes d'un appel d'utilitaire ------------------------------------------------------------------------------

    @Test
    void aStringOperandAtTheLimit_passes_andOneMoreCharacterFails() {
        Context context = new Context();
        context.setVariable("ok", "a".repeat(10));
        context.setVariable("ko", "a".repeat(11));

        assertThat(eval(context, "#strings.length(ok)", limits(100, 10))).isEqualTo(10);
        assertRefused(context, "#strings.length(ko)", limits(100, 10), "string operand longer than 10 characters");
    }

    @Test
    void aBigNumberOperand_isRefused() {
        Context context = new Context();
        context.setVariable("big", new BigDecimal("1".repeat(11)));
        context.setVariable("fine", new BigDecimal("1".repeat(10)));

        assertRefused(context, "#strings.length(big)", limits(100, 10), "number longer than 10 digits");
        assertThat(eval(context, "#strings.length(fine)", limits(100, 10))).isEqualTo(10);
    }

    @Test
    void randomAlphanumeric_isRefused() {
        assertRefused(new Context(), "#strings.randomAlphanumeric(5)", limits(100, 100), "randomAlphanumeric is not allowed");
    }

    @Test
    void sequence_isBoundedByItsSize_inBothDirections_andWithAStep() {
        Context context = new Context();
        assertThat((Object[]) eval(context, "#numbers.sequence(1,10)", limits(10, 100))).hasSize(10);
        assertRefused(context, "#numbers.sequence(1,10)", limits(9, 100), "#numbers.sequence size exceeds 9");

        assertThat((Object[]) eval(context, "#numbers.sequence(10,1,-1)", limits(10, 100))).hasSize(10);
        assertRefused(context, "#numbers.sequence(10,1,-1)", limits(9, 100), "#numbers.sequence size exceeds 9");

        // 1, 4, 7, 10
        assertThat((Object[]) eval(context, "#numbers.sequence(1,10,3)", limits(4, 100))).containsExactly(1, 4, 7, 10);
        assertRefused(context, "#numbers.sequence(1,10,3)", limits(3, 100), "#numbers.sequence size exceeds 3");
    }

    @Test
    void sequence_withAZeroStep_isRefused() {
        assertRefused(new Context(), "#numbers.sequence(1,10,0)", limits(100, 100), "#numbers.sequence step");
    }

    @Test
    void repeat_isBoundedByTheCountAndByTheProduct() {
        Context context = new Context();
        assertThat(eval(context, "#strings.repeat('ab',3)", limits(6, 100))).isEqualTo("ababab");
        assertRefused(context, "#strings.repeat('ab',3)", limits(5, 100), "#strings.repeat size exceeds 5");
        assertThat(eval(context, "#strings.repeat('a',10)", limits(10, 100))).isEqualTo("a".repeat(10));
        assertRefused(context, "#strings.repeat('a',11)", limits(10, 100), "#strings.repeat size exceeds 10");
        assertRefused(context, "#strings.repeat('a',-1)", limits(10, 100), "#strings.repeat size exceeds 10");
        // une chaîne vide compte pour un caractère : la borne porte alors sur le nombre de répétitions
        assertThat(eval(context, "#strings.repeat('',3)", limits(3, 100))).isEqualTo("");
        assertRefused(context, "#strings.repeat('',4)", limits(3, 100), "#strings.repeat size exceeds 3");
    }

    @Test
    void repeat_withANonNumericCount_isRefused() {
        assertRefused(new Context(), "#strings.repeat('a','b')", limits(10, 100), "numeric argument expected");
    }

    @Test
    void numericArguments_mustFitAnInt() {
        Context context = new Context();
        assertRefused(context, "#numbers.sequence(1,2147483648)", limits(10, 100), "numeric argument out of range");
        assertRefused(context, "#numbers.sequence(-2147483649,1)", limits(10, 100), "numeric argument out of range");
        // les bornes de l'int passent ce contrôle, puis butent sur la taille
        assertRefused(context, "#numbers.sequence(1,2147483647)", limits(10, 100), "#numbers.sequence size exceeds 10");
        assertRefused(context, "#numbers.sequence(-2147483648,1)", limits(10, 100), "#numbers.sequence size exceeds 10");
    }

    @Test
    void decimalArguments_mustBeWholeAndFitAnInt() {
        Context context = new Context();
        assertRefused(context, "#numbers.sequence(1.5,3)", limits(10, 100), "numeric argument out of range");
        assertRefused(context, "#numbers.sequence(1,3000000000.0)", limits(10, 100), "numeric argument out of range");
        assertRefused(context, "#numbers.sequence(1,2147483648.0)", limits(10, 100), "numeric argument out of range");
        assertRefused(context, "#numbers.sequence(1,2147483647.0)", limits(10, 100), "#numbers.sequence size exceeds 10");
        assertThat((Object[]) eval(context, "#numbers.sequence(1,4.0)", limits(10, 100))).containsExactly(1, 2, 3, 4);
    }

    @Test
    void numberFormat_digitsAreBounded_butNotTheNumberItself() {
        Context context = new Context();
        assertThat(eval(context, "#numbers.formatInteger(123456, 3)", limits(10, 100))).isEqualTo("123456");
        assertThat(eval(context, "#numbers.formatDecimal(1234.5, 1, 10)", limits(10, 100))).isNotNull();
        assertRefused(context, "#numbers.formatDecimal(1234.5, 1, 11)", limits(10, 100), "#numbers.formatDecimal digits exceed 10");
        assertRefused(context, "#numbers.formatDecimal(1234.5, 1, -11)", limits(10, 100), "#numbers.formatDecimal digits exceed 10");
        assertRefused(context, "#numbers.formatInteger(5, 11)", limits(10, 100), "#numbers.formatInteger digits exceed 10");
    }

    @Test
    void replace_isBoundedByTheWorstCaseSize() {
        Context context = new Context();
        // 4 occurrences de « a » remplacées par 2 caractères : 4 + 4 x 1 = 8
        assertThat(eval(context, "#strings.replace('aaaa','a','bb')", limits(100, 8))).isEqualTo("bbbbbbbb");
        assertRefused(context, "#strings.replace('aaaa','a','bb')", limits(100, 7), "#strings.replace result would exceed 7 characters");
        // motif vide : une occurrence de plus que de caractères, 2 + 3 x 1 = 5
        assertThat(eval(context, "#strings.replace('ab','','x')", limits(100, 5))).isEqualTo("ab");
        assertRefused(context, "#strings.replace('ab','','x')", limits(100, 4), "#strings.replace result would exceed 4 characters");
        // un remplacement plus court ne retire rien à la borne
        assertThat(eval(context, "#strings.replace('aaaa','aa','')", limits(100, 4))).isEqualTo("");
    }

    @Test
    void join_isBoundedByTheCharactersAndTheSeparators() {
        Context context = new Context();
        context.setVariable("items", List.of("a", "b", "c"));
        context.setVariable("pairs", new String[] {"ab", "cd"});

        // 3 caractères + 3 séparateurs de 2
        assertThat(eval(context, "#strings.listJoin(items, ', ')", limits(100, 9))).isEqualTo("a, b, c");
        assertRefused(context, "#strings.listJoin(items, ', ')", limits(100, 8), "#strings.listJoin result would exceed 8 characters");
        // 4 caractères + 2 séparateurs de 1
        assertThat(eval(context, "#strings.arrayJoin(pairs, '-')", limits(100, 6))).isEqualTo("ab-cd");
        assertRefused(context, "#strings.arrayJoin(pairs, '-')", limits(100, 5), "#strings.arrayJoin result would exceed 5 characters");
    }

    @Test
    void aCollectionResult_isBoundedByTheStringLimit() {
        Context context = new Context();
        assertThat((Object[]) eval(context, "#numbers.sequence(1,5)", limits(100, 5))).hasSize(5);
        assertRefused(context, "#numbers.sequence(1,6)", limits(100, 5), "collection larger than 5 elements");
    }

    @Test
    void anErrorMessage_neverCitesAValue() {
        Context context = new Context();
        context.setVariable("secret", "SECRET-VALUE-0123456789");

        assertThatThrownBy(() -> eval(context, "#strings.length(secret)", limits(100, 10)))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageNotContaining("SECRET");
    }

    // ---- locale, arité et conversions des arguments ------------------------------------------------------------------

    @Test
    void utilities_useTheLocaleOfTheContext() {
        Context context = new Context(Locale.FRENCH);
        context.setVariable("d", Date.from(Instant.parse("2026-06-15T12:00:00Z")));

        assertThat(eval(context, "#dates.format(d, 'MMMM')")).isEqualTo("juin");
        assertThat(eval(new Context(Locale.ENGLISH, Map.of("d", Date.from(Instant.parse("2026-06-15T12:00:00Z")))),
                        "#dates.format(d, 'MMMM')"))
                .isEqualTo("June");
    }

    @Test
    void variadicUtilities_acceptAnyNumberOfTrailingArguments() {
        Context context = new Context();
        assertThat(eval(context, "#strings.concat()")).isEqualTo("");
        assertThat(eval(context, "#strings.concat('a')")).isEqualTo("a");
        assertThat(eval(context, "#strings.concat('a','b','c')")).isEqualTo("abc");
        assertThat(eval(context, "#strings.concatReplaceNulls('-')")).isEqualTo("");
        assertThat(eval(context, "#strings.concatReplaceNulls('-', 'a', null, 'b')")).isEqualTo("a-b");
    }

    @Test
    void aFixedArityUtility_refusesTheWrongNumberOfArguments() {
        Context context = new Context();
        assertThatThrownBy(() -> eval(context, "#strings.length('a','b')"))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("Unsupported Thymeleaf expression");
        assertThatThrownBy(() -> eval(context, "#strings.length()")).isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void aDecimalArgument_isConvertedToAnInt() {
        Context context = new Context();
        assertThat(eval(context, "#strings.substring('abcdef', 1.0, 3.0)")).isEqualTo("bc");
        assertThat(eval(context, "#strings.substring('abcdef', 2)")).isEqualTo("cdef");
    }

    @Test
    void anArgumentThatCannotBeConverted_isRefusedAsUnsupported() {
        Context context = new Context();
        context.setVariable("tags", Set.of("a"));
        assertThatThrownBy(() -> eval(context, "#strings.contains('abc', 1)"))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("Unsupported Thymeleaf expression");
        assertThatThrownBy(() -> eval(context, "#lists.size(tags)"))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("Unsupported Thymeleaf expression");
        assertThatThrownBy(() -> eval(context, "#strings.substring('abc', null, 2)"))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("Unsupported Thymeleaf expression");
    }

    // ---- grammaire ---------------------------------------------------------------------------------------------------

    @Test
    void whitespace_isAcceptedAroundEveryToken() {
        Context context = new Context();
        context.setVariable("dossier", Map.of("reference", "DOS-1"));
        assertThat(eval(context, " # strings . concat ( 'a' , 'b' ) ")).isEqualTo("ab");
        assertThat(eval(context, "#strings.concat( 'a' ,'b' )")).isEqualTo("ab");
        assertThat(eval(context, "#strings.concat( )")).isEqualTo("");
        assertThat(eval(context, " #vars . dossier . reference ")).isEqualTo("DOS-1");
        assertThat(eval(context, "dossier . reference")).isEqualTo("DOS-1");
        assertThat(eval(context, "# vars.dossier.reference")).isEqualTo("DOS-1");
    }

    @Test
    void anExpressionCutShort_isRefused_asATemplateError() {
        Context context = new Context();
        for (String raw : List.of(
                "#strings.concat('a',", "#strings.", "#", "dossier.", "#vars.", "#vars.a.", "#strings.concat('a'", "#strings.concat")) {
            assertThatThrownBy(() -> eval(context, raw))
                    .as(raw)
                    .isInstanceOf(TemplateProcessingException.class);
        }
    }

    @Test
    void aTrailingBackslashInAString_isAnUnterminatedString() {
        assertThatThrownBy(() -> eval(new Context(), "'a\\")).isInstanceOf(TemplateProcessingException.class);
        assertThat(eval(new Context(), "'a\\\\b'")).isEqualTo("a\\b");
    }

    @Test
    void aDoubledQuoteAtTheEnd_endsTheString() {
        assertThat(eval(new Context(), "'it'''")).isEqualTo("it'");
        assertThat(eval(new Context(), "'a'")).isEqualTo("a");
    }

    @Test
    void aNumberLiteral_needsDigitsAfterTheDecimalPoint() {
        assertThatThrownBy(() -> eval(new Context(), "5.")).isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> eval(new Context(), "5.x")).isInstanceOf(TemplateProcessingException.class);
        assertThat(eval(new Context(), "5.25")).isEqualTo(5.25d);
        assertThat(eval(new Context(), "-5")).isEqualTo(-5);
    }

    @Test
    void identifiers_startWithALetterOrAnUnderscore() {
        Context context = new Context();
        context.setVariable("_x", "under");
        context.setVariable("a", Map.of("b1", "digit"));
        assertThat(eval(context, "_x")).isEqualTo("under");
        assertThat(eval(context, "a.b1")).isEqualTo("digit");
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("a.1b"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("a.$b"))
                .isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed("#strings.1x('a')"))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void keywords_areOnlyKeywordsWhenBare() {
        Context context = new Context();
        context.setVariable("true", Map.of("x", "from-variable"));
        context.setVariable("T", Map.of("x", "t-variable"));
        context.setVariable("newer", "n");
        assertThat(eval(context, "true.x")).isEqualTo("from-variable");
        assertThat(eval(context, "true")).isEqualTo(Boolean.TRUE);
        assertThat(eval(context, "T.x")).isEqualTo("t-variable");
        assertThat(eval(context, "newer")).isEqualTo("n");
        assertThatThrownBy(() -> eval(context, "T(java.lang.Runtime)")).isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> eval(context, "new java.lang.Object()")).isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> eval(context, "new")).isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> eval(context, "true(1)")).isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void blockedProperties_areRefused_inEveryPosition() {
        Context context = new Context();
        context.setVariable("class", "variable-named-class");
        context.setVariable("a", Map.of("class", "x", "getClass", "y", "wait", "z"));
        for (String raw : List.of(
                "class", "getClass", "a.class", "a.getClass", "a.wait", "a.notify", "a.notifyAll", "a.CLASS",
                "#vars.class", "#vars.a.class", "#vars.a.getClass", "#vars.a.wait")) {
            assertThatThrownBy(() -> SafeThymeleafExpressionSupport.assertAllowed(raw))
                    .as("assertAllowed(%s)", raw)
                    .isInstanceOf(TemplateProcessingException.class);
            assertThatThrownBy(() -> eval(context, raw))
                    .as("evaluate(%s)", raw)
                    .isInstanceOf(TemplateProcessingException.class);
        }
    }

    // ---- extraction des expressions ----------------------------------------------------------------------------------

    @Test
    void extract_findsTheFirstOpenerWhicheverComesFirst() {
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions("${a} *{b}")).containsExactly("a", "b");
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions("*{a} ${b}")).containsExactly("a", "b");
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions("x ${a} y *{b} z ${c}"))
                .containsExactly("a", "b", "c");
    }

    @Test
    void extract_followsNestedBracesAndQuotes() {
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions("${a{b}c} ${d}"))
                .containsExactly("a{b}c", "d");
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions("${'it\\'s'} ${c}"))
                .containsExactly("'it\\'s'", "c");
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions("${\"a'b\"} ${c}"))
                .containsExactly("\"a'b\"", "c");
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions("${'a\"b'} ${c}"))
                .containsExactly("'a\"b'", "c");
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions("${'}'} ${\"}\"}"))
                .containsExactly("'}'", "\"}\"");
        assertThat(SafeThymeleafExpressionSupport.extractVariableExpressions("${'\\\\'} ${c}"))
                .containsExactly("'\\\\'", "c");
    }

    @Test
    void extract_aBracedQuoteInsideAStringDoesNotCloseIt() {
        assertThatThrownBy(() -> SafeThymeleafExpressionSupport.extractVariableExpressions("${'unterminated}"))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("Unclosed template expression");
    }

    // ---- accès aux propriétés ----------------------------------------------------------------------------------------

    @Test
    void properties_ofABeanAreReadThroughGetAndIsOnly() {
        Context context = new Context();
        context.setVariable("bean", new Bean());
        assertThat(eval(context, "bean.name")).isEqualTo("bean-name");
        assertThat(eval(context, "bean.active")).isEqualTo(true);
        assertThatThrownBy(() -> eval(context, "bean.nothing")).isInstanceOf(TemplateProcessingException.class);
        assertThatThrownBy(() -> eval(context, "bean.empty")).isInstanceOf(TemplateProcessingException.class);
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("k", null);
        context.setVariable("withNull", withNull);
        assertThatThrownBy(() -> eval(context, "withNull.k"))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("Unknown template property: withNull.k");
    }

    public static final class Bean {
        public String getName() {
            return "bean-name";
        }

        public boolean isActive() {
            return true;
        }

        public String getEmpty() {
            return null;
        }
    }
}
