package be.myrmacta.domain.shared;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResult {
        content = content == null ? List.of() : List.copyOf(content);
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be >= 0");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages must be >= 0");
        }
    }

    public static <T> PageResult<T> of(List<T> content, int page, int size, long totalElements) {
        Objects.requireNonNull(content, "content");
        int totalPages = size <= 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
        return new PageResult<>(content, page, size, totalElements, totalPages);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return of(List.of(), page, size, 0);
    }

    public static <T> PageResult<T> slice(List<T> all, PageQuery query) {
        Objects.requireNonNull(all, "all");
        Objects.requireNonNull(query, "query");
        List<T> source = List.copyOf(all);
        int from = Math.min(query.offset(), source.size());
        int to = Math.min(from + query.size(), source.size());
        return of(source.subList(from, to), query.page(), query.size(), source.size());
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        List<R> mapped = content.stream().<R>map(mapper).toList();
        return of(mapped, page, size, totalElements);
    }
}
