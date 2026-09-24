package com.flowforge.domain.workflow.portal.port.in;

import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.FieldDefinition;
import java.time.Instant;
import java.util.List;

/** Vue minimale d'une tâche externe (D-PORTAL-DATA) : rien d'autre du dossier. */
public interface GetPortalTaskUseCase {

    PortalTaskView get(String token);

    record PortalTaskView(
            String dossierReference,
            String taskName,
            String instructions,
            String formLabel,
            List<FieldDefinition> fields,
            Instant expiresAt,
            List<DossierElement> ownAttachments) {}
}
