package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.model.DossierPage;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.SearchDossiersQuery;
import com.flowforge.domain.dossier.port.in.SearchDossiersUseCase;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class SearchDossiersTool {

    private final SearchDossiersUseCase searchDossiersUseCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;

    public SearchDossiersTool(
            SearchDossiersUseCase searchDossiersUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory) {
        this.searchDossiersUseCase = Objects.requireNonNull(searchDossiersUseCase, "searchDossiersUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "search_dossiers",
            description = "Search dossier summaries by status, dossier type code, reference prefix and/or dynamic-field filters, "
                    + "paginated. fieldFilters (needs dossierTypeCode; at most 5; only fields declared searchable in the type — see "
                    + "get_dossier_type): keys 'name' (equals), 'name.in' (comma-separated), 'name.gte' / 'name.lte' (number, ISO "
                    + "date), 'name.prefix' (text, case-insensitive). Example {\"contrat\":\"C-42\",\"montant.gte\":\"5000\"}.")
    public DossierPage searchDossiers(
            @McpToolParam(required = false, description = "Dossier status filter") String status,
            @McpToolParam(required = false, description = "Dossier type code filter") String dossierTypeCode,
            @McpToolParam(required = false, description = "Reference prefix filter") String referencePrefix,
            @McpToolParam(required = false, description = "Dynamic-field filters, see the tool description")
                    java.util.Map<String, Object> fieldFilters,
            @McpToolParam(required = false, description = "Zero-based page index, default 0") Integer page,
            @McpToolParam(required = false, description = "Page size, 1-100, default 20") Integer size) {
        DossierStatus statusFilter = status == null || status.isBlank() ? null : DossierStatus.valueOf(status);
        java.util.Map<String, String> filterParams = new java.util.LinkedHashMap<>();
        if (fieldFilters != null) {
            fieldFilters.forEach((k, v) -> filterParams.put("f." + k, v == null ? null : String.valueOf(v)));
        }
        SearchDossiersQuery query = SearchDossiersQuery.of(
                statusFilter,
                dossierTypeCode,
                referencePrefix,
                page == null ? 0 : page,
                size == null ? PageQuery.DEFAULT_SIZE : size,
                com.flowforge.domain.dossier.model.FieldConditionParser.parse(filterParams));
        return searchDossiersUseCase.search(tenantProperties.id(), query, accessContextFactory.currentActor());
    }
}
