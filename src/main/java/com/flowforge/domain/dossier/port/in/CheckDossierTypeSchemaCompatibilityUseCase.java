package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.UniqueDuplicate;
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
     * incompatibles).
     */
    record Result(int incompatibleCount, List<String> sampleReferences, List<UniqueDuplicate> duplicates) {
        public Result {
            sampleReferences = sampleReferences == null ? List.of() : List.copyOf(sampleReferences);
            duplicates = duplicates == null ? List.of() : List.copyOf(duplicates);
        }

        public Result(int incompatibleCount, List<String> sampleReferences) {
            this(incompatibleCount, sampleReferences, List.of());
        }

        public boolean compatible() {
            return incompatibleCount == 0;
        }
    }
}
