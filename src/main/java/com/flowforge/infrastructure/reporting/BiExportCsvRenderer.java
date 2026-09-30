package com.flowforge.infrastructure.reporting;

import com.flowforge.domain.dossier.DossierFieldRedactor;
import com.flowforge.domain.dossier.DossierFieldCsv;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.port.out.DossierExportPort;
import com.flowforge.domain.dossier.port.out.DossierSearchPort;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.reporting.model.BiExportKind;
import com.flowforge.domain.reporting.model.SlaReport;
import com.flowforge.domain.reporting.port.in.GetSlaReportUseCase;
import com.flowforge.domain.reporting.port.out.BiExportContentPort;
import com.flowforge.infrastructure.web.csv.CsvWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BiExportCsvRenderer implements BiExportContentPort {

    static final List<String> FIXED_COLUMNS =
            List.of("reference", "label", "status", "createdAt", "updatedAt", "closedAt");

    private final DossierTypeRepository typeRepository;
    private final DossierExportPort exportPort;
    private final GetSlaReportUseCase getSlaReportUseCase;

    public BiExportCsvRenderer(
            DossierTypeRepository typeRepository,
            DossierExportPort exportPort,
            GetSlaReportUseCase getSlaReportUseCase) {
        this.typeRepository = typeRepository;
        this.exportPort = exportPort;
        this.getSlaReportUseCase = getSlaReportUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BiExportPayload> render(String tenantId, Instant generatedAt) {
        String stamp = generatedAt.toString().replace(':', '-');
        List<BiExportPayload> payloads = new ArrayList<>();
        for (DossierType type : typeRepository.findAll(tenantId)) {
            payloads.add(new BiExportPayload(
                    BiExportKind.DOSSIERS,
                    type.code(),
                    "dossiers-" + type.code() + "-" + stamp + ".csv",
                    dossierCsv(tenantId, type)));
        }
        payloads.add(new BiExportPayload(
                BiExportKind.SLA, null, "sla-report-" + stamp + ".csv", slaCsv(tenantId)));
        return List.copyOf(payloads);
    }

    private byte[] dossierCsv(String tenantId, DossierType type) {
        List<FieldDefinition> definitions = exportedDefinitions(type.fieldDefinitions());
        StringBuilder csv = new StringBuilder();
        List<String> columns = new ArrayList<>(FIXED_COLUMNS);
        for (FieldDefinition definition : definitions) {
            columns.addAll(DossierFieldCsv.headers(definition));
        }
        csv.append(CsvWriter.writeRow(columns));
        try (Stream<Dossier> dossiers = exportPort.streamForExport(
                tenantId, DossierSearchPort.Filter.restricted(null, Set.of(type.code()), null))) {
            dossiers.forEach(dossier -> csv.append(CsvWriter.writeRow(row(dossier, definitions))));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static List<FieldDefinition> exportedDefinitions(List<FieldDefinition> definitions) {
        List<FieldDefinition> exported = new ArrayList<>();
        for (FieldDefinition definition : definitions) {
            if (!definition.isSensitive()) {
                exported.add(definition);
            }
        }
        return List.copyOf(exported);
    }

    private static List<String> row(Dossier dossier, List<FieldDefinition> definitions) {
        Map<String, Object> fields = DossierFieldRedactor.withoutSensitiveKeys(dossier, definitions);
        List<String> values = new ArrayList<>(List.of(
                dossier.reference(),
                dossier.label(),
                dossier.status().name(),
                dossier.createdAt().toString(),
                dossier.updatedAt() == null ? "" : dossier.updatedAt().toString(),
                dossier.closedAt() == null ? "" : dossier.closedAt().toString()));
        for (FieldDefinition definition : definitions) {
            values.addAll(DossierFieldCsv.cells(definition, fields.get(definition.name())));
        }
        return values;
    }

    private byte[] slaCsv(String tenantId) {
        SlaReport report = getSlaReportUseCase.get(
                new GetSlaReportUseCase.Query(tenantId, null, null, null), AccessContext.system("bi-export"));
        StringBuilder csv = new StringBuilder();
        csv.append("# compliance=")
                .append(report.compliancePercent())
                .append("; target=")
                .append(report.complianceTargetPercent())
                .append("; overdue=")
                .append(report.overdueCount())
                .append("; meanDays=")
                .append(report.meanProcessingDays())
                .append("\r\n");
        csv.append(CsvWriter.writeRow(List.of(
                "type", "workflow", "volume", "meanDays", "slaPercent", "producedDocs", "volumeTrendPercent")));
        for (SlaReport.TypeBreakdown row : report.types()) {
            csv.append(CsvWriter.writeRow(List.of(
                    row.typeLabel(),
                    row.workflowKey(),
                    Long.toString(row.volume()),
                    row.meanDays() == null ? "" : row.meanDays().toString(),
                    row.slaPercent() == null ? "" : row.slaPercent().toString(),
                    Long.toString(row.producedDocCount()),
                    row.volumeTrendPercent() == null ? "" : row.volumeTrendPercent().toString())));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
