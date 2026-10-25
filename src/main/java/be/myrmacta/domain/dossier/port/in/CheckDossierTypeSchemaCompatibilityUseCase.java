package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.UniqueDuplicate;
import java.util.List;
import java.util.Objects;

public interface CheckDossierTypeSchemaCompatibilityUseCase {

    Result check(Query query);

    record Query(
            String tenantId,
            String dossierTypeCode,
            List<FieldDefinition> newFieldDefinitions,
            boolean allowUnknownFields) {
        public Query {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
            newFieldDefinitions = newFieldDefinitions == null ? List.of() : List.copyOf(newFieldDefinitions);
        }
    }

    /**
     * {@code sampleReferences} caps at a small preview size — {@code incompatibleCount} is the full count. TODO §102 (U10) :
     * {@code duplicates} = valeurs d'un champ unique tenues par plusieurs dossiers ouverts (ces dossiers comptent comme
     * incompatibles), au plus 20. {@code duplicateCount} est le nombre total de ces valeurs, y compris celles non citées.
     * Chaque entrée borne ses références à 20 et garde le total dans {@code referenceCount}.
     */
    record Result(int incompatibleCount, List<String> sampleReferences, List<UniqueDuplicate> duplicates, int duplicateCount) {
        public Result {
            sampleReferences = sampleReferences == null ? List.of() : List.copyOf(sampleReferences);
            duplicates = duplicates == null ? List.of() : List.copyOf(duplicates);
            if (duplicateCount < duplicates.size()) {
                throw new IllegalArgumentException("duplicateCount");
            }
        }

        public Result(int incompatibleCount, List<String> sampleReferences, List<UniqueDuplicate> duplicates) {
            this(incompatibleCount, sampleReferences, duplicates, duplicates == null ? 0 : duplicates.size());
        }

        public Result(int incompatibleCount, List<String> sampleReferences) {
            this(incompatibleCount, sampleReferences, List.of());
        }

        public boolean compatible() {
            return incompatibleCount == 0;
        }
    }
}
