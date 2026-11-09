package be.myrmacta.infrastructure.document;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressionExecutionContext;
import org.thymeleaf.standard.expression.StandardExpressionParser;

/**
 * Analyseur qui n'est pas un {@link StandardExpressionParser} : Thymeleaf désactive alors le pré-traitement
 * {@code __…__} (issue #43). Chaque expression vérifie l'échéance du rendu.
 */
final class GuardedExpressionParser implements IStandardExpressionParser {

    private final StandardExpressionParser delegate = new StandardExpressionParser();

    @Override
    public IStandardExpression parseExpression(IExpressionContext context, String input) {
        IStandardExpression parsed = delegate.parseExpression(context, input);
        return parsed == null ? null : new GuardedExpression(parsed);
    }

    private record GuardedExpression(IStandardExpression delegate) implements IStandardExpression {

        @Override
        public String getStringRepresentation() {
            return delegate.getStringRepresentation();
        }

        @Override
        public Object execute(IExpressionContext context) {
            ExpressionLimits.checkDeadline();
            return delegate.execute(context);
        }

        @Override
        public Object execute(IExpressionContext context, StandardExpressionExecutionContext expContext) {
            ExpressionLimits.checkDeadline();
            return delegate.execute(context, expContext);
        }
    }
}