package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.MyWorkflowTask;

public interface ListMyTasksUseCase {

    PageResult<MyWorkflowTask> listMine(String assigneeId, PageQuery query);
}
