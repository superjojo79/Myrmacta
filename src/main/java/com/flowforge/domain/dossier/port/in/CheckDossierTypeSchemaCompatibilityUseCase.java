package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.FieldDefinition;
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

    /** {@code sampleReferences} caps at a small preview size — {@code incompatibleCount} is the full count. */
    record Result(int incompatibleCount, List<String> sampleReferences) {
        public Result {
            sampleReferences = sampleReferences == null ? List.of() : List.copyOf(sampleReferences);
        }

        public boolean compatible() {
            return incompatibleCount == 0;
        }
    }
}
