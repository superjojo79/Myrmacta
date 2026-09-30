package com.flowforge.domain.workflow.palette.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.UnknownBlockSourceException;
import com.flowforge.domain.workflow.palette.port.in.ListBlockReferencesUseCase;
import com.flowforge.domain.workflow.palette.port.out.BlockReference;
import com.flowforge.domain.workflow.palette.port.out.BlockReferenceLookupPort;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ListBlockReferencesUseCaseImpl implements ListBlockReferencesUseCase {

    /** Sources dont l'API d'administration est réservée à {@code TENANT_ADMIN} : les autres restent lues à leur API. */
    static final Set<String> SERVED_SOURCES = Set.of("documentTemplates", "connectorInstances", "notificationWebhooks");

    private final String tenantId;
    private final BlockReferenceLookupPort lookup;
    private final TenantAdminGuard guard;

    public ListBlockReferencesUseCaseImpl(String tenantId, BlockReferenceLookupPort lookup, TenantAdminGuard guard) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @Override
    public List<BlockReference> list(String source, AccessContext.User actor) {
        guard.requireTenantOrWorkflowAdmin(actor);
        if (source == null || !SERVED_SOURCES.contains(source)) {
            throw new UnknownBlockSourceException(source);
        }
        return lookup.list(tenantId, source);
    }
}
