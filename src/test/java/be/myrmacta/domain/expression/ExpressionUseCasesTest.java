package be.myrmacta.domain.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.expression.model.ExpressionResult;
import be.myrmacta.domain.expression.port.out.ExpressionEnginePort;
import be.myrmacta.domain.expression.usecase.EvaluateExpressionUseCaseImpl;
import be.myrmacta.domain.expression.usecase.ValidateExpressionUseCaseImpl;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** LC2 (TODO §75) — gardes de rôle des cas d'usage (évalués avant tout appel au moteur). */
class ExpressionUseCasesTest {

    private final ExpressionEnginePort engine = mock(ExpressionEnginePort.class);
    private final TenantAdminGuard guard = new TenantAdminGuard();
    private final EvaluateExpressionUseCaseImpl evaluate = new EvaluateExpressionUseCaseImpl(engine, guard);
    private final ValidateExpressionUseCaseImpl validate = new ValidateExpressionUseCaseImpl(engine, guard);

    private static AccessContext.User user(String role) {
        return AccessContext.user("u", Set.of(role));
    }

    @Test
    void adminsAndTheServiceRoleMayEvaluateAndValidate() {
        when(engine.evaluate("1+1", Map.of())).thenReturn(ExpressionResult.of(2L));
        for (String role : new String[] {"TENANT_ADMIN", "WORKFLOW_ADMIN", "API_SERVICE"}) {
            assertThat(evaluate.evaluate(user(role), "1+1", null).value()).isEqualTo(2L);
            validate.validate(user(role), "1+1");
        }
    }

    @Test
    void otherRolesAreRefusedBeforeTheEngineIsTouched() {
        for (String role : new String[] {"DOSSIER_MANAGER", "DOSSIER_VIEWER"}) {
            assertThatThrownBy(() -> evaluate.evaluate(user(role), "1+1", Map.of())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> validate.validate(user(role), "1+1")).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(engine);
    }

    @Test
    void theContextIsForwardedAsIs() {
        Map<String, Object> ctx = Map.of("a", 1);
        evaluate.evaluate(user("TENANT_ADMIN"), "a", ctx);
        verify(engine).evaluate("a", ctx);
    }
}
