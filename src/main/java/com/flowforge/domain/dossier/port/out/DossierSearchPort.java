package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.DossierPage;
import com.flowforge.domain.dossier.model.FieldCondition;
import com.flowforge.domain.dossier.model.DossierStatus;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public interface DossierSearchPort {

    DossierPage search(String tenantId, Filter filter, int page, int size);

    /**
     * @param dossierTypeCodes {@code null} = no type restriction (admin bypass);
     *     non-null set = restrict to those codes (must be non-empty when called).
     */
    record Filter(
            DossierStatus status,
            Set<String> dossierTypeCodes,
            String referencePrefix,
            List<FieldPredicate> fieldPredicates) {

        /** Sans critère sur champ dynamique (le cas historique). */
        public Filter(DossierStatus status, Set<String> dossierTypeCodes, String referencePrefix) {
            this(status, dossierTypeCodes, referencePrefix, List.of());
        }

        public Filter {
            fieldPredicates = fieldPredicates == null ? List.of() : List.copyOf(fieldPredicates);
            if (dossierTypeCodes != null) {
                dossierTypeCodes = Set.copyOf(dossierTypeCodes);
            }
            if (referencePrefix != null && referencePrefix.isBlank()) {
                referencePrefix = null;
            }
        }

        public Filter withFieldPredicates(List<FieldPredicate> predicates) {
            return new Filter(status, dossierTypeCodes, referencePrefix, predicates);
        }

        public static Filter unrestricted(DossierStatus status, String referencePrefix) {
            return new Filter(status, null, referencePrefix);
        }

        public static Filter restricted(
                DossierStatus status, Set<String> dossierTypeCodes, String referencePrefix) {
            Objects.requireNonNull(dossierTypeCodes, "dossierTypeCodes");
            if (dossierTypeCodes.isEmpty()) {
                throw new IllegalArgumentException("dossierTypeCodes must be non-empty");
            }
            return new Filter(status, dossierTypeCodes, referencePrefix);
        }
    }

    /** Nature de la valeur comparée : {@code TEXT} (texte, SELECT, date ISO), {@code NUMBER} (décimal), {@code BOOLEAN}. */
    enum Kind {
        TEXT,
        NUMBER,
        BOOLEAN
    }

    /**
     * TODO §81 (D2) — critère typé sur un champ dynamique : déjà validé contre la définition du type (champ déclaré
     * cherchable, opérateur compatible, valeur convertie). Les valeurs sont {@code String}, {@code BigDecimal} ou
     * {@code Boolean} selon {@link Kind}.
     */
    record FieldPredicate(String field, Kind kind, FieldCondition.Op op, List<Object> values) {
        public FieldPredicate {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(op, "op");
            values = List.copyOf(values);
        }
    }
}
