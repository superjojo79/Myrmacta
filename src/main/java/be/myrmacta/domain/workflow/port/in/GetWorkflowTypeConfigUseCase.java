package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import java.util.Optional;

public interface GetWorkflowTypeConfigUseCase {

    Optional<WorkflowTypeConfig> getByKey(String workflowKey);

    PageResult<WorkflowTypeConfig> list(PageQuery query);
}
