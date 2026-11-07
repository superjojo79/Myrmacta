package be.myrmacta.domain.deadline.usecase;

import be.myrmacta.domain.deadline.model.BelgianFederalHolidays;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.in.ManageTenantCalendarUseCase;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** TODO §120 — le calendrier lu sans ligne en base est le défaut, non persisté tant qu'on ne l'enregistre pas. */
public final class ManageTenantCalendarUseCaseImpl implements ManageTenantCalendarUseCase {

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
    public StoredTenantCalendar replace(AccessContext.User actor, String zoneId, WeekendPattern weekend, List<LocalDate> holidays) {
        guard.requireTenantAdmin(actor);
        TenantCalendar calendar = new TenantCalendar(zoneId, weekend, holidays);
        StoredTenantCalendar stored = new StoredTenantCalendar(tenantId, calendar, clock.instant(), actor.userId());
        repository.save(stored);
        return stored;
    }

    @Override
    public StoredTenantCalendar addBelgianFederalHolidays(AccessContext.User actor) {
        guard.requireTenantAdmin(actor);
        TenantCalendar current = repository.find(tenantId).map(StoredTenantCalendar::calendar).orElseGet(TenantCalendar::defaults);
        int year = current.toCalendar().today(clock.instant()).getYear();
        TenantCalendar filled = current.plus(BelgianFederalHolidays.of(year, year + 1));
        StoredTenantCalendar stored = new StoredTenantCalendar(tenantId, filled, clock.instant(), actor.userId());
        repository.save(stored);
        return stored;
    }

    private StoredTenantCalendar defaults() {
        return new StoredTenantCalendar(tenantId, TenantCalendar.defaults(), null, null);
    }
}
