package be.myrmacta.domain.deadline.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarEntry.Nature;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.CalendarSourceConflictException;
import be.myrmacta.domain.deadline.model.CalendarSourceNotFoundException;
import be.myrmacta.domain.deadline.model.DayRange;
import be.myrmacta.domain.deadline.model.IcsIgnoreReason;
import be.myrmacta.domain.deadline.model.SourcePreview;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.out.IcsCalendarParser;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §121 (D-CAL-OVERRIDE, D-CAL-SOURCE, D-CAL-BE-SOURCE) — cas d'usage sur un dépôt en mémoire. */
class ManageTenantCalendarUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final AccessContext.User ADMIN = new AccessContext.User("admin", Set.of("TENANT_ADMIN"));
    private static final AccessContext.User MANAGER = new AccessContext.User("mgr", Set.of("DOSSIER_MANAGER"));
    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private InMemoryRepository repository;
    private StubParser parser;
    private ManageTenantCalendarUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        parser = new StubParser();
        useCase = new ManageTenantCalendarUseCaseImpl(TENANT, repository, new TenantAdminGuard(), Clock.fixed(NOW, ZoneOffset.UTC), parser);
    }

    @Test
    void getWithoutARow_returnsTheDefaultsWithoutPersistingThem() {
        StoredTenantCalendar stored = useCase.get(ADMIN);

        assertThat(stored.calendar()).isEqualTo(TenantCalendar.defaults());
        assertThat(stored.updatedAt()).isNull();
        assertThat(repository.stored).isNull();
    }

    @Test
    void everyOperationRequiresTheTenantAdminRole() {
        assertThatThrownBy(() -> useCase.get(MANAGER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> useCase.replace(MANAGER, "UTC", WeekendPattern.SATURDAY_SUNDAY, List.of()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> useCase.loadBelgianFederalHolidays(MANAGER)).isInstanceOf(AccessDeniedException.class);
        assertThat(repository.stored).isNull();
    }

    @Test
    void replaceStoresTheSettingsAndTheManualEntries_andRecordsWhoAndWhen() {
        CalendarEntry open = CalendarEntry.day(LocalDate.parse("2026-11-14"), Nature.OPEN, "Samedi travaillé");

        StoredTenantCalendar stored = useCase.replace(ADMIN, "Europe/Brussels", WeekendPattern.SUNDAY_ONLY, List.of(open));

        assertThat(stored.calendar().manual()).containsExactly(open);
        assertThat(stored.updatedAt()).isEqualTo(NOW);
        assertThat(stored.updatedBy()).isEqualTo("admin");
        assertThat(repository.stored.calendar().zoneId()).isEqualTo("Europe/Brussels");
    }

    @Test
    void replaceNeverTouchesTheSources() {
        useCase.loadBelgianFederalHolidays(ADMIN);

        StoredTenantCalendar stored = useCase.replace(ADMIN, "UTC", WeekendPattern.SATURDAY_SUNDAY,
                List.of(CalendarEntry.day(LocalDate.parse("2026-12-24"), Nature.CLOSED, null)));

        assertThat(stored.calendar().sources()).hasSize(1);
        assertThat(repository.sources).hasSize(1);
    }

    @Test
    void replaceRefusesAnInvalidZone() {
        assertThatThrownBy(() -> useCase.replace(ADMIN, "Mars/Phobos", WeekendPattern.SATURDAY_SUNDAY, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.stored).isNull();
    }

    @Test
    void theBelgianSourceHoldsTheCurrentAndNextYear() {
        StoredTenantCalendar stored = useCase.loadBelgianFederalHolidays(ADMIN);

        CalendarSource source = stored.calendar().sources().getFirst();
        assertThat(source.kind()).isEqualTo(CalendarSource.Kind.BE_FEDERAL);
        assertThat(source.name()).isEqualTo(ManageTenantCalendarUseCaseImpl.BELGIAN_SOURCE_NAME);
        assertThat(source.closedDays()).hasSize(20);
        assertThat(source.closedDays()).anyMatch(entry -> entry.start().equals(LocalDate.parse("2026-11-11")));
        assertThat(source.closedDays()).anyMatch(entry -> entry.start().equals(LocalDate.parse("2027-12-25")));
        assertThat(source.loadedBy()).isEqualTo("admin");
        assertThat(source.loadedAt()).isEqualTo(NOW);
    }

    @Test
    void loadingTheBelgianSourceTwiceKeepsOneSourceWithTheSameId() {
        UUID first = useCase.loadBelgianFederalHolidays(ADMIN).calendar().sources().getFirst().id();

        StoredTenantCalendar again = useCase.loadBelgianFederalHolidays(ADMIN);

        assertThat(again.calendar().sources()).hasSize(1);
        assertThat(again.calendar().sources().getFirst().id()).isEqualTo(first);
        assertThat(repository.sources).hasSize(1);
        assertThat(repository.sources.getFirst().closedDays()).hasSize(20);
    }

    @Test
    void reloadingTheBelgianSourceKeepsTheManualEntriesAndTheirPriority() {
        LocalDate armistice = LocalDate.parse("2026-11-11");
        CalendarEntry worked = CalendarEntry.day(armistice, Nature.OPEN, "On travaille");
        useCase.replace(ADMIN, "UTC", WeekendPattern.SATURDAY_SUNDAY, List.of(worked));

        StoredTenantCalendar stored = useCase.loadBelgianFederalHolidays(ADMIN);

        assertThat(stored.calendar().manual()).containsExactly(worked);
        assertThat(stored.calendar().effectiveClosedDays()).doesNotContain(armistice);
        assertThat(stored.calendar().toCalendar().isBusinessDay(armistice)).isTrue();
        assertThat(repository.stored.calendar().manual()).containsExactly(worked);
    }

    @Test
    void aSourceNamedLikeTheBelgianOneButOfAnotherKind_blocksTheLoad() {
        CalendarSource other = CalendarSource.create(ManageTenantCalendarUseCaseImpl.BELGIAN_SOURCE_NAME.toUpperCase(),
                CalendarSource.Kind.ICS_FILE, List.of(), NOW, "admin", "abc");
        repository.sources.add(other);
        repository.stored = new StoredTenantCalendar(TENANT, TenantCalendar.defaults(), NOW, "admin");

        assertThatThrownBy(() -> useCase.loadBelgianFederalHolidays(ADMIN)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ sources ICS par fichier (lot 2)

    private static final byte[] FILE = "BEGIN:VCALENDAR".getBytes(StandardCharsets.UTF_8);

    private static CalendarEntry closedDay(String date, String label) {
        return CalendarEntry.day(LocalDate.parse(date), Nature.CLOSED, label);
    }

    private void parsed(String sha, CalendarEntry... days) {
        parser.next = new IcsCalendarParser.ParsedIcs(List.of(days), Map.of(IcsIgnoreReason.TIMED, 2), sha);
    }

    @Test
    void previewOfANewSourceListsTheAddedDays_ignoredEventsAndTheSha_andWritesNothing() {
        parsed("sha-1", closedDay("2026-12-25", "Noël"), new CalendarEntry(LocalDate.parse("2026-12-29"), LocalDate.parse("2026-12-31"), Nature.CLOSED, "Congé"));

        SourcePreview preview = useCase.previewFileSource(ADMIN, "Écoles", null, FILE);

        assertThat(preview.sourceId()).isNotNull();
        assertThat(preview.name()).isEqualTo("Écoles");
        assertThat(preview.kind()).isEqualTo(CalendarSource.Kind.ICS_FILE);
        assertThat(preview.added()).containsExactly(new DayRange(LocalDate.parse("2026-12-25"), LocalDate.parse("2026-12-25")),
                new DayRange(LocalDate.parse("2026-12-29"), LocalDate.parse("2026-12-31")));
        assertThat(preview.removed()).isEmpty();
        assertThat(preview.masked()).isEmpty();
        assertThat(preview.ignored()).containsEntry(IcsIgnoreReason.TIMED, 2);
        assertThat(preview.sha256()).isEqualTo("sha-1");
        assertThat(preview.periodCount()).isEqualTo(2);
        assertThat(repository.stored).isNull();
        assertThat(repository.sources).isEmpty();
    }

    @Test
    void theParserReceivesTheTenantZoneAndTheWindowOfThePreviousYearToTwoYearsAhead() {
        useCase.replace(ADMIN, "Europe/Brussels", WeekendPattern.SATURDAY_SUNDAY, List.of());
        parsed("sha-1");

        useCase.previewFileSource(ADMIN, "Écoles", null, FILE);

        assertThat(parser.lastZone).isEqualTo(ZoneId.of("Europe/Brussels"));
        assertThat(parser.fromYear).isEqualTo(2025);
        assertThat(parser.toYear).isEqualTo(2028);
        assertThat(parser.lastContent).isEqualTo(FILE);
    }

    @Test
    void applyingCreatesTheSourceAndRecordsTheLoadAndTheAuthor() {
        parsed("sha-1", closedDay("2026-12-25", "Noël"));

        StoredTenantCalendar stored = useCase.applyFileSource(ADMIN, "Écoles", null, FILE, "sha-1");

        assertThat(stored.calendar().sources()).hasSize(1);
        CalendarSource source = repository.sources.getFirst();
        assertThat(source.name()).isEqualTo("Écoles");
        assertThat(source.kind()).isEqualTo(CalendarSource.Kind.ICS_FILE);
        assertThat(source.loadedBy()).isEqualTo("admin");
        assertThat(source.loadedAt()).isEqualTo(NOW);
        assertThat(source.contentSha256()).isEqualTo("sha-1");
        assertThat(source.closedDays()).containsExactly(closedDay("2026-12-25", "Noël"));
        assertThat(repository.stored.updatedBy()).isEqualTo("admin");
    }

    @Test
    void applyingWithAnotherShaThanThePreviewIsAConflict_andWritesNothing() {
        parsed("sha-2", closedDay("2026-12-25", "Noël"));

        assertThatThrownBy(() -> useCase.applyFileSource(ADMIN, "Écoles", null, FILE, "sha-1"))
                .isInstanceOf(CalendarSourceConflictException.class);

        assertThat(repository.stored).isNull();
        assertThat(repository.sources).isEmpty();
    }

    @Test
    void applyingWithoutAShaIsRefused() {
        parsed("sha-1");

        assertThatThrownBy(() -> useCase.applyFileSource(ADMIN, "Écoles", null, FILE, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.applyFileSource(ADMIN, "Écoles", null, FILE, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.sources).isEmpty();
    }

    @Test
    void theShaComparisonIgnoresCase() {
        parsed("ABCDEF");

        useCase.applyFileSource(ADMIN, "Écoles", null, FILE, "abcdef");

        assertThat(repository.sources).hasSize(1);
    }

    @Test
    void aNameAlreadyUsedIsAConflictForPreviewAndApply_ignoringCase() {
        parsed("sha-1");
        useCase.applyFileSource(ADMIN, "Écoles", null, FILE, "sha-1");

        assertThatThrownBy(() -> useCase.previewFileSource(ADMIN, "ÉCOLES", null, FILE)).isInstanceOf(CalendarSourceConflictException.class);
        assertThatThrownBy(() -> useCase.applyFileSource(ADMIN, "écoles", null, FILE, "sha-1"))
                .isInstanceOf(CalendarSourceConflictException.class);
        assertThat(repository.sources).hasSize(1);
    }

    @Test
    void aBlankNameOrAnEmptyFileIsRefused() {
        parsed("sha-1");

        assertThatThrownBy(() -> useCase.previewFileSource(ADMIN, "  ", null, FILE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.previewFileSource(ADMIN, null, null, FILE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.previewFileSource(ADMIN, "Écoles", null, new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.previewFileSource(ADMIN, "Écoles", null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnreadableFileSurfacesTheParserError_andWritesNothing() {
        parser.failure = new IllegalArgumentException("calendar file is not a readable iCalendar file");

        assertThatThrownBy(() -> useCase.applyFileSource(ADMIN, "Écoles", null, FILE, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readable");

        assertThat(repository.sources).isEmpty();
    }

    @Test
    void reloadingReplacesOnlyThatSource_keepingTheOthersAndTheManualEntries() {
        useCase.loadBelgianFederalHolidays(ADMIN);
        CalendarEntry manual = CalendarEntry.day(LocalDate.parse("2026-12-24"), Nature.CLOSED, "Pont");
        useCase.replace(ADMIN, "UTC", WeekendPattern.SATURDAY_SUNDAY, List.of(manual));
        parsed("sha-1", closedDay("2026-12-25", "Noël"), closedDay("2026-12-26", "Lendemain"));
        UUID id = useCase.applyFileSource(ADMIN, "Écoles", null, FILE, "sha-1").calendar().sources().stream()
                .filter(source -> source.name().equals("Écoles")).findFirst().orElseThrow().id();

        parsed("sha-2", closedDay("2026-12-26", "Lendemain"), closedDay("2027-01-04", "Rentrée"));
        StoredTenantCalendar stored = useCase.applyFileSource(ADMIN, null, id, FILE, "sha-2");

        assertThat(stored.calendar().sources()).hasSize(2);
        CalendarSource reloaded = repository.sources.stream().filter(source -> source.id().equals(id)).findFirst().orElseThrow();
        assertThat(reloaded.name()).isEqualTo("Écoles");
        assertThat(reloaded.closedDays()).extracting(CalendarEntry::start)
                .containsExactly(LocalDate.parse("2026-12-26"), LocalDate.parse("2027-01-04"));
        assertThat(reloaded.contentSha256()).isEqualTo("sha-2");
        assertThat(repository.sources.stream().filter(source -> source.kind() == CalendarSource.Kind.BE_FEDERAL).findFirst().orElseThrow()
                .closedDays()).hasSize(20);
        assertThat(repository.stored.calendar().manual()).containsExactly(manual);
    }

    @Test
    void thePreviewOfAReloadShowsWhatIsAddedAndWhatDisappears() {
        parsed("sha-1", closedDay("2026-12-25", "Noël"), closedDay("2026-12-26", "Lendemain"));
        UUID id = useCase.applyFileSource(ADMIN, "Écoles", null, FILE, "sha-1").calendar().sources().getFirst().id();
        parsed("sha-2", closedDay("2026-12-26", "Lendemain"), closedDay("2027-01-04", "Rentrée"));

        SourcePreview preview = useCase.previewFileSource(ADMIN, "ignoré", id, FILE);

        assertThat(preview.sourceId()).isEqualTo(id);
        assertThat(preview.name()).isEqualTo("Écoles");
        assertThat(preview.added()).containsExactly(new DayRange(LocalDate.parse("2027-01-04"), LocalDate.parse("2027-01-04")));
        assertThat(preview.removed()).containsExactly(new DayRange(LocalDate.parse("2026-12-25"), LocalDate.parse("2026-12-25")));
        assertThat(repository.sources.getFirst().contentSha256()).isEqualTo("sha-1");
    }

    @Test
    void daysCoveredByAManualForcedOpenDayAreReportedAsMasked() {
        useCase.replace(ADMIN, "UTC", WeekendPattern.SATURDAY_SUNDAY,
                List.of(CalendarEntry.day(LocalDate.parse("2026-12-25"), Nature.OPEN, "On travaille"),
                        CalendarEntry.day(LocalDate.parse("2026-12-26"), Nature.CLOSED, "Pont")));
        parsed("sha-1", closedDay("2026-12-25", "Noël"), closedDay("2026-12-26", "Lendemain"));

        SourcePreview preview = useCase.previewFileSource(ADMIN, "Écoles", null, FILE);

        assertThat(preview.masked()).containsExactly(new DayRange(LocalDate.parse("2026-12-25"), LocalDate.parse("2026-12-25")));
    }

    @Test
    void reloadingAnUnknownSourceIs404_andABelgianSourceCannotBeReloadedFromAFile() {
        parsed("sha-1");
        UUID belgian = useCase.loadBelgianFederalHolidays(ADMIN).calendar().sources().getFirst().id();

        assertThatThrownBy(() -> useCase.previewFileSource(ADMIN, null, UUID.randomUUID(), FILE))
                .isInstanceOf(CalendarSourceNotFoundException.class);
        assertThatThrownBy(() -> useCase.applyFileSource(ADMIN, null, UUID.randomUUID(), FILE, "sha-1"))
                .isInstanceOf(CalendarSourceNotFoundException.class);
        assertThatThrownBy(() -> useCase.previewFileSource(ADMIN, null, belgian, FILE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletingASourceRemovesItAndItsDays_butNeverTheOthers() {
        parsed("sha-1", closedDay("2026-12-25", "Noël"));
        UUID id = useCase.applyFileSource(ADMIN, "Écoles", null, FILE, "sha-1").calendar().sources().getFirst().id();
        useCase.loadBelgianFederalHolidays(ADMIN);

        useCase.deleteSource(ADMIN, id);

        assertThat(repository.sources).extracting(CalendarSource::kind).containsExactly(CalendarSource.Kind.BE_FEDERAL);
        assertThatThrownBy(() -> useCase.deleteSource(ADMIN, id)).isInstanceOf(CalendarSourceNotFoundException.class);
        assertThatThrownBy(() -> useCase.deleteSource(ADMIN, UUID.randomUUID())).isInstanceOf(CalendarSourceNotFoundException.class);
    }

    @Test
    void anEleventhSourceIsRefused() {
        parsed("sha-1");
        for (int i = 0; i < TenantCalendar.MAX_SOURCES; i++) {
            useCase.applyFileSource(ADMIN, "Source " + i, null, FILE, "sha-1");
        }

        assertThatThrownBy(() -> useCase.previewFileSource(ADMIN, "Une de trop", null, FILE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.sources).hasSize(10);
    }

    @Test
    void theSourceOperationsRequireTheTenantAdminRole() {
        parsed("sha-1");

        assertThatThrownBy(() -> useCase.previewFileSource(MANAGER, "Écoles", null, FILE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> useCase.applyFileSource(MANAGER, "Écoles", null, FILE, "sha-1")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> useCase.deleteSource(MANAGER, UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        assertThat(repository.sources).isEmpty();
        assertThat(parser.lastContent).isNull();
    }

    /** Analyseur de test : rend ce qu'on lui a préparé et garde ce qu'il a reçu. */
    private static final class StubParser implements IcsCalendarParser {

        ParsedIcs next;
        IllegalArgumentException failure;
        byte[] lastContent;
        ZoneId lastZone;
        int fromYear;
        int toYear;

        @Override
        public ParsedIcs parse(byte[] content, ZoneId zone, int fromYear, int toYear) {
            lastContent = content;
            lastZone = zone;
            this.fromYear = fromYear;
            this.toYear = toYear;
            if (failure != null) {
                throw failure;
            }
            return next;
        }
    }

    /** Dépôt en mémoire : la ligne de réglage, les saisies manuelles (dans le réglage) et les sources à part. */
    private static final class InMemoryRepository implements TenantCalendarRepository {

        StoredTenantCalendar stored;
        final List<CalendarSource> sources = new ArrayList<>();

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
            stored = calendar;
        }

        @Override
        public void saveSource(String tenantId, CalendarSource source) {
            sources.removeIf(existing -> existing.id().equals(source.id()));
            sources.add(source);
        }

        @Override
        public boolean deleteSource(String tenantId, UUID sourceId) {
            return sources.removeIf(existing -> existing.id().equals(sourceId));
        }
    }
}
