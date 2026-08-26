package com.flowforge.infrastructure.web.reporting;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.SlaReport;
import com.flowforge.domain.reporting.port.in.GetSlaReportUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlaReportController {

    private final TenantProperties tenantProperties;
    private final GetSlaReportUseCase getSlaReportUseCase;
    private final AccessContextFactory accessContextFactory;

    public SlaReportController(
            TenantProperties tenantProperties,
            GetSlaReportUseCase getSlaReportUseCase,
            AccessContextFactory accessContextFactory) {
        this.tenantProperties = tenantProperties;
        this.getSlaReportUseCase = getSlaReportUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping("/api/reports/sla")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN','DOSSIER_MANAGER')")
    public SlaReport get(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String dossierTypeCode,
            Authentication authentication) {
        AccessContext ctx = accessContextFactory.user(authentication);
        return getSlaReportUseCase.get(
                new GetSlaReportUseCase.Query(tenantProperties.id(), from, to, dossierTypeCode), ctx);
    }
}
