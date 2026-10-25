package be.myrmacta.infrastructure.persistence;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class SpringPages {

    private SpringPages() {}

    public static Pageable of(PageQuery query) {
        return PageRequest.of(query.page(), query.size());
    }

    public static <E, D> PageResult<D> map(Page<E> page, Function<E, D> mapper) {
        return PageResult.of(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}
