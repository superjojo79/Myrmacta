package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.expression.model.InvalidExpressionException;
import com.flowforge.domain.expression.port.in.ValidateExpressionUseCase;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ValidateExpressionTool {

    private final ValidateExpressionUseCase validateUseCase;
    private final McpAccessContextFactory accessContextFactory;

    public ValidateExpressionTool(ValidateExpressionUseCase validateUseCase, McpAccessContextFactory accessContextFactory) {
        this.validateUseCase = Objects.requireNonNull(validateUseCase, "validateUseCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "validate_expression",
            description = "Check a business expression (JSONata subset: no regex literals, no $eval/$random/$now/$millis; "
                    + "use the `now` context value) without running it. Returns {valid, message, position}. "
                    + "Same limits as at runtime. No side effect.")
    public Map<String, Object> validateExpression(
            @McpToolParam(required = true, description = "The expression text") String expression) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            validateUseCase.validate(accessContextFactory.currentActor(), expression);
            out.put("valid", true);
        } catch (InvalidExpressionException e) {
            out.put("valid", false);
            out.put("message", e.getMessage());
            out.put("position", e.position());
        }
        return out;
    }
}
