package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import be.myrmacta.domain.workflow.port.in.ListDecisionTablesUseCase;
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
