package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.port.in.RedeployDecisionTableUseCase;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class RedeployDecisionTableTool {

    private static final Logger log = LoggerFactory.getLogger(RedeployDecisionTableTool.class);

    private final RedeployDecisionTableUseCase redeployDecisionTableUseCase;
    private final McpAccessContextFactory accessContextFactory;

    public RedeployDecisionTableTool(
            RedeployDecisionTableUseCase redeployDecisionTableUseCase, McpAccessContextFactory accessContextFactory) {
        this.redeployDecisionTableUseCase =
                Objects.requireNonNull(redeployDecisionTableUseCase, "redeployDecisionTableUseCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "redeploy_decision_table",
            description = "Redeploy an earlier version of a DMN decision table as the new current version. "
                    + "Does not delete history. Refuses when that version is already current.")
    public DecisionTableDeploymentResult redeployDecisionTable(
            @McpToolParam(required = true, description = "Decision key") String decisionKey,
            @McpToolParam(required = true, description = "Version number to redeploy") Integer version) {
        if (version == null) {
            throw new IllegalArgumentException("version is required");
        }
        log.info("dmn.redeploy decisionKey={} version={}", decisionKey, version);
        return redeployDecisionTableUseCase.redeploy(accessContextFactory.currentActor(), decisionKey, version);
    }
}
