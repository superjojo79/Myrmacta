package be.myrmacta.infrastructure.document;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.standard.expression.IStandardVariableExpression;
import org.thymeleaf.standard.expression.IStandardVariableExpressionEvaluator;
import org.thymeleaf.standard.expression.StandardExpressionExecutionContext;

/**
 * Évaluateur B2 (DD-THYME-B2 = E) : pas de SpEL, pas d'OGNL. Délègue à
 * {@link SafeThymeleafExpressionSupport}.
 */
final class SafeThymeleafVariableExpressionEvaluator implements IStandardVariableExpressionEvaluator {

    static final SafeThymeleafVariableExpressionEvaluator INSTANCE =
            new SafeThymeleafVariableExpressionEvaluator(null);

    private final ExpressionLimits limits;

    SafeThymeleafVariableExpressionEvaluator(ExpressionLimits limits) {
        this.limits = limits;
    }

    boolean limited() {
        return limits != null;
    }

    @Override
    public Object evaluate(
            IExpressionContext context,
            IStandardVariableExpression expression,
            StandardExpressionExecutionContext expContext) {
        String raw = expression.getExpression();
        if (raw == null || raw.isBlank()) {
            throw new TemplateProcessingException("Unsupported Thymeleaf expression: " + raw);
        }
        return SafeThymeleafExpressionSupport.evaluate(context, raw, limits);
    }
}
