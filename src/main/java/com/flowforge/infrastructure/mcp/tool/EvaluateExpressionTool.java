package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.expression.model.ExpressionResult;
import com.flowforge.domain.expression.port.in.EvaluateExpressionUseCase;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class EvaluateExpressionTool {

    private final EvaluateExpressionUseCase evaluateUseCase;
    private final McpAccessContextFactory accessContextFactory;

    public EvaluateExpressionTool(EvaluateExpressionUseCase evaluateUseCase, McpAccessContextFactory accessContextFactory) {
        this.evaluateUseCase = Objects.requireNonNull(evaluateUseCase, "evaluateUseCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "evaluate_expression",
            description = "Dry-run a business expression against a sample JSON context you provide (nothing from the "
                    + "tenant is read, no side effect). Returns {defined, value}. The context is exposed as the root "
                    + "document (e.g. dossier.fields.montant, connectorResult.data); `now` is the current UTC time. "
                    + "Same limits as at runtime.")
    public Map<String, Object> evaluateExpression(
            @McpToolParam(required = true, description = "The expression text") String expression,
            @McpToolParam(required = false, description = "Sample JSON context (root document)")
                    Map<String, Object> context) {
        ExpressionResult result = evaluateUseCase.evaluate(
                accessContextFactory.currentActor(), expression, McpArgs.optionalMap(context, "context"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("defined", result.defined());
        out.put("value", result.value());
        return out;
    }
}
