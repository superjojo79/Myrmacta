package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.workflow.model.DecisionTestCase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DmnCsvTestCaseParserTest {

    private final DmnCsvTestCaseParser parser = new DmnCsvTestCaseParser();

    @Test
    void parsesInputAndExpectedColumnsIntoOneRowPerLine() {
        List<DecisionTestCase> cases = parser.parse("""
                input.customerCat,expected.result
                BRONZE,ok
                "SILVER,PLUS",review
                """);

        assertThat(cases).containsExactly(
                new DecisionTestCase(Map.of("customerCat", "BRONZE"), List.of(Map.of("result", "ok"))),
                new DecisionTestCase(Map.of("customerCat", "SILVER,PLUS"), List.of(Map.of("result", "review"))));
    }

    @Test
    void coercesNumericAndBooleanLiterals() {
        List<DecisionTestCase> cases = parser.parse("""
                input.amount,expected.accepted
                1000,true
                1000.5,false
                """);

        assertThat(cases.getFirst().inputs()).containsEntry("amount", 1000);
        assertThat(cases.getFirst().expectedRows().getFirst()).containsEntry("accepted", true);
        assertThat(cases.get(1).inputs().get("amount")).isEqualTo(new java.math.BigDecimal("1000.5"));
        assertThat(cases.get(1).expectedRows().getFirst()).containsEntry("accepted", false);
    }

    @Test
    void rejectsColumnsOutsideThePrefixes() {
        assertThatThrownBy(() -> parser.parse("""
                decisionKey,input.customerCat,expected.result
                eligibilite,BRONZE,ok
                """)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingPrefixAndBlankPayload() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("""
                input.customerCat
                BRONZE
                """)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("""
                expected.result
                ok
                """)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("""
                input.,expected.result
                x,ok
                """)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankCellIsNullAndOversizedIntegerBecomesDecimal() {
        List<DecisionTestCase> cases = parser.parse("""
                input.amount,expected.note
                ,pending
                2147483648,ok
                """);

        assertThat(cases.getFirst().inputs()).containsEntry("amount", null);
        assertThat(cases.get(1).inputs().get("amount")).isEqualTo(new java.math.BigDecimal("2147483648"));
    }

    @Test
    void rejectsMoreThanOneHundredDataRows() {
        StringBuilder csv = new StringBuilder("input.a,expected.b\n");
        for (int i = 0; i < 101; i++) {
            csv.append("x,y\n");
        }

        assertThatThrownBy(() -> parser.parse(csv.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    void rejectsMalformedOrEmptyCsv() {
        assertThatThrownBy(() -> parser.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("input.customerCat,expected.result\n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("input.customerCat,expected.result\n\"unterminated"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
