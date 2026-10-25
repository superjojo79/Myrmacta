package be.myrmacta.infrastructure.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierExportPort;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.reporting.model.BiExportKind;
import be.myrmacta.domain.reporting.model.SlaReport;
import be.myrmacta.domain.reporting.port.in.GetSlaReportUseCase;
import be.myrmacta.domain.reporting.port.out.BiExportContentPort;
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

    /** TODO §76 (D1, revue adversariale) : montant et référence aplatis ; groupe et montant sensibles absents de l'export BI. */
    @Test
    void render_flattensStructuredFieldsAndNeverExportsASensitiveGroupOrMoney() {
        DossierTypeRepository types = mock(DossierTypeRepository.class);
        DossierExportPort exportPort = mock(DossierExportPort.class);
        GetSlaReportUseCase sla = mock(GetSlaReportUseCase.class);
        FieldDefinition price = new FieldDefinition("prix", "Prix", FieldType.MONEY, false, null, List.of(), null, false);
        FieldDefinition supplier = new FieldDefinition("fournisseur", "F", FieldType.EXTERNAL_REF, false, null, List.of(), null, false);
        FieldDefinition lines = new FieldDefinition("lignes", "L", FieldType.REPEATABLE_GROUP, false, null, List.of(), null,
                false, List.of(new FieldDefinition("poste", "P", FieldType.STRING, false, null, List.of(), null)), null, null);
        FieldDefinition secretMoney = new FieldDefinition("salaire", "S", FieldType.MONEY, false, null, List.of(), null, true);
        FieldDefinition secretGroup = new FieldDefinition("budget", "B", FieldType.REPEATABLE_GROUP, false, null, List.of(), null,
                true, List.of(new FieldDefinition("poste", "P", FieldType.STRING, false, null, List.of(), null)), null, null);
        when(types.findAll(TENANT)).thenReturn(List.of(new DossierType(UUID.randomUUID(), TENANT, "CLAIM", "Sinistre", "wf", true,
                false, List.of(price, supplier, lines, secretMoney, secretGroup), null, false, 24, "admin", "admin")));
        Instant created = NOW.minusSeconds(3600);
        Dossier dossier = new Dossier(UUID.randomUUID(), TENANT, "DOS-9", "CLAIM", "x", DossierStatus.OUVERT,
                Map.of("prix", Map.of("amount", "12.50", "currency", "EUR"),
                        "fournisseur", Map.of("system", "d365fo", "id", "V-1"),
                        "lignes", List.of(Map.of("poste", "Toiture")),
                        "salaire", Map.of("amount", "4242.00", "currency", "EUR"),
                        "budget", List.of(Map.of("poste", "CONFIDENTIEL-XYZ"))),
                null, 0L, "admin", created, "admin", created, null, null, null);
        when(exportPort.streamForExport(eq(TENANT), any())).thenReturn(Stream.of(dossier));
        when(sla.get(any(), any())).thenReturn(new SlaReport(NOW.minusSeconds(86_400), NOW, 95, 80.0, 1L, 2.0, null, null,
                List.of(), List.of(), List.of(), List.of()));

        String csv = new String(new BiExportCsvRenderer(types, exportPort, sla).render(TENANT, NOW).get(0).content());

        assertThat(csv).contains("prix.amount,prix.currency,fournisseur.system,fournisseur.id,fournisseur.label,lignes");
        assertThat(csv).contains("12.50,EUR,d365fo,V-1,");
        assertThat(csv).contains("Toiture");
        assertThat(csv).doesNotContain("salaire").doesNotContain("budget").doesNotContain("4242").doesNotContain("CONFIDENTIEL-XYZ");
    }
}
