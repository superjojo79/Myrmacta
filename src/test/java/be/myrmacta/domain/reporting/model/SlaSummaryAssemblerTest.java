package be.myrmacta.domain.reporting.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.dossier.model.DossierStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SlaSummaryAssemblerTest {

    private static final Instant FROM = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-03-16T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-03-15T00:00:00Z");
    private static final TypeSlaMeta SIN = new TypeSlaMeta("SIN", "Sinistre", "wf-sin", 48);
    private static final TypeSlaMeta REC = new TypeSlaMeta("REC", "Réclamation", "wf-rec", null);

    @Test
    void groupsByType_countsStatusesAndUnscored() {
        List<DossierSlaRow> rows = List.of(
                open("SIN", "2026-03-14T00:00:00Z"),
                atRisk("SIN"),
                closedOverdue("SIN"),
                open("REC", "2026-03-14T00:00:00Z"));

        SlaSummary summary = SlaSummaryAssembler.assemble(FROM, TO, null, List.of(SIN, REC), rows, NOW);

        assertThat(summary.groupBy()).isEqualTo("dossierTypeCode");
        assertThat(summary.buckets()).hasSize(2);
        SlaSummaryBucket sin = summary.buckets().stream().filter(b -> b.key().equals("SIN")).findFirst().orElseThrow();
        assertThat(sin.label()).isEqualTo("Sinistre");
        assertThat(sin.volume()).isEqualTo(3);
        assertThat(sin.onTrack()).isEqualTo(1);
        assertThat(sin.atRisk()).isEqualTo(1);
        assertThat(sin.breached()).isEqualTo(1);
        assertThat(sin.unscored()).isZero();
        SlaSummaryBucket rec = summary.buckets().stream().filter(b -> b.key().equals("REC")).findFirst().orElseThrow();
        assertThat(rec.unscored()).isEqualTo(1);
        assertThat(rec.volume()).isEqualTo(1);
        assertThat(rec.onTrack() + rec.atRisk() + rec.breached()).isZero();
    }

    @Test
    void groupsByStatus_usesEnumNameAsKey() {
        List<DossierSlaRow> rows = List.of(
                open("SIN", "2026-03-14T00:00:00Z"),
                closedOverdue("SIN"));

        SlaSummary summary =
                SlaSummaryAssembler.assemble(FROM, TO, "status", List.of(SIN), rows, NOW);

        assertThat(summary.groupBy()).isEqualTo("status");
        assertThat(summary.buckets())
                .extracting(SlaSummaryBucket::key)
                .containsExactlyInAnyOrder("EN_COURS", "CLOTURE");
    }

    @Test
    void calendarGroupBy_isRejected() {
        assertThatThrownBy(() ->
                        SlaSummaryAssembler.assemble(FROM, TO, "day", List.of(), List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupBy");
    }

    private static DossierSlaRow open(String type, String created) {
        Instant at = Instant.parse(created);
        return new DossierSlaRow(UUID.randomUUID(), type, at, null, at, DossierStatus.EN_COURS);
    }

    private static DossierSlaRow atRisk(String type) {
        Instant created = Instant.parse("2026-03-13T09:00:00Z");
        return new DossierSlaRow(UUID.randomUUID(), type, created, null, created, DossierStatus.EN_COURS);
    }

    private static DossierSlaRow closedOverdue(String type) {
        Instant created = Instant.parse("2026-02-01T00:00:00Z");
        Instant closed = Instant.parse("2026-02-10T00:00:00Z");
        return new DossierSlaRow(UUID.randomUUID(), type, created, closed, closed, DossierStatus.CLOTURE);
    }
}
