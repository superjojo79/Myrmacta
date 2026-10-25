package be.myrmacta.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.shared.PageResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void from_copiesEnvelopeFields() {
        PageResponse<String> response = PageResponse.from(PageResult.of(List.of("a"), 1, 20, 21));
        assertThat(response.content()).containsExactly("a");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(2);
    }
}
