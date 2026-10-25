package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.port.in.GetTaskQuestionSchemaUseCase;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ListTaskQuestionSchemasTool {

    private final GetTaskQuestionSchemaUseCase getTaskQuestionSchemaUseCase;
    private final McpAccessContextFactory accessContextFactory;

    public ListTaskQuestionSchemasTool(
            GetTaskQuestionSchemaUseCase getTaskQuestionSchemaUseCase, McpAccessContextFactory accessContextFactory) {
        this.getTaskQuestionSchemaUseCase =
                Objects.requireNonNull(getTaskQuestionSchemaUseCase, "getTaskQuestionSchemaUseCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(name = "list_task_question_schemas", description = "List task question schemas, paginated.")
    public PageResult<TaskQuestionSchema> listTaskQuestionSchemas(
            @McpToolParam(required = false, description = "Zero-based page index, default 0") Integer page,
            @McpToolParam(required = false, description = "Page size, 1-100, default 20") Integer size) {
        PageQuery query = PageQuery.of(page == null ? 0 : page, size == null ? PageQuery.DEFAULT_SIZE : size);
        return getTaskQuestionSchemaUseCase.list(query, accessContextFactory.currentActor());
    }
}
