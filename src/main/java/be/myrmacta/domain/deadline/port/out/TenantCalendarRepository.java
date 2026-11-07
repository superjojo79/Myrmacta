package be.myrmacta.domain.deadline.port.out;

import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** TODO §120 et §121 — réglage persisté. Vide : le tenant n'a encore rien enregistré. */
public interface TenantCalendarRepository {

    Optional<StoredTenantCalendar> find(String tenantId);

    /** Écrit le fuseau, le week-end et les saisies manuelles. Les sources ne sont pas touchées. */
    void save(StoredTenantCalendar calendar);

    /** Crée ou remplace atomiquement la source et tous ses jours (D-CAL-SOURCE). */
    void saveSource(String tenantId, CalendarSource source);

    /** Supprime la source et ses jours. {@code false} : aucune source de ce tenant avec cet identifiant. */
    boolean deleteSource(String tenantId, UUID sourceId);

    record StoredTenantCalendar(String tenantId, TenantCalendar calendar, Instant updatedAt, String updatedBy) {}
}
