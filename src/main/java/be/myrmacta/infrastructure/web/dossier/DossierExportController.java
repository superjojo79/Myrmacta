package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.dossier.DossierFieldCsv;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.ExportDossiersQuery;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.port.in.ExportDossiersUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
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
            columns.addAll(DossierFieldCsv.headers(definition));
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
            values.addAll(DossierFieldCsv.cells(definition, dossier.fields().get(definition.name())));
        }
        return values;
    }
}
