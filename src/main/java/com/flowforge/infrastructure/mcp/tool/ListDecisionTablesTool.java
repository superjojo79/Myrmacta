package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentInfo;
import com.flowforge.domain.workflow.port.in.ListDecisionTablesUseCase;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class ListDecisionTablesTool {

    private final ListDecisionTablesUseCase listDecisionTablesUseCase;

    public ListDecisionTablesTool(ListDecisionTablesUseCase listDecisionTablesUseCase) {
        this.listDecisionTablesUseCase = Objects.requireNonNull(listDecisionTablesUseCase, "listDecisionTablesUseCase");
    }

    @McpTool(name = "list_decision_tables", description = "List deployed DMN decision tables (latest version each).")
    public List<DecisionTableDeploymentInfo> listDecisionTables() {
        return listDecisionTablesUseCase.list();
    }
}
