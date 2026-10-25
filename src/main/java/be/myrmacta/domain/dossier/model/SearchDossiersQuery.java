package be.myrmacta.domain.dossier.model;

import be.myrmacta.domain.shared.PageQuery;
import java.util.List;
import java.util.Objects;

public record SearchDossiersQuery(
        DossierStatus status,
        String dossierTypeCode,
        String referencePrefix,
        int page,
        int size,
        List<FieldCondition> fieldConditions) {

    /** Largest size accepted — also the divisor used to derive {@link #MAX_PAGE}. */
    public static final int MAX_SIZE = PageQuery.MAX_SIZE;

    /**
     * Largest page accepted, chosen so {@code page * size} can never overflow {@code int} —
     * the persistence adapter multiplies the two to compute the query offset.
     */
    public static final int MAX_PAGE = PageQuery.MAX_PAGE;

    public SearchDossiersQuery {
        if (page < 0 || page > MAX_PAGE) {
            throw new IllegalArgumentException("page must be between 0 and " + MAX_PAGE);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
        fieldConditions = fieldConditions == null ? List.of() : List.copyOf(fieldConditions);
        if (fieldConditions.size() > FieldCondition.MAX_CONDITIONS) {
            throw new IllegalArgumentException(
                    "a search takes at most " + FieldCondition.MAX_CONDITIONS + " field filters");
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
        return new SearchDossiersQuery(status, dossierTypeCode, referencePrefix, page, size, List.of());
    }

    /** TODO §81 (D2) : avec critères sur champs dynamiques cherchables. */
    public static SearchDossiersQuery of(
            DossierStatus status,
            String dossierTypeCode,
            String referencePrefix,
            int page,
            int size,
            List<FieldCondition> fieldConditions) {
        return new SearchDossiersQuery(status, dossierTypeCode, referencePrefix, page, size, fieldConditions);
    }
}
