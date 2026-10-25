package be.myrmacta.infrastructure.web;

import be.myrmacta.domain.shared.PageQuery;
import java.util.Objects;

public final class PageQueryParser {

    private PageQueryParser() {}

    public static PageQuery parse(Integer page, Integer size, String sort, String allowedSort) {
        Objects.requireNonNull(allowedSort, "allowedSort");
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? PageQuery.DEFAULT_SIZE : size;
        if (sort != null && !sort.isBlank() && !allowedSort.equals(sort.trim())) {
            throw new IllegalArgumentException("unsupported sort: only " + allowedSort + " is allowed");
        }
        return PageQuery.of(resolvedPage, resolvedSize);
    }
}
