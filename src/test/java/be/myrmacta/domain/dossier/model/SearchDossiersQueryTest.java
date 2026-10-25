package be.myrmacta.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SearchDossiersQueryTest {

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> SearchDossiersQuery.of(null, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    void rejectsPageAboveMax() {
        assertThatThrownBy(() -> SearchDossiersQuery.of(null, null, null, SearchDossiersQuery.MAX_PAGE + 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    void acceptsMaxPage_andItsOffsetNeverOverflowsInt() {
        SearchDossiersQuery query = SearchDossiersQuery.of(null, null, null, SearchDossiersQuery.MAX_PAGE, 100);

        assertThatCode(() -> Math.toIntExact((long) query.page() * query.size())).doesNotThrowAnyException();
        assertThat((long) query.page() * query.size()).isLessThanOrEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void rejectsSizeOutOfBounds() {
        assertThatThrownBy(() -> SearchDossiersQuery.of(null, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SearchDossiersQuery.of(null, null, null, 0, SearchDossiersQuery.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
