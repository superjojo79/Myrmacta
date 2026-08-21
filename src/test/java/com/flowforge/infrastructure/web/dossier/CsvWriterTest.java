package com.flowforge.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void writeRow_joinsFieldsWithCommas() {
        assertThat(CsvWriter.writeRow(List.of("a", "b", "c"))).isEqualTo("a,b,c\r\n");
    }

    @Test
    void writeRow_quotesFieldContainingComma() {
        assertThat(CsvWriter.writeRow(List.of("a,b", "c"))).isEqualTo("\"a,b\",c\r\n");
    }

    @Test
    void writeRow_quotesAndEscapesFieldContainingQuote() {
        assertThat(CsvWriter.writeRow(List.of("a\"b"))).isEqualTo("\"a\"\"b\"\r\n");
    }

    @Test
    void writeRow_quotesFieldContainingNewline() {
        assertThat(CsvWriter.writeRow(List.of("a\nb"))).isEqualTo("\"a\nb\"\r\n");
    }

    @Test
    void writeRow_quotesFieldContainingBareCarriageReturn() {
        assertThat(CsvWriter.writeRow(List.of("a\rb"))).isEqualTo("\"a\rb\"\r\n");
    }

    @Test
    void writeRow_treatsEmptyStringFieldAsEmpty() {
        assertThat(CsvWriter.writeRow(List.of("a", "", "c"))).isEqualTo("a,,c\r\n");
    }

    @Test
    void writeRow_treatsNullFieldAsEmpty() {
        assertThat(CsvWriter.writeRow(java.util.Arrays.asList("a", null, "c"))).isEqualTo("a,,c\r\n");
    }

    @Test
    void writeRow_doesNotQuotePlainField() {
        assertThat(CsvWriter.writeRow(List.of("plain"))).isEqualTo("plain\r\n");
    }

    @Test
    void writeRow_neutralizesLeadingEqualsToPreventFormulaInjection() {
        assertThat(CsvWriter.writeRow(List.of("=cmd|calc!A0")))
                .isEqualTo("'=cmd|calc!A0\r\n");
    }

    @Test
    void writeRow_neutralizesAndStillQuotesWhenFieldAlsoNeedsRfc4180Escaping() {
        assertThat(CsvWriter.writeRow(List.of("=A1,B1")))
                .isEqualTo("\"'=A1,B1\"\r\n");
    }

    @Test
    void writeRow_neutralizesLeadingPlusMinusAtAndTab() {
        assertThat(CsvWriter.writeRow(List.of("+1+1"))).isEqualTo("'+1+1\r\n");
        assertThat(CsvWriter.writeRow(List.of("-1-1"))).isEqualTo("'-1-1\r\n");
        assertThat(CsvWriter.writeRow(List.of("@SUM(A1)"))).isEqualTo("'@SUM(A1)\r\n");
        assertThat(CsvWriter.writeRow(List.of("\tHYPERLINK"))).isEqualTo("'\tHYPERLINK\r\n");
    }

    @Test
    void writeRow_doesNotNeutralizeANormalNumberOrDate() {
        assertThat(CsvWriter.writeRow(List.of("-42"))).isEqualTo("'-42\r\n");
        assertThat(CsvWriter.writeRow(List.of("2026-08-21"))).isEqualTo("2026-08-21\r\n");
    }
}
