package be.myrmacta.domain.deadline.port.in;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import be.myrmacta.domain.dossier.model.AccessContext;
import java.util.List;

/**
 * TODO §120 et §121 — lecture et remplacement du calendrier du tenant, et source des fériés fédéraux belges.
 * Les opérations sur les sources ICS s'ajoutent aux lots suivants.
 */
public interface ManageTenantCalendarUseCase {

    StoredTenantCalendar get(AccessContext.User actor);

    /** Remplace le fuseau, le week-end et les saisies manuelles ; les sources ne bougent pas (D-CAL-API). */
    StoredTenantCalendar replace(AccessContext.User actor, String zoneId, WeekendPattern weekend, List<CalendarEntry> manual);

    /**
     * Crée ou recharge la source {@code BE_FEDERAL} (D-CAL-BE-SOURCE) : les fériés de l'année civile courante (fuseau
     * du tenant) et de la suivante. Idempotent ; ne touche pas aux saisies manuelles.
     */
    StoredTenantCalendar loadBelgianFederalHolidays(AccessContext.User actor);
}
