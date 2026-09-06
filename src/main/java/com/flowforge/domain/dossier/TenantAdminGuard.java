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

    /**
     * Shared beyond the {@code dossier} bounded context by {@code domain.connector} and
     * {@code domain.workflow} (extension-mcp-flux-metier, D2) — no ArchUnit rule isolates domain
     * sub-packages from one another, and {@code domain.workflow} already depends on
     * {@code domain.dossier.model.FieldDefinition} (TODO §55), so this is not a new precedent.
     */
    public void requireTenantOrWorkflowAdminOrApiService(AccessContext.User actor) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.hasRole("TENANT_ADMIN") && !actor.hasRole("WORKFLOW_ADMIN") && !actor.hasRole("API_SERVICE")) {
            throw new AccessDeniedException("TENANT_ADMIN, WORKFLOW_ADMIN or API_SERVICE role required");
        }
    }
}
