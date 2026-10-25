package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.workflow.model.DecisionTableVersionInfo;
import be.myrmacta.domain.workflow.port.in.ListDecisionTableVersionsUseCase;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ListDecisionTableVersionsTool {

    private final ListDecisionTableVersionsUseCase listDecisionTableVersionsUseCase;
    private final McpAccessContextFactory accessContextFactory;

    public ListDecisionTableVersionsTool(
            ListDecisionTableVersionsUseCase listDecisionTableVersionsUseCase,
            McpAccessContextFactory accessContextFactory) {
        this.listDecisionTableVersionsUseCase =
                Objects.requireNonNull(listDecisionTableVersionsUseCase, "listDecisionTableVersionsUseCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "list_decision_table_versions",
            description = "List every deployed version of a DMN decision table, oldest first.")
    public List<DecisionTableVersionInfo> listDecisionTableVersions(
            @McpToolParam(required = true, description = "Decision key") String decisionKey) {
        return listDecisionTableVersionsUseCase.list(accessContextFactory.currentActor(), decisionKey);
    }
}
