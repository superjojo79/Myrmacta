package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.SearchDossiersQuery;

final class DossierListQueryParser {

    private DossierListQueryParser() {}

    static SearchDossiersQuery parse(
            Integer page,
            Integer size,
            String sort,
            String status,
            String dossierTypeCode,
            String reference) {
        int resolvedPage = page == null ? 0 : page;
        if (resolvedPage < 0 || resolvedPage > SearchDossiersQuery.MAX_PAGE) {
            throw new IllegalArgumentException("page must be between 0 and " + SearchDossiersQuery.MAX_PAGE);
        }

        int resolvedSize = size == null ? 20 : size;
        if (resolvedSize < 1 || resolvedSize > SearchDossiersQuery.MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + SearchDossiersQuery.MAX_SIZE);
        }

        if (sort != null && !sort.isBlank() && !"updatedAt,desc".equals(sort.trim())) {
            throw new IllegalArgumentException("unsupported sort: only updatedAt,desc is allowed");
        }

        return SearchDossiersQuery.of(parseStatus(status), dossierTypeCode, reference, resolvedPage, resolvedSize);
    }

    static DossierStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DossierStatus.valueOf(status.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid status: " + status);
        }
    }
}
