package com.flowforge.domain.dossier.model;

import java.util.Objects;

public record SearchDossiersQuery(
        DossierStatus status,
        String dossierTypeCode,
        String referencePrefix,
        int page,
        int size) {

    /** Largest size accepted — also the divisor used to derive {@link #MAX_PAGE}. */
    public static final int MAX_SIZE = 100;

    /**
     * Largest page accepted, chosen so {@code page * size} can never overflow {@code int} —
     * the persistence adapter multiplies the two to compute the query offset.
     */
    public static final int MAX_PAGE = Integer.MAX_VALUE / MAX_SIZE;

    public SearchDossiersQuery {
        if (page < 0 || page > MAX_PAGE) {
            throw new IllegalArgumentException("page must be between 0 and " + MAX_PAGE);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
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
