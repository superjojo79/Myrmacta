package com.flowforge.domain.expression.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.expression.model.ExpressionResult;
import com.flowforge.domain.expression.port.in.EvaluateExpressionUseCase;
import com.flowforge.domain.expression.port.out.ExpressionEnginePort;
import java.util.Map;
import java.util.Objects;

public final class EvaluateExpressionUseCaseImpl implements EvaluateExpressionUseCase {

    private final ExpressionEnginePort engine;
    private final TenantAdminGuard guard;

    public EvaluateExpressionUseCaseImpl(ExpressionEnginePort engine, TenantAdminGuard guard) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @Override
    public ExpressionResult evaluate(AccessContext.User actor, String expression, Map<String, Object> context) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        return engine.evaluate(expression, context == null ? Map.of() : context);
    }
}
