package be.myrmacta.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageQueryTest {

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> PageQuery.of(-1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    void rejectsPageAboveMax() {
        assertThatThrownBy(() -> PageQuery.of(PageQuery.MAX_PAGE + 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    void acceptsMaxPage_andItsOffsetNeverOverflowsInt() {
        PageQuery query = PageQuery.of(PageQuery.MAX_PAGE, 100);
        assertThatCode(() -> Math.toIntExact((long) query.page() * query.size())).doesNotThrowAnyException();
        assertThat((long) query.page() * query.size()).isLessThanOrEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void rejectsSizeOutOfBounds() {
        assertThatThrownBy(() -> PageQuery.of(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageQuery.of(0, PageQuery.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaults_arePageZeroAndSizeTwenty() {
        PageQuery query = PageQuery.defaults();
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(PageQuery.DEFAULT_SIZE);
        assertThat(query.offset()).isZero();
    }
}
