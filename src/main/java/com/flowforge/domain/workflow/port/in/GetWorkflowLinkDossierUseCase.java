package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.WorkflowLinkDossierView;
import java.util.UUID;

public interface GetWorkflowLinkDossierUseCase {
    WorkflowLinkDossierView getDossier(UUID linkId, AccessContext.User actor);
}
