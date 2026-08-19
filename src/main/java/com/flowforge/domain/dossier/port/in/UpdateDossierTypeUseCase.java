package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import java.util.List;

public interface UpdateDossierTypeUseCase {
    DossierType update(UpdateDossierTypeCommand command, AccessContext.User actor);

    record UpdateDossierTypeCommand(
            String tenantId,
            String code,
            String label,
            String workflowKey,
            boolean active,
            boolean allowUnknownFields,
            List<FieldDefinition> fieldDefinitions,
            Integer retentionDays,
            boolean retentionNever) {}
}
