package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.in.GetDossierTypeUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
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
