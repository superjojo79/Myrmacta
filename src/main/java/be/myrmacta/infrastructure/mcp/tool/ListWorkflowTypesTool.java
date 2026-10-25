package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ListWorkflowTypesTool {

    private final GetWorkflowTypeConfigUseCase getWorkflowTypeConfigUseCase;

    public ListWorkflowTypesTool(GetWorkflowTypeConfigUseCase getWorkflowTypeConfigUseCase) {
        this.getWorkflowTypeConfigUseCase =
                Objects.requireNonNull(getWorkflowTypeConfigUseCase, "getWorkflowTypeConfigUseCase");
    }

    @McpTool(name = "list_workflow_types", description = "List workflow type configurations, paginated.")
    public PageResult<WorkflowTypeConfig> listWorkflowTypes(
            @McpToolParam(required = false, description = "Zero-based page index, default 0") Integer page,
            @McpToolParam(required = false, description = "Page size, 1-100, default 20") Integer size) {
        PageQuery query = PageQuery.of(page == null ? 0 : page, size == null ? PageQuery.DEFAULT_SIZE : size);
        return getWorkflowTypeConfigUseCase.list(query);
    }
}
