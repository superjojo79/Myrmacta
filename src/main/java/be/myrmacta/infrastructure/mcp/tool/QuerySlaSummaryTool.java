package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.reporting.model.SlaSummary;
import be.myrmacta.domain.reporting.port.in.GetSlaSummaryUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class QuerySlaSummaryTool {

    private final GetSlaSummaryUseCase getSlaSummaryUseCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;

    public QuerySlaSummaryTool(
            GetSlaSummaryUseCase getSlaSummaryUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory) {
        this.getSlaSummaryUseCase = Objects.requireNonNull(getSlaSummaryUseCase, "getSlaSummaryUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "query_sla_summary",
            description = "SLA summary buckets (volume, on-track/at-risk/breached counts, durations) "
                    + "grouped by dossierTypeCode or status over a date range.")
    public SlaSummary querySlaSummary(
            @McpToolParam(required = false, description = "ISO-8601 instant, default 30 days ago") String from,
            @McpToolParam(required = false, description = "ISO-8601 instant, default now") String to,
            @McpToolParam(required = false, description = "Dossier type code filter") String dossierTypeCode,
            @McpToolParam(required = false, description = "\"dossierTypeCode\" (default) or \"status\"") String groupBy) {
        var query = new GetSlaSummaryUseCase.Query(tenantProperties.id(), from, to, dossierTypeCode, groupBy);
        return getSlaSummaryUseCase.get(query, accessContextFactory.currentActor());
    }
}
