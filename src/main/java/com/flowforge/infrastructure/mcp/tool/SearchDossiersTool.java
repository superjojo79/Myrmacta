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
            description = "Search dossier summaries by status, dossier type code and/or reference prefix, paginated.")
    public DossierPage searchDossiers(
            @McpToolParam(required = false, description = "Dossier status filter") String status,
            @McpToolParam(required = false, description = "Dossier type code filter") String dossierTypeCode,
            @McpToolParam(required = false, description = "Reference prefix filter") String referencePrefix,
            @McpToolParam(required = false, description = "Zero-based page index, default 0") Integer page,
            @McpToolParam(required = false, description = "Page size, 1-100, default 20") Integer size) {
        DossierStatus statusFilter = status == null || status.isBlank() ? null : DossierStatus.valueOf(status);
        SearchDossiersQuery query = SearchDossiersQuery.of(
                statusFilter,
                dossierTypeCode,
                referencePrefix,
                page == null ? 0 : page,
                size == null ? PageQuery.DEFAULT_SIZE : size);
        return searchDossiersUseCase.search(tenantProperties.id(), query, accessContextFactory.currentActor());
    }
}
