package be.myrmacta.domain.deadline.usecase;

import be.myrmacta.domain.deadline.model.BelgianFederalHolidays;
import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.in.ManageTenantCalendarUseCase;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** TODO §120 et §121 — le calendrier lu sans ligne en base est le défaut, non persisté tant qu'on ne l'enregistre pas. */
public final class ManageTenantCalendarUseCaseImpl implements ManageTenantCalendarUseCase {

    /** Nom de la source des fériés fédéraux belges (D-CAL-BE-SOURCE). */
    public static final String BELGIAN_SOURCE_NAME = "Fériés fédéraux belges";

    private final String tenantId;
    private final TenantCalendarRepository repository;
    private final TenantAdminGuard guard;
    private final Clock clock;

    public ManageTenantCalendarUseCaseImpl(String tenantId, TenantCalendarRepository repository, TenantAdminGuard guard, Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public StoredTenantCalendar get(AccessContext.User actor) {
        guard.requireTenantAdmin(actor);
        return repository.find(tenantId).orElseGet(this::defaults);
    }

    @Override
    public StoredTenantCalendar replace(AccessContext.User actor, String zoneId, WeekendPattern weekend, List<CalendarEntry> manual) {
        guard.requireTenantAdmin(actor);
        TenantCalendar next = current().withSettings(zoneId, weekend, manual);
        return saveSettings(next, actor);
    }

    @Override
    public StoredTenantCalendar loadBelgianFederalHolidays(AccessContext.User actor) {
        guard.requireTenantAdmin(actor);
        TenantCalendar current = current();
        Instant now = clock.instant();
        int year = current.toCalendar().today(now).getYear();
        List<CalendarEntry> days = BelgianFederalHolidays.entries(year, year + 1);
        CalendarSource source = current.sources().stream()
                .filter(existing -> existing.kind() == CalendarSource.Kind.BE_FEDERAL)
                .findFirst()
                .map(existing -> existing.reloaded(days, now, actor.userId(), null))
                .orElseGet(() -> CalendarSource.create(BELGIAN_SOURCE_NAME, CalendarSource.Kind.BE_FEDERAL, days, now,
                        actor.userId(), null));
        TenantCalendar next = current.withSource(source);
        StoredTenantCalendar stored = saveSettings(next, actor);
        repository.saveSource(tenantId, source);
        return stored;
    }

    private TenantCalendar current() {
        return repository.find(tenantId).map(StoredTenantCalendar::calendar).orElseGet(TenantCalendar::defaults);
    }

    private StoredTenantCalendar saveSettings(TenantCalendar calendar, AccessContext.User actor) {
        StoredTenantCalendar stored = new StoredTenantCalendar(tenantId, calendar, clock.instant(), actor.userId());
        repository.save(stored);
        return stored;
    }

    private StoredTenantCalendar defaults() {
        return new StoredTenantCalendar(tenantId, TenantCalendar.defaults(), null, null);
    }
}
