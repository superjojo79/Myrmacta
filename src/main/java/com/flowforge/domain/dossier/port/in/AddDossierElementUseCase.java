package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.ElementType;
import java.util.Map;
import java.util.UUID;

public interface AddDossierElementUseCase {

    DossierElement add(AddCommand command, AccessContext ctx);

    record AddCommand(
            UUID dossierId,
            ElementType type,
            String label,
            String storageRef,
            Map<String, Object> metadata,
            String addedBy,
            String workflowStepId) {}
}
