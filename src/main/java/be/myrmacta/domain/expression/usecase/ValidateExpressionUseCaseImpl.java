package be.myrmacta.domain.expression.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.expression.port.in.ValidateExpressionUseCase;
import be.myrmacta.domain.expression.port.out.ExpressionEnginePort;
import java.util.Objects;

public final class ValidateExpressionUseCaseImpl implements ValidateExpressionUseCase {

    private final ExpressionEnginePort engine;
    private final TenantAdminGuard guard;

    public ValidateExpressionUseCaseImpl(ExpressionEnginePort engine, TenantAdminGuard guard) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @Override
    public void validate(AccessContext.User actor, String expression) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        engine.validate(expression);
    }
}
