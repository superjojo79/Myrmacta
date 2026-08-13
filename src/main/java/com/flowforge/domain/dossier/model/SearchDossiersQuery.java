package com.flowforge.domain.dossier.model;

import java.util.Objects;

public record SearchDossiersQuery(
        DossierStatus status,
        String dossierTypeCode,
        String referencePrefix,
        int page,
        int size) {

    public SearchDossiersQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (dossierTypeCode != null && dossierTypeCode.isBlank()) {
            dossierTypeCode = null;
        }
        if (referencePrefix != null) {
            referencePrefix = referencePrefix.isBlank() ? null : referencePrefix.trim();
        }
    }

    public static SearchDossiersQuery of(
            DossierStatus status,
            String dossierTypeCode,
            String referencePrefix,
            int page,
            int size) {
        return new SearchDossiersQuery(status, dossierTypeCode, referencePrefix, page, size);
    }
}
