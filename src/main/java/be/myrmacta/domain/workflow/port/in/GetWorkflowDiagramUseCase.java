package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.WorkflowDiagramView;
import java.util.UUID;

public interface GetWorkflowDiagramUseCase {
    WorkflowDiagramView getDiagram(UUID linkId, AccessContext.User actor);
}
