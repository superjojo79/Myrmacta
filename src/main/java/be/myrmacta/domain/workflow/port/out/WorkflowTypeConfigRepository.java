package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import java.util.List;
import java.util.Optional;

public interface WorkflowTypeConfigRepository {

    WorkflowTypeConfig save(WorkflowTypeConfig config);

    Optional<WorkflowTypeConfig> findByWorkflowKey(String tenantId, String workflowKey);

    List<WorkflowTypeConfig> findAll(String tenantId);

    default PageResult<WorkflowTypeConfig> page(String tenantId, PageQuery query) {
        return PageResult.slice(findAll(tenantId), query);
    }
}
