package be.myrmacta.domain.dossier.model;

import java.util.List;
import java.util.Objects;

public record DossierPage(
        List<DossierListItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public DossierPage {
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

    public static DossierPage of(List<DossierListItem> content, int page, int size, long totalElements) {
        Objects.requireNonNull(content, "content");
        int totalPages = size <= 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
        return new DossierPage(content, page, size, totalElements, totalPages);
    }

    public static DossierPage empty(int page, int size) {
        return of(List.of(), page, size, 0);
    }
}
