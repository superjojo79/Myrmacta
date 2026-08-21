package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.ExportDossiersQuery;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.port.in.ExportDossiersUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dedicated GET entry point for {@code export.csv} — kept out of {@link DossierController} so the
 * response can be written incrementally to {@link HttpServletResponse} (streamed CSV) instead of
 * returning a single body object like the other endpoints. {@code @Transactional} here (not on
 * the use case or the port, both domain/infra POJOs) is what keeps the underlying JPA cursor
 * ({@code DossierExportPort}) open for the whole write — see the port's javadoc.
 */
@RestController
@RequestMapping("/api/dossiers")
public class DossierExportController {

    private static final List<String> FIXED_COLUMNS =
            List.of("reference", "label", "status", "createdAt", "updatedAt", "closedAt");

    private final TenantProperties tenantProperties;
    private final ExportDossiersUseCase exportDossiersUseCase;
    private final AccessContextFactory accessContextFactory;

    public DossierExportController(
            TenantProperties tenantProperties,
            ExportDossiersUseCase exportDossiersUseCase,
            AccessContextFactory accessContextFactory) {
        this.tenantProperties = tenantProperties;
        this.exportDossiersUseCase = exportDossiersUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping(path = "/export.csv", produces = "text/csv")
    @Transactional(readOnly = true)
    public void exportCsv(
            @RequestParam String dossierTypeCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reference,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        AccessContext.User actor = accessContextFactory.user(authentication);
        ExportDossiersQuery query = new ExportDossiersQuery(
                dossierTypeCode, DossierListQueryParser.parseStatus(status), reference);
        ExportDossiersUseCase.ExportResult result =
                exportDossiersUseCase.export(tenantProperties.id(), query, actor);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"dossiers-" + dossierTypeCode + ".csv\"");

        List<String> columns = new ArrayList<>(FIXED_COLUMNS);
        for (FieldDefinition definition : result.fieldDefinitions()) {
            columns.add(definition.name());
        }

        PrintWriter writer = response.getWriter();
        writer.write(CsvWriter.writeRow(columns));
        try (Stream<Dossier> dossiers = result.dossiers()) {
            dossiers.forEach(dossier -> writer.write(CsvWriter.writeRow(row(dossier, result.fieldDefinitions()))));
        }
        writer.flush();
    }

    private static List<String> row(Dossier dossier, List<FieldDefinition> fieldDefinitions) {
        List<String> values = new ArrayList<>(List.of(
                dossier.reference(),
                dossier.label(),
                dossier.status().name(),
                dossier.createdAt().toString(),
                dossier.updatedAt().toString(),
                dossier.closedAt() == null ? "" : dossier.closedAt().toString()));
        for (FieldDefinition definition : fieldDefinitions) {
            Object value = dossier.fields().get(definition.name());
            values.add(value == null ? "" : String.valueOf(value));
        }
        return values;
    }
}
