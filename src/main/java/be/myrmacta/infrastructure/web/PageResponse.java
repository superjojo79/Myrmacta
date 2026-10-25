package be.myrmacta.infrastructure.web;

import be.myrmacta.domain.shared.PageResult;
import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(PageResult<T> page) {
        return new PageResponse<>(
                page.content(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
