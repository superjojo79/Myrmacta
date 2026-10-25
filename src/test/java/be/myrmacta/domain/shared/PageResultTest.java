package be.myrmacta.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void of_computesTotalPagesFromCeilDivision() {
        PageResult<String> page = PageResult.of(List.of("a", "b"), 1, 2, 5);

        assertThat(page.content()).containsExactly("a", "b");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void empty_hasZeroTotals() {
        PageResult<String> page = PageResult.empty(0, 20);
        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void slice_returnsRequestedWindow() {
        PageResult<Integer> page = PageResult.slice(List.of(1, 2, 3, 4, 5), PageQuery.of(1, 2));
        assertThat(page.content()).containsExactly(3, 4);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void slice_pastEnd_returnsEmptyContent() {
        PageResult<Integer> page = PageResult.slice(List.of(1, 2), PageQuery.of(5, 20));
        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void of_rejectsNegativeTotalElements() {
        assertThatThrownBy(() -> PageResult.of(List.of(), 0, 20, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalElements");
    }

    @Test
    void map_transformsContentAndKeepsTotals() {
        PageResult<String> mapped = PageResult.of(List.of(1, 2), 0, 20, 2).map(value -> "n" + value);
        assertThat(mapped.content()).containsExactly("n1", "n2");
        assertThat(mapped.totalElements()).isEqualTo(2);
    }

    @Test
    void contentIsCopied() {
        List<String> mutable = new java.util.ArrayList<>(List.of("x"));
        PageResult<String> page = PageResult.of(mutable, 0, 20, 1);
        mutable.add("y");
        assertThat(page.content()).containsExactly("x");
    }
}
