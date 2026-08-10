package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.WorkflowDiagramView;
import java.util.UUID;

public interface GetWorkflowDiagramUseCase {
    WorkflowDiagramView getDiagram(UUID linkId, AccessContext.User actor);
}
