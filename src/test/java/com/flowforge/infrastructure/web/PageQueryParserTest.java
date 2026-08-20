package com.flowforge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.shared.PageQuery;
import org.junit.jupiter.api.Test;

class PageQueryParserTest {

    @Test
    void defaultsPageZeroAndSizeTwenty() {
        PageQuery query = PageQueryParser.parse(null, null, null, "code,asc");
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    void rejectsPageOutOfRange() {
        assertThatThrownBy(() -> PageQueryParser.parse(-1, 20, null, "code,asc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> PageQueryParser.parse(PageQuery.MAX_PAGE + 1, 20, null, "code,asc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    void rejectsSizeOutOfRange() {
        assertThatThrownBy(() -> PageQueryParser.parse(0, 0, null, "code,asc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
        assertThatThrownBy(() -> PageQueryParser.parse(0, 101, null, "code,asc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    void rejectsUnsupportedSort() {
        assertThatThrownBy(() -> PageQueryParser.parse(0, 20, "name,desc", "name,asc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported sort");
    }

    @Test
    void acceptsExactAllowedSort() {
        PageQuery query = PageQueryParser.parse(2, 10, "name,asc", "name,asc");
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(10);
    }
}
