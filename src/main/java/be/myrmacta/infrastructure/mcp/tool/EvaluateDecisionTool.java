package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.workflow.model.DecisionEvaluationResult;
import be.myrmacta.domain.workflow.port.in.EvaluateDecisionUseCase;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class EvaluateDecisionTool {

    private final EvaluateDecisionUseCase evaluateDecisionUseCase;
    private final McpAccessContextFactory accessContextFactory;

    public EvaluateDecisionTool(
            EvaluateDecisionUseCase evaluateDecisionUseCase, McpAccessContextFactory accessContextFactory) {
        this.evaluateDecisionUseCase = Objects.requireNonNull(evaluateDecisionUseCase, "evaluateDecisionUseCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "evaluate_decision",
            description = "Evaluate a deployed DMN decision table. version omitted means the latest deployed version. "
                    + "No matching rule returns an empty row list.")
    public DecisionEvaluationResult evaluateDecision(
            @McpToolParam(required = true, description = "Decision key") String decisionKey,
            @McpToolParam(required = true, description = "Input values keyed by DMN input name") Map<String, Object> inputs,
            @McpToolParam(required = false, description = "Deployed version; omit for the latest") Integer version) {
        return evaluateDecisionUseCase.evaluate(
                accessContextFactory.currentActor(),
                decisionKey,
                version,
                McpArgs.optionalMap(inputs, "inputs"));
    }
}
