package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GetDossierTypeTool {

    private final GetDossierTypeUseCase getDossierTypeUseCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;

    public GetDossierTypeTool(
            GetDossierTypeUseCase getDossierTypeUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory) {
        this.getDossierTypeUseCase = Objects.requireNonNull(getDossierTypeUseCase, "getDossierTypeUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "get_dossier_type",
            description = "Get a dossier type's schema (field definitions, retention/SLA policy) by its code.")
    public DossierType getDossierType(
            @McpToolParam(required = true, description = "Dossier type code") String code) {
        return getDossierTypeUseCase.getByCode(tenantProperties.id(), code, accessContextFactory.currentActor());
    }
}
