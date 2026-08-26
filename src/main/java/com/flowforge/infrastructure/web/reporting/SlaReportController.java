package com.flowforge.infrastructure.web.reporting;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.SlaReport;
import com.flowforge.domain.reporting.model.SlaSummary;
import com.flowforge.domain.reporting.port.in.GetSlaReportUseCase;
import com.flowforge.domain.reporting.port.in.GetSlaSummaryUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlaReportController {

    private final TenantProperties tenantProperties;
    private final GetSlaReportUseCase getSlaReportUseCase;
    private final GetSlaSummaryUseCase getSlaSummaryUseCase;
    private final AccessContextFactory accessContextFactory;

    public SlaReportController(
            TenantProperties tenantProperties,
            GetSlaReportUseCase getSlaReportUseCase,
            GetSlaSummaryUseCase getSlaSummaryUseCase,
            AccessContextFactory accessContextFactory) {
        this.tenantProperties = tenantProperties;
        this.getSlaReportUseCase = getSlaReportUseCase;
        this.getSlaSummaryUseCase = getSlaSummaryUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping("/api/reports/sla")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN','DOSSIER_MANAGER')")
    public SlaReport get(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String dossierTypeCode,
            Authentication authentication) {
        AccessContext ctx = accessContextFactory.user(authentication);
        return getSlaReportUseCase.get(
                new GetSlaReportUseCase.Query(tenantProperties.id(), from, to, dossierTypeCode), ctx);
    }

    @GetMapping("/api/reports/sla/summary")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN','DOSSIER_MANAGER')")
    public SlaSummary summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String dossierTypeCode,
            @RequestParam(required = false) String groupBy,
            Authentication authentication) {
        AccessContext ctx = accessContextFactory.user(authentication);
        return getSlaSummaryUseCase.get(
                new GetSlaSummaryUseCase.Query(
                        tenantProperties.id(), from, to, dossierTypeCode, groupBy),
                ctx);
    }
}
