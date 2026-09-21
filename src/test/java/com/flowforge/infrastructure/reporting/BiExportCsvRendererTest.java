package com.flowforge.infrastructure.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.out.DossierExportPort;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.reporting.model.BiExportKind;
import com.flowforge.domain.reporting.model.SlaReport;
import com.flowforge.domain.reporting.port.in.GetSlaReportUseCase;
import com.flowforge.domain.reporting.port.out.BiExportContentPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BiExportCsvRendererTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-08-01T10:15:30Z");

    @Test
    void render_stripsSensitiveKeysAndBuildsSlaCsvAsSystem() {
        DossierTypeRepository types = mock(DossierTypeRepository.class);
        DossierExportPort exportPort = mock(DossierExportPort.class);
        GetSlaReportUseCase sla = mock(GetSlaReportUseCase.class);
        FieldDefinition secret = new FieldDefinition(
                "iban", "IBAN", FieldType.STRING, false, null, List.of(), null, true);
        FieldDefinition city = new FieldDefinition(
                "city", "Ville", FieldType.STRING, false, null, List.of(), null, false);
        when(types.findAll(TENANT)).thenReturn(List.of(new DossierType(
                UUID.randomUUID(),
                TENANT,
                "CLAIM",
                "Sinistre",
                "wf",
                true,
                false,
                List.of(secret, city),
                null,
                false,
                24,
                "admin",
                "admin")));
        Instant created = NOW.minusSeconds(3600);
        Dossier dossier = new Dossier(
                UUID.randomUUID(),
                TENANT,
                "DOS-9",
                "CLAIM",
                "=cmd",
                DossierStatus.OUVERT,
                Map.of("iban", "TOP-SECRET", "city", "Lyon"),
                null,
                0L,
                "admin",
                created,
                "admin",
                created,
                null,
                null,
                null);
        when(exportPort.streamForExport(eq(TENANT), any())).thenReturn(Stream.of(dossier));
        when(sla.get(any(), any())).thenReturn(new SlaReport(
                NOW.minusSeconds(86_400),
                NOW,
                95,
                80.0,
                1L,
                2.0,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(new SlaReport.TypeBreakdown("CLAIM", "=CMD", "wf", 3, 2.0, 80.0, 1, null))));

        List<BiExportContentPort.BiExportPayload> payloads =
                new BiExportCsvRenderer(types, exportPort, sla).render(TENANT, NOW);

        assertThat(payloads).hasSize(2);
        BiExportContentPort.BiExportPayload dossiers = payloads.get(0);
        assertThat(dossiers.kind()).isEqualTo(BiExportKind.DOSSIERS);
        assertThat(dossiers.fileName()).isEqualTo("dossiers-CLAIM-2026-08-01T10-15-30Z.csv");
        String dossierCsv = new String(dossiers.content());
        assertThat(dossierCsv).contains("reference,label,status,createdAt,updatedAt,closedAt,city");
        assertThat(dossierCsv).doesNotContain("iban");
        assertThat(dossierCsv).contains("Lyon");
        assertThat(dossierCsv).contains("'=cmd");
        assertThat(dossierCsv).doesNotContain("TOP-SECRET");

        String slaCsv = new String(payloads.get(1).content());
        assertThat(payloads.get(1).kind()).isEqualTo(BiExportKind.SLA);
        assertThat(payloads.get(1).dossierTypeCode()).isNull();
        assertThat(slaCsv).contains("# compliance=80.0; target=95; overdue=1; meanDays=2.0");
        assertThat(slaCsv).contains("'=CMD");

        ArgumentCaptor<AccessContext> ctx = ArgumentCaptor.forClass(AccessContext.class);
        verify(sla).get(any(), ctx.capture());
        assertThat(ctx.getValue()).isInstanceOf(AccessContext.System.class);
    }
}
