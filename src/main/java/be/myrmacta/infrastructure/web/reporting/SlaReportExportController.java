package be.myrmacta.infrastructure.web.reporting;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.reporting.model.SlaReport;
import be.myrmacta.domain.reporting.port.in.GetSlaReportUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.web.csv.CsvWriter;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlaReportExportController {

    private final TenantProperties tenantProperties;
    private final GetSlaReportUseCase getSlaReportUseCase;
    private final AccessContextFactory accessContextFactory;

    public SlaReportExportController(
            TenantProperties tenantProperties,
            GetSlaReportUseCase getSlaReportUseCase,
            AccessContextFactory accessContextFactory) {
        this.tenantProperties = tenantProperties;
        this.getSlaReportUseCase = getSlaReportUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping(path = "/api/reports/sla.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN','DOSSIER_MANAGER')")
    public void exportCsv(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String dossierTypeCode,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException {
        AccessContext ctx = accessContextFactory.user(authentication);
        SlaReport report = getSlaReportUseCase.get(
                new GetSlaReportUseCase.Query(tenantProperties.id(), from, to, dossierTypeCode), ctx);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"sla-report.csv\"");
        PrintWriter writer = response.getWriter();
        writer.write("# compliance="
                + report.compliancePercent()
                + "; target="
                + report.complianceTargetPercent()
                + "; overdue="
                + report.overdueCount()
                + "; meanDays="
                + report.meanProcessingDays()
                + "\r\n");
        writer.write(CsvWriter.writeRow(List.of(
                "type", "workflow", "volume", "meanDays", "slaPercent", "producedDocs", "volumeTrendPercent")));
        for (SlaReport.TypeBreakdown row : report.types()) {
            writer.write(CsvWriter.writeRow(List.of(
                    row.typeLabel(),
                    row.workflowKey(),
                    Long.toString(row.volume()),
                    row.meanDays() == null ? "" : row.meanDays().toString(),
                    row.slaPercent() == null ? "" : row.slaPercent().toString(),
                    Long.toString(row.producedDocCount()),
                    row.volumeTrendPercent() == null ? "" : row.volumeTrendPercent().toString())));
        }
        writer.flush();
    }
}
