package com.flowforge.domain.workflow.palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.workflow.model.InvalidBpmnException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TODO §78 (D3b) — format « champ = expression » du paramètre fieldMapping. */
class FieldMappingSpecTest {

    private static List<FieldMappingSpec.Entry> parse(String raw) {
        return FieldMappingSpec.parse(raw, "owner");
    }

    @Test
    void parsesOneEntryPerLineSplittingOnTheFirstEqualsSign() {
        assertThat(parse("score = connectorResult.json.score\n\n  ok=a = b and c == d ")).containsExactly(
                new FieldMappingSpec.Entry("score", "connectorResult.json.score"),
                new FieldMappingSpec.Entry("ok", "a = b and c == d"));
    }

    @Test
    void acceptsWindowsLineEndingsAndIgnoresBlankLines() {
        assertThat(parse("a = 1\r\n\r\nb = 2")).extracting(FieldMappingSpec.Entry::field).containsExactly("a", "b");
    }

    @Test
    void refusesEmptyDuplicateAndMalformedLines() {
        for (String bad : List.of("", "   \n  ", "no equals", "= 1", "a =", "a = 1\na = 2", "1a = 1", "a b = 1", "a-b = 1", "a == 1")) {
            assertThatThrownBy(() -> parse(bad)).as(bad).isInstanceOf(InvalidBpmnException.class);
        }
        assertThatThrownBy(() -> FieldMappingSpec.parse(null, "owner")).isInstanceOf(InvalidBpmnException.class);
    }

    @Test
    void boundsTheNumberOfEntriesAndTheExpressionLength() {
        String twentyOne = String.join("\n", java.util.stream.IntStream.rangeClosed(1, 21).mapToObj(i -> "f" + i + " = 1").toList());
        assertThatThrownBy(() -> parse(twentyOne)).hasMessageContaining("at most 20");
        assertThat(parse(twentyOne.substring(0, twentyOne.lastIndexOf('\n')))).hasSize(20);
        assertThatThrownBy(() -> parse("a = " + "x".repeat(FieldMappingSpec.MAX_EXPRESSION_CHARS + 1))).hasMessageContaining("exceeds");
        assertThat(parse("a = " + "x".repeat(FieldMappingSpec.MAX_EXPRESSION_CHARS))).hasSize(1);
    }

    @Test
    void anOverlongFieldNameIsRefusedAndAbbreviatedInTheMessage() {
        String name = "n".repeat(61);
        assertThatThrownBy(() -> parse(name + " = 1")).hasMessageContaining("valid field name").hasMessageNotContaining(name);
    }
}
