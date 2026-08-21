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
    void writeRow_treatsNullFieldAsEmpty() {
        assertThat(CsvWriter.writeRow(java.util.Arrays.asList("a", null, "c"))).isEqualTo("a,,c\r\n");
    }

    @Test
    void writeRow_doesNotQuotePlainField() {
        assertThat(CsvWriter.writeRow(List.of("plain"))).isEqualTo("plain\r\n");
    }
}
