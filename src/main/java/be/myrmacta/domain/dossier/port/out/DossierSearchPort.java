package be.myrmacta.domain.dossier.port.out;

import be.myrmacta.domain.dossier.model.DossierPage;
import be.myrmacta.domain.dossier.model.FieldCondition;
import be.myrmacta.domain.dossier.model.DossierStatus;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public interface DossierSearchPort {

    DossierPage search(String tenantId, Filter filter, int page, int size);

    /**
     * TODO §102 (issue #184, lot B, D-SEARCH-BLOCK-SCOPE) — dossiers du type {@code dossierTypeCode} ni {@code CLOTURE} ni
     * {@code ARCHIVE}, autres que {@code excludeDossierId}, qui satisfont tous les {@code predicates} : leur nombre et la
     * référence du plus ancien (date de création, puis identifiant), {@code null} s'il n'y en a aucun.
     */
    MatchSummary findOpenMatches(
            String tenantId, String dossierTypeCode, List<FieldPredicate> predicates, UUID excludeDossierId);

    /** TODO §102 (D-SEARCH-BLOCK-OUTPUT) — résultat de {@link #findOpenMatches}. */
    record MatchSummary(long count, String firstReference) {
        public MatchSummary {
            if (count < 0 || (count == 0) != (firstReference == null)) {
                throw new IllegalArgumentException("a match summary has a first reference exactly when it counts a match");
            }
        }

        public static MatchSummary none() {
            return new MatchSummary(0, null);
        }
    }

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
