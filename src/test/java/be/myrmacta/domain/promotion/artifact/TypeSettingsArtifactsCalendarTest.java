package be.myrmacta.domain.promotion.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarEntry.Nature;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.in.ManageDeadlineUseCase;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.mailroom.port.in.ManageReplyWorkflowUseCase;
import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.PlanAction;
import be.myrmacta.domain.promotion.model.PlanEntry;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.infrastructure.promotion.JsonArtifactCodec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * TODO §121 (D-CAL-PROMO-2) — l'artefact CALENDAR est un instantané complet : fuseau, week-end, saisies manuelles et
 * sources avec leurs périodes. L'import ne fait aucun appel réseau ; un ancien bundle à liste plate s'importe.
 */
class TypeSettingsArtifactsCalendarTest {

    private static final String TENANT = "tenant_dev";
    private static final JsonArtifactCodec CODEC = new JsonArtifactCodec();

    private static CalendarSource holidays(String name, CalendarSource.Kind kind, CalendarEntry... days) {
        return CalendarSource.create(name, kind, List.of(days), Instant.parse("2026-11-07T10:00:00Z"), "admin", "abc");
    }

    private static TenantCalendar populated() {
        return new TenantCalendar("Europe/Brussels", WeekendPattern.FRIDAY_SATURDAY,
                List.of(CalendarEntry.day(LocalDate.parse("2026-11-14"), Nature.OPEN, "Samedi travaillé"),
                        new CalendarEntry(LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-31"), Nature.CLOSED, "Congé")),
                List.of(holidays("Fériés BE", CalendarSource.Kind.BE_FEDERAL,
                                CalendarEntry.day(LocalDate.parse("2026-11-11"), Nature.CLOSED, "Armistice")),
                        holidays("Écoles", CalendarSource.Kind.ICS_URL,
                                new CalendarEntry(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-31"), Nature.CLOSED, "Vacances"))));
    }

    private static BundleEntry legacy(String json) {
        return new BundleEntry(ArtifactType.CALENDAR, "tenant", json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void anEmptyTenantExportsTheDefaultsAndPlansThemAsUnchanged() {
        Memory here = new Memory();

        BundleEntry exported = calendarEntry(artifacts(here).export());

        assertThat(artifacts(new Memory()).planCalendar(exported).action()).isEqualTo(PlanAction.UNCHANGED);
        assertThat(new String(exported.content(), StandardCharsets.UTF_8)).contains("\"entries\" : [ ]", "\"sources\" : [ ]");
    }

    @Test
    void aFullSnapshotRoundTripsWithoutAnyNetworkCall_keepingSourcesAndOverrides() {
        Memory origin = new Memory(populated());
        BundleEntry exported = calendarEntry(artifacts(origin).export());
        String json = new String(exported.content(), StandardCharsets.UTF_8);
        assertThat(json).contains("Samedi travaillé", "Vacances", "ICS_URL").doesNotContain("http");

        Memory target = new Memory();
        TypeSettingsArtifacts importer = artifacts(target);
        PlanEntry plan = importer.planCalendar(exported);
        String outcome = importer.applyCalendar(exported, plan.action());

        assertThat(plan.action()).isEqualTo(PlanAction.CREATE);
        assertThat(outcome).isEqualTo("created");
        TenantCalendar imported = target.stored.calendar();
        assertThat(imported.zoneId()).isEqualTo("Europe/Brussels");
        assertThat(imported.weekend()).isEqualTo(WeekendPattern.FRIDAY_SATURDAY);
        assertThat(imported.manual()).isEqualTo(populated().manual());
        assertThat(target.sources).extracting(CalendarSource::name).containsExactlyInAnyOrder("Fériés BE", "Écoles");
        assertThat(target.sources).extracting(CalendarSource::kind)
                .containsExactlyInAnyOrder(CalendarSource.Kind.BE_FEDERAL, CalendarSource.Kind.ICS_URL);
        assertThat(target.sources).allSatisfy(source -> assertThat(source.loadedBy()).isEqualTo("promotion"));
        assertThat(importer.planCalendar(calendarEntry(artifacts(target).export())).action()).isEqualTo(PlanAction.UNCHANGED);
    }

    // TODO §121 (revue de #33) — l'application d'un calendrier de bundle (réglage, suppressions, sources) est atomique.
    @Test
    void applyingACalendarRunsInASingleLock_andAnUnchangedOneTakesNone() {
        Memory target = new Memory();
        TypeSettingsArtifacts importer = artifacts(target);
        BundleEntry exported = calendarEntry(artifacts(new Memory(populated())).export());

        importer.applyCalendar(exported, importer.planCalendar(exported).action());
        assertThat(target.lockedRuns).isEqualTo(1);

        importer.applyCalendar(exported, PlanAction.UNCHANGED);
        assertThat(target.lockedRuns).isEqualTo(1);
    }

    @Test
    void importingAgainOverTheSameCalendarIsUnchanged_andWritesNothing() {
        Memory target = new Memory(populated());
        TypeSettingsArtifacts importer = artifacts(target);
        BundleEntry exported = calendarEntry(importer.export());
        int writes = target.writes;

        PlanEntry plan = importer.planCalendar(exported);

        assertThat(plan.action()).isEqualTo(PlanAction.UNCHANGED);
        assertThat(importer.applyCalendar(exported, plan.action())).isEqualTo("unchanged");
        assertThat(target.writes).isEqualTo(writes);
    }

    @Test
    void aSourceWithTheSameNameKeepsItsIdHere_andASourceAbsentFromTheBundleIsRemoved() {
        TenantCalendar here = populated();
        Memory target = new Memory(here);
        UUID kept = target.sources.stream().filter(source -> source.name().equals("Fériés BE")).findFirst().orElseThrow().id();
        UUID removed = target.sources.stream().filter(source -> source.name().equals("Écoles")).findFirst().orElseThrow().id();
        TenantCalendar incoming = new TenantCalendar("UTC", WeekendPattern.SATURDAY_SUNDAY, List.of(),
                List.of(holidays("FÉRIÉS BE", CalendarSource.Kind.BE_FEDERAL,
                        CalendarEntry.day(LocalDate.parse("2027-01-01"), Nature.CLOSED, "Jour de l'an"))));
        BundleEntry bundle = calendarEntry(artifacts(new Memory(incoming)).export());
        TypeSettingsArtifacts importer = artifacts(target);

        PlanEntry plan = importer.planCalendar(bundle);
        String outcome = importer.applyCalendar(bundle, plan.action());

        assertThat(plan.action()).isEqualTo(PlanAction.UPDATE);
        assertThat(outcome).isEqualTo("updated");
        assertThat(target.sources).hasSize(1);
        assertThat(target.sources.getFirst().id()).isEqualTo(kept);
        assertThat(target.sources.getFirst().closedDays()).hasSize(1);
        assertThat(target.deleted).containsExactly(removed);
    }

    @Test
    void anOldFlatBundleImportsAsManualClosedDays() {
        BundleEntry old = legacy("""
                {"holidays":["2026-12-25","2026-11-11"],"weekend":"SUNDAY_ONLY","zoneId":"Europe/Brussels"}
                """);
        Memory target = new Memory();
        TypeSettingsArtifacts importer = artifacts(target);

        PlanEntry plan = importer.planCalendar(old);
        importer.applyCalendar(old, plan.action());

        assertThat(plan.action()).isEqualTo(PlanAction.CREATE);
        assertThat(target.stored.calendar().manual()).extracting(CalendarEntry::start)
                .containsExactly(LocalDate.parse("2026-11-11"), LocalDate.parse("2026-12-25"));
        assertThat(target.stored.calendar().manual()).allSatisfy(entry -> {
            assertThat(entry.nature()).isEqualTo(Nature.CLOSED);
            assertThat(entry.label()).isNull();
            assertThat(entry.dayCount()).isEqualTo(1);
        });
        assertThat(target.stored.calendar().sources()).isEmpty();
    }

    @Test
    void anOldFlatBundleWithExplicitDefaultsIsUnchangedOnATenantWithoutARow() {
        BundleEntry old = legacy("""
                {"holidays":[],"weekend":"SATURDAY_SUNDAY","zoneId":"UTC"}
                """);
        Memory target = new Memory();
        TypeSettingsArtifacts importer = artifacts(target);

        PlanEntry plan = importer.planCalendar(old);

        assertThat(plan.action()).isEqualTo(PlanAction.UNCHANGED);
        assertThat(importer.applyCalendar(old, plan.action())).isEqualTo("unchanged");
        assertThat(target.stored).isNull();
        assertThat(target.writes).isZero();
    }

    @Test
    void anInvalidCalendarIsBlocked_neverApplied() {
        String[] invalid = {
                """
                {"zoneId":"Mars/Phobos","weekend":"SATURDAY_SUNDAY","entries":[],"sources":[]}
                """,
                """
                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[{"start":"2026-12-31","end":"2026-12-24","nature":"CLOSED"}],"sources":[]}
                """,
                """
                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[],"sources":[{"name":"X","kind":"ICS_FILE","entries":[{"start":"2026-12-24","end":"2026-12-24","nature":"OPEN"}]}]}
                """,
                """
                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","holidays":["2026-12-25"],"entries":[{"start":"2026-12-24","end":"2026-12-24","nature":"CLOSED"}],"sources":[]}
                """,
                """
                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","holidays":["pas une date"]}
                """
        };
        for (String json : invalid) {
            Memory target = new Memory();

            PlanEntry plan = artifacts(target).planCalendar(legacy(json));

            assertThat(plan.action()).as(json).isEqualTo(PlanAction.BLOCKED);
            assertThat(target.writes).isZero();
        }
    }

    private static BundleEntry calendarEntry(List<BundleEntry> entries) {
        return entries.stream().filter(entry -> entry.type() == ArtifactType.CALENDAR).findFirst().orElseThrow();
    }

    private static TypeSettingsArtifacts artifacts(Memory memory) {
        return new TypeSettingsArtifacts(TENANT, CODEC, mock(DossierTypeRepository.class), mock(WorkflowTypeConfigRepository.class),
                mock(ManageReplyWorkflowUseCase.class), mock(ManageDeadlineUseCase.class), memory);
    }

    /** Dépôt en mémoire : une ligne de réglage (avec ses saisies manuelles) et des sources à part. */
    private static final class Memory implements TenantCalendarRepository {

        StoredTenantCalendar stored;
        final List<CalendarSource> sources = new ArrayList<>();
        final List<UUID> deleted = new ArrayList<>();
        int writes;
        int lockedRuns;

        Memory() {
        }

        Memory(TenantCalendar calendar) {
            stored = new StoredTenantCalendar(TENANT, calendar, Instant.parse("2026-11-01T00:00:00Z"), "admin");
            sources.addAll(calendar.sources());
        }

        @Override
        public Optional<StoredTenantCalendar> find(String tenantId) {
            if (stored == null) {
                return Optional.empty();
            }
            TenantCalendar calendar = stored.calendar();
            return Optional.of(new StoredTenantCalendar(tenantId,
                    new TenantCalendar(calendar.zoneId(), calendar.weekend(), calendar.manual(), sources),
                    stored.updatedAt(), stored.updatedBy()));
        }

        @Override
        public void save(StoredTenantCalendar calendar) {
            writes++;
            stored = calendar;
        }

        @Override
        public void saveSource(String tenantId, CalendarSource source, String url) {
            writes++;
            sources.removeIf(existing -> existing.id().equals(source.id()));
            sources.add(source);
        }

        @Override
        public <T> T locked(String tenantId, java.util.function.Supplier<T> work) {
            lockedRuns++;
            return work.get();
        }

        @Override
        public void touch(String tenantId, Instant at, String by) {
            writes++;
            TenantCalendar calendar = stored == null ? TenantCalendar.defaults() : stored.calendar();
            stored = new StoredTenantCalendar(tenantId, calendar, at, by);
        }

        @Override
        public Optional<String> findSourceUrl(String tenantId, UUID sourceId) {
            return Optional.empty();
        }

        @Override
        public boolean deleteSource(String tenantId, UUID sourceId) {
            writes++;
            deleted.add(sourceId);
            return sources.removeIf(existing -> existing.id().equals(sourceId));
        }
    }
}
