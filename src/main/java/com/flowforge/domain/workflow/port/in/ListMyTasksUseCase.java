package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.workflow.model.MyWorkflowTask;
import java.util.List;

public interface ListMyTasksUseCase {

    List<MyWorkflowTask> listMine(String assigneeId);
}
