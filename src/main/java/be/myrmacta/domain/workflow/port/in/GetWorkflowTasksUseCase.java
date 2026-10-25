package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.WorkflowTaskView;
import java.util.UUID;

public interface GetWorkflowTasksUseCase {
    PageResult<WorkflowTaskView> getTasks(UUID linkId, AccessContext.User actor, PageQuery query);
}
