package be.myrmacta.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class SpringPagesTest {

    @Test
    void of_mapsPageAndSizeToUnsortedPageRequest() {
        Pageable pageable = SpringPages.of(PageQuery.of(2, 10));
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    void map_copiesWindowAndTotals() {
        Page<String> spring = new PageImpl<>(List.of("aa", "b"), PageRequest.of(1, 2), 5);
        PageResult<Integer> result = SpringPages.map(spring, String::length);
        assertThat(result.content()).containsExactly(2, 1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
    }
}
