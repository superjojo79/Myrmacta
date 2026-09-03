package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import java.util.List;

public interface CreateDossierTypeUseCase {
    DossierType create(CreateDossierTypeCommand command, AccessContext.User actor);

    record CreateDossierTypeCommand(
            String tenantId,
            String code,
            String label,
            String workflowKey,
            boolean active,
            boolean allowUnknownFields,
            List<FieldDefinition> fieldDefinitions,
            Integer retentionDays,
            boolean retentionNever,
            Integer slaTargetHours,
            List<String> documentTemplateKeys) {

        /** Convenience for callers built before {@code documentTemplateKeys} existed (TODO §52). */
        public CreateDossierTypeCommand(
                String tenantId,
                String code,
                String label,
                String workflowKey,
                boolean active,
                boolean allowUnknownFields,
                List<FieldDefinition> fieldDefinitions,
                Integer retentionDays,
                boolean retentionNever,
                Integer slaTargetHours) {
            this(tenantId, code, label, workflowKey, active, allowUnknownFields, fieldDefinitions,
                    retentionDays, retentionNever, slaTargetHours, List.of());
        }
    }
}
