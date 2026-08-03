package com.flowforge.domain.dossier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
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
}
