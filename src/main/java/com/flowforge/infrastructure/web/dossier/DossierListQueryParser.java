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
        if (resolvedPage < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }

        int resolvedSize = size == null ? 20 : size;
        if (resolvedSize < 1 || resolvedSize > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        if (sort != null && !sort.isBlank() && !"updatedAt,desc".equals(sort.trim())) {
            throw new IllegalArgumentException("unsupported sort: only updatedAt,desc is allowed");
        }

        DossierStatus resolvedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                resolvedStatus = DossierStatus.valueOf(status.trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("invalid status: " + status);
            }
        }

        return SearchDossiersQuery.of(resolvedStatus, dossierTypeCode, reference, resolvedPage, resolvedSize);
    }
}
