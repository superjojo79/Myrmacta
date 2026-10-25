package be.myrmacta.domain.workflow.palette.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.UnknownBlockSourceException;
import be.myrmacta.domain.workflow.palette.port.in.ListBlockReferencesUseCase;
import be.myrmacta.domain.workflow.palette.port.out.BlockReference;
import be.myrmacta.domain.workflow.palette.port.out.BlockReferenceLookupPort;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ListBlockReferencesUseCaseImpl implements ListBlockReferencesUseCase {

    /**
     * Sources servies ici : celles dont l'API d'administration est réservée à {@code TENANT_ADMIN}, et
     * {@code dmnTables} qui n'a pas d'API REST de liste (seul l'outil MCP {@code list_decision_tables}). Les autres
     * restent lues à leur API.
     */
    static final Set<String> SERVED_SOURCES = Set.of("documentTemplates", "connectorInstances", "notificationWebhooks", "dmnTables");

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
