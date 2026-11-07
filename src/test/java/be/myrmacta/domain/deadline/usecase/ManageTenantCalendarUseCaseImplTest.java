package be.myrmacta.domain.deadline.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarEntry.Nature;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private ManageTenantCalendarUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        useCase = new ManageTenantCalendarUseCaseImpl(TENANT, repository, new TenantAdminGuard(), Clock.fixed(NOW, ZoneOffset.UTC));
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
