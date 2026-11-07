package be.myrmacta.domain.deadline.port.out;

import be.myrmacta.domain.deadline.model.TenantCalendar;
import java.time.Instant;
import java.util.Optional;

/** TODO §120 — réglage persisté. Vide : le tenant n'a encore rien enregistré. */
public interface TenantCalendarRepository {

    Optional<StoredTenantCalendar> find(String tenantId);

    void save(StoredTenantCalendar calendar);

    record StoredTenantCalendar(String tenantId, TenantCalendar calendar, Instant updatedAt, String updatedBy) {}
}
