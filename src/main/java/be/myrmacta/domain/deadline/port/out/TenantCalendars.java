package be.myrmacta.domain.deadline.port.out;

import be.myrmacta.domain.deadline.model.BusinessCalendar;

/** TODO §120 — calendrier effectif du tenant. Jamais vide : l'absence de réglage vaut les défauts. */
public interface TenantCalendars {

    BusinessCalendar get(String tenantId);
}
