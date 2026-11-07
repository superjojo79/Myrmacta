package be.myrmacta.domain.deadline.port.in;

import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import be.myrmacta.domain.dossier.model.AccessContext;
import java.time.LocalDate;
import java.util.List;

/** TODO §120 — lecture et remplacement du calendrier du tenant, et ajout des fériés fédéraux belges. */
public interface ManageTenantCalendarUseCase {

    StoredTenantCalendar get(AccessContext.User actor);

    StoredTenantCalendar replace(AccessContext.User actor, String zoneId, WeekendPattern weekend, List<LocalDate> holidays);

    /** Ajoute les fériés de l'année civile courante (fuseau du tenant) et de la suivante. Idempotent. */
    StoredTenantCalendar addBelgianFederalHolidays(AccessContext.User actor);
}
