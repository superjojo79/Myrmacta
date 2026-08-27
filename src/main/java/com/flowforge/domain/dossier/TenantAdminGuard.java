package com.flowforge.domain.dossier;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import java.util.Objects;

/** Domain guard — requires {@code TENANT_ADMIN} on the actor. */
public final class TenantAdminGuard {

    public void requireTenantAdmin(AccessContext.User actor) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.hasRole("TENANT_ADMIN")) {
            throw new AccessDeniedException("TENANT_ADMIN role required");
        }
    }

    public void requireTenantAdminOrApiService(AccessContext.User actor) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.hasRole("TENANT_ADMIN") && !actor.hasRole("API_SERVICE")) {
            throw new AccessDeniedException("TENANT_ADMIN or API_SERVICE role required");
        }
    }
}
