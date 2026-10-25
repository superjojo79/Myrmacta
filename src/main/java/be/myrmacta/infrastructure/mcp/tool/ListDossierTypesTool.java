package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.in.ListDossierTypesUseCase;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ListDossierTypesTool {

    private final ListDossierTypesUseCase listDossierTypesUseCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;

    public ListDossierTypesTool(
            ListDossierTypesUseCase listDossierTypesUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory) {
        this.listDossierTypesUseCase = Objects.requireNonNull(listDossierTypesUseCase, "listDossierTypesUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "list_dossier_types",
            description = "List dossier types visible to the caller (active and inactive), paginated.")
    public PageResult<DossierType> listDossierTypes(
            @McpToolParam(required = false, description = "Zero-based page index, default 0") Integer page,
            @McpToolParam(required = false, description = "Page size, 1-100, default 20") Integer size) {
        PageQuery query = page == null && size == null
                ? PageQuery.defaults()
                : PageQuery.of(page == null ? 0 : page, size == null ? PageQuery.DEFAULT_SIZE : size);
        return listDossierTypesUseCase.list(tenantProperties.id(), accessContextFactory.currentActor(), query);
    }
}
