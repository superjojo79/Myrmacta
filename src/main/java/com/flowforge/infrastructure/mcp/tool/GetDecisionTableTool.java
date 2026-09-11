package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentInfo;
import com.flowforge.domain.workflow.port.in.GetDecisionTableDeploymentInfoUseCase;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GetDecisionTableTool {

    private final GetDecisionTableDeploymentInfoUseCase getDecisionTableDeploymentInfoUseCase;

    public GetDecisionTableTool(GetDecisionTableDeploymentInfoUseCase getDecisionTableDeploymentInfoUseCase) {
        this.getDecisionTableDeploymentInfoUseCase = Objects.requireNonNull(
                getDecisionTableDeploymentInfoUseCase, "getDecisionTableDeploymentInfoUseCase");
    }

    @McpTool(name = "get_decision_table", description = "Get a deployed DMN decision table's latest version by its key.")
    public DecisionTableDeploymentInfo getDecisionTable(
            @McpToolParam(required = true, description = "Decision key") String decisionKey) {
        return getDecisionTableDeploymentInfoUseCase
                .getDeploymentInfo(decisionKey)
                .orElseThrow(() -> new com.flowforge.domain.workflow.model.UnknownDecisionKeyException(
                        "No deployed DMN decision table found for key: " + decisionKey));
    }
}
