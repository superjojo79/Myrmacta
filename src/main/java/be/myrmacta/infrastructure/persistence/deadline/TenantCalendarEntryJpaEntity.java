package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * TODO §121 (D-CAL-ENTRY, D-CAL-SOURCE) — une date ou une période du calendrier (changeset 047). {@code sourceId} nul :
 * saisie manuelle. Supprimer la source supprime ses périodes, par la clé étrangère de la base (cascade).
 */
@Entity
@Table(name = "tenant_calendar_entry")
class TenantCalendarEntryJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 8)
    private String nature;

    @Column(length = 120)
    private String label;

    protected TenantCalendarEntryJpaEntity() {}

    static TenantCalendarEntryJpaEntity fromDomain(String tenantId, UUID sourceId, CalendarEntry entry) {
        TenantCalendarEntryJpaEntity entity = new TenantCalendarEntryJpaEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.sourceId = sourceId;
        entity.startDate = entry.start();
        entity.endDate = entry.end();
        entity.nature = entry.nature().name();
        entity.label = entry.label();
        return entity;
    }

    CalendarEntry toDomain() {
        return new CalendarEntry(startDate, endDate, CalendarEntry.Nature.parse(nature), label);
    }

    UUID sourceId() {
        return sourceId;
    }
}
