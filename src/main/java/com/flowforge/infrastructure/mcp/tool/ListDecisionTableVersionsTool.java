package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.workflow.model.DecisionTableVersionInfo;
import com.flowforge.domain.workflow.port.in.ListDecisionTableVersionsUseCase;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
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
