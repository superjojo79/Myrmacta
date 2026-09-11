package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.port.in.DeployDecisionTableUseCase;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * {@code deploy_decision_table} : parses + validates the DMN 1.3 XML (JUEL allowlist, TODO §61)
 * then deploys. Mirrors {@code deploy_workflow}'s {@code dryRun} contract — validates without
 * persisting or deploying.
 */
@Component
public class DeployDecisionTableTool {

    private final DmnParserPort dmnParserPort;
    private final DeployDecisionTableUseCase deployDecisionTableUseCase;
    private final DataSize maxXmlSize;

    public DeployDecisionTableTool(
            DmnParserPort dmnParserPort,
            DeployDecisionTableUseCase deployDecisionTableUseCase,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxXmlSize) {
        this.dmnParserPort = Objects.requireNonNull(dmnParserPort, "dmnParserPort");
        this.deployDecisionTableUseCase =
                Objects.requireNonNull(deployDecisionTableUseCase, "deployDecisionTableUseCase");
        this.maxXmlSize = Objects.requireNonNull(maxXmlSize, "maxXmlSize");
    }

    @McpTool(
            name = "deploy_decision_table",
            description = "Deploy a DMN 1.3 decision table (UTF-8 XML text, not base64). decisionKey must "
                    + "match the decision id. Input/output cell expressions are validated against a JUEL "
                    + "allowlist (literals and simple comparisons only, never a bare identifier) before "
                    + "deployment. dryRun=true validates without deploying.")
    public Map<String, Object> deployDecisionTable(
            @McpToolParam(required = true, description = "Decision key — must match the DMN decision id")
                    String decisionKey,
            @McpToolParam(required = true, description = "DMN 1.3 XML document, UTF-8 text (not base64)") String xml,
            @McpToolParam(required = false, description = "Validate without persisting or deploying; default false")
                    Boolean dryRun) {
        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
        if (xmlBytes.length > maxXmlSize.toBytes()) {
            throw new IllegalArgumentException("xml exceeds max size of " + maxXmlSize);
        }
        String extractedKey = dmnParserPort.extractDecisionKey(xmlBytes);
        if (!extractedKey.equals(decisionKey)) {
            throw new IllegalArgumentException(
                    "decisionKey (" + decisionKey + ") does not match the DMN decision id (" + extractedKey + ")");
        }

        if (Boolean.TRUE.equals(dryRun)) {
            return result(decisionKey, true, null);
        }

        String filename = decisionKey + ".dmn";
        DecisionTableDeploymentResult deployment = deployDecisionTableUseCase.deploy(filename, xmlBytes);
        return result(decisionKey, false, deployment);
    }

    private static Map<String, Object> result(String decisionKey, boolean dryRun, DecisionTableDeploymentResult deployment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("decisionKey", decisionKey);
        map.put("dryRun", dryRun);
        map.put("deployment", deployment);
        return map;
    }
}
