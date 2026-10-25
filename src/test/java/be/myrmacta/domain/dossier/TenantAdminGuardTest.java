package be.myrmacta.domain.dossier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantAdminGuardTest {

    private final TenantAdminGuard guard = new TenantAdminGuard();

    @Test
    void tenantAdmin_doesNotThrow() {
        assertThatCode(() -> guard.requireTenantAdmin(AccessContext.user("a", Set.of("TENANT_ADMIN"))))
                .doesNotThrowAnyException();
    }

    @Test
    void workflowAdminAlone_isDenied() {
        assertThatThrownBy(() ->
                        guard.requireTenantAdmin(AccessContext.user("a", Set.of("WORKFLOW_ADMIN"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void dossierManager_isDenied() {
        assertThatThrownBy(() ->
                        guard.requireTenantAdmin(AccessContext.user("a", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userWithoutRoles_isDenied() {
        assertThatThrownBy(() -> guard.requireTenantAdmin(AccessContext.user("a", Set.of())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void apiServiceAlone_isDeniedByRequireTenantAdmin() {
        assertThatThrownBy(() -> guard.requireTenantAdmin(AccessContext.user("svc", Set.of("API_SERVICE"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireTenantAdminOrApiService_tenantAdmin_doesNotThrow() {
        assertThatCode(() -> guard.requireTenantAdminOrApiService(
                        AccessContext.user("a", Set.of("TENANT_ADMIN"))))
                .doesNotThrowAnyException();
    }

    @Test
    void requireTenantAdminOrApiService_apiService_doesNotThrow() {
        assertThatCode(() -> guard.requireTenantAdminOrApiService(
                        AccessContext.user("svc", Set.of("API_SERVICE"))))
                .doesNotThrowAnyException();
    }

    @Test
    void requireTenantAdminOrApiService_workflowAdminAlone_isDenied() {
        assertThatThrownBy(() -> guard.requireTenantAdminOrApiService(
                        AccessContext.user("a", Set.of("WORKFLOW_ADMIN"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireTenantOrWorkflowAdminOrApiService_tenantAdmin_doesNotThrow() {
        assertThatCode(() -> guard.requireTenantOrWorkflowAdminOrApiService(
                        AccessContext.user("a", Set.of("TENANT_ADMIN"))))
                .doesNotThrowAnyException();
    }

    @Test
    void requireTenantOrWorkflowAdminOrApiService_workflowAdmin_doesNotThrow() {
        assertThatCode(() -> guard.requireTenantOrWorkflowAdminOrApiService(
                        AccessContext.user("a", Set.of("WORKFLOW_ADMIN"))))
                .doesNotThrowAnyException();
    }

    @Test
    void requireTenantOrWorkflowAdminOrApiService_apiService_doesNotThrow() {
        assertThatCode(() -> guard.requireTenantOrWorkflowAdminOrApiService(
                        AccessContext.user("svc", Set.of("API_SERVICE"))))
                .doesNotThrowAnyException();
    }

    @Test
    void requireTenantOrWorkflowAdminOrApiService_dossierManager_isDenied() {
        assertThatThrownBy(() -> guard.requireTenantOrWorkflowAdminOrApiService(
                        AccessContext.user("a", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireTenantOrWorkflowAdminOrApiService_noRoles_isDenied() {
        assertThatThrownBy(() -> guard.requireTenantOrWorkflowAdminOrApiService(
                        AccessContext.user("a", Set.of())))
                .isInstanceOf(AccessDeniedException.class);
    }
}
