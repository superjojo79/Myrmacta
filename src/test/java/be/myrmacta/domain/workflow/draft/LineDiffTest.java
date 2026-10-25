package be.myrmacta.domain.workflow.draft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LineDiffTest {

    @Test
    void countsAddedAndRemovedLinesIgnoringWhitespaceAndOrder() {
        var s = LineDiff.summary("<a>\n  <b/>\n  <d/>\n</a>", "<a>\n<c/>\n<b/>\n</a>");
        assertThat(s.added()).isEqualTo(1);
        assertThat(s.removed()).isEqualTo(1);
    }

    @Test
    void duplicateLinesAreCountedAsAMultiset() {
        assertThat(LineDiff.summary("x\nx", "x\nx\nx").added()).isEqualTo(1);
        assertThat(LineDiff.summary("x\nx\nx", "x").removed()).isEqualTo(2);
    }

    @Test
    void neverPublishedMeansEverythingIsAdded() {
        var s = LineDiff.summary(null, "a\n\nb");
        assertThat(s.added()).isEqualTo(2);
        assertThat(s.removed()).isZero();
        assertThat(LineDiff.summary("a", "a").added()).isZero();
    }
}
