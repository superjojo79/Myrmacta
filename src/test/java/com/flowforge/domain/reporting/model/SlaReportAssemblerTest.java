package com.flowforge.domain.reporting.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.flowforge.domain.dossier.model.DossierStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SlaReportAssemblerTest {

    private static final Instant FROM = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-03-16T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-03-15T00:00:00Z");
    private static final Instant PREV_FROM = Instant.parse("2025-12-16T00:00:00Z");

    private static final TypeSlaMeta SINISTRE =
            new TypeSlaMeta("SIN", "Sinistre", "instruction-sinistre", 48);
    private static final TypeSlaMeta RECLAMATION =
            new TypeSlaMeta("REC", "Réclamation", "traitement-reclamation", null);

    @Test
    void typesWithoutTarget_areExcludedFromCompliance() {
        List<DossierSlaRow> current = List.of(
                closed("SIN", "2026-02-01T00:00:00Z", "2026-02-02T00:00:00Z"),
                closed("REC", "2026-02-01T00:00:00Z", "2026-02-10T00:00:00Z"));

        SlaReport report = assemble(current, List.of(), Map.of(), List.of(), List.of());

        assertThat(report.compliancePercent()).isEqualTo(100.0);
        assertThat(report.overdueCount()).isZero();
        assertThat(report.types())
                .anySatisfy(row -> {
                    if (row.typeCode().equals("REC")) {
                        assertThat(row.slaPercent()).isNull();
                    }
                });
    }

    @Test
    void overdue_whenElapsedExceedsTarget() {
        List<DossierSlaRow> current = List.of(
                closed("SIN", "2026-02-01T00:00:00Z", "2026-02-04T00:00:00Z"));

        SlaReport report = assemble(current, List.of(), Map.of(), List.of(), List.of());

        assertThat(report.overdueCount()).isEqualTo(1);
        assertThat(report.compliancePercent()).isEqualTo(0.0);
    }

    @Test
    void emptyCohort_yieldsNullComplianceAndZeroOverdue() {
        SlaReport report = assemble(List.of(), List.of(), Map.of(), List.of(), List.of());

        assertThat(report.compliancePercent()).isNull();
        assertThat(report.overdueCount()).isZero();
        assertThat(report.meanProcessingDays()).isNull();
        assertThat(report.medianProcessingDays()).isNull();
        assertThat(report.meanDeltaVsPreviousDays()).isNull();
        assertThat(report.typeShares()).isEmpty();
        assertThat(report.types()).isEmpty();
    }

    @Test
    void median_oddAndEven() {
        List<DossierSlaRow> odd = List.of(
                closed("SIN", "2026-02-01T00:00:00Z", "2026-02-02T00:00:00Z"),
                closed("SIN", "2026-02-01T00:00:00Z", "2026-02-03T00:00:00Z"),
                closed("SIN", "2026-02-01T00:00:00Z", "2026-02-04T00:00:00Z"));
        assertThat(assemble(odd, List.of(), Map.of(), List.of(), List.of()).medianProcessingDays())
                .isEqualTo(2.0);

        List<DossierSlaRow> even = List.of(
                closed("SIN", "2026-02-01T00:00:00Z", "2026-02-02T00:00:00Z"),
                closed("SIN", "2026-02-01T00:00:00Z", "2026-02-04T00:00:00Z"));
        assertThat(assemble(even, List.of(), Map.of(), List.of(), List.of()).medianProcessingDays())
                .isEqualTo(2.0);
    }

    @Test
    void meanDelta_vsPreviousPeriod() {
        List<DossierSlaRow> current = List.of(
                closed("SIN", "2026-02-01T00:00:00Z", "2026-02-03T00:00:00Z"));
        List<DossierSlaRow> previous = List.of(
                closed("SIN", "2025-12-16T00:00:00Z", "2025-12-19T00:00:00Z"));

        SlaReport report = assemble(current, previous, Map.of(), List.of(), List.of());

        assertThat(report.meanProcessingDays()).isEqualTo(2.0);
        assertThat(report.meanDeltaVsPreviousDays()).isEqualTo(-1.0);
    }

    @Test
    void volumeTrend_previousZeroAndCurrentPositive_isPlus100() {
        List<DossierSlaRow> current = List.of(
                closed("SIN", "2026-02-01T00:00:00Z", "2026-02-02T00:00:00Z"));

        SlaReport report = assemble(current, List.of(), Map.of(), List.of(), List.of());

        assertThat(report.types().getFirst().volumeTrendPercent()).isEqualTo(100.0);
    }

    @Test
    void typeShares_lastPercentAbsorbsRounding() {
        List<DossierSlaRow> current = List.of(
                closed("SIN", "2026-02-01T00:00:00Z", "2026-02-02T00:00:00Z"),
                closed("SIN", "2026-02-01T00:00:00Z", "2026-02-02T00:00:00Z"),
                closed("REC", "2026-02-01T00:00:00Z", "2026-02-02T00:00:00Z"));

        SlaReport report = assemble(current, List.of(), Map.of(), List.of(), List.of());

        double sum = report.typeShares().stream().mapToDouble(SlaReport.TypeShare::percent).sum();
        assertThat(sum).isCloseTo(100.0, within(0.0001));
    }

    @Test
    void unassignedManager_hasNullSlaPercent() {
        DossierSlaRow open = open("SIN", "2026-02-01T00:00:00Z");
        List<ActiveTaskRow> tasks = List.of(new ActiveTaskRow(open.id(), null, "SIN", open));

        SlaReport report = assemble(List.of(open), List.of(), Map.of(), tasks, List.of());

        assertThat(report.managerLoads()).hasSize(1);
        assertThat(report.managerLoads().getFirst().displayName()).isEqualTo("Non assignés");
        assertThat(report.managerLoads().getFirst().slaPercent()).isNull();
        assertThat(report.managerLoads().getFirst().inProgress()).isEqualTo(1);
    }

    @Test
    void stepDurations_areMeanHours() {
        List<HistoricStepRow> steps = List.of(
                new HistoricStepRow("Instruction", 36_000_000L),
                new HistoricStepRow("Instruction", 72_000_000L));

        SlaReport report = assemble(List.of(), List.of(), Map.of(), List.of(), steps);

        assertThat(report.stepDurationsHours()).containsExactly(new SlaReport.StepDuration("Instruction", 15.0));
    }

    private static SlaReport assemble(
            List<DossierSlaRow> current,
            List<DossierSlaRow> previous,
            Map<String, Long> produced,
            List<ActiveTaskRow> tasks,
            List<HistoricStepRow> steps) {
        return SlaReportAssembler.assemble(
                FROM,
                TO,
                95,
                List.of(SINISTRE, RECLAMATION),
                current,
                previous,
                produced,
                tasks,
                steps,
                NOW);
    }

    private static DossierSlaRow closed(String type, String created, String closed) {
        return new DossierSlaRow(
                UUID.randomUUID(),
                type,
                Instant.parse(created),
                Instant.parse(closed),
                Instant.parse(closed),
                DossierStatus.CLOTURE);
    }

    private static DossierSlaRow open(String type, String created) {
        Instant at = Instant.parse(created);
        return new DossierSlaRow(UUID.randomUUID(), type, at, null, at, DossierStatus.EN_COURS);
    }
}
