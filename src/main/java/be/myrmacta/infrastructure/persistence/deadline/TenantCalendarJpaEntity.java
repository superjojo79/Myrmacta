package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.deadline.model.WeekendPattern;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** TODO §120 (D-CAL-ZONE, D-CAL-WEEKEND) — ligne de réglage du calendrier du tenant (changeset 046). Le verrou du calendrier (§121) est celui de cette ligne. */
@Entity
@Table(name = "tenant_calendar")
class TenantCalendarJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId;

    @Column(nullable = false, length = 32)
    private String weekend;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    protected TenantCalendarJpaEntity() {}

    /** Les défauts de {@code TenantCalendar.defaults()} : UTC, samedi et dimanche. */
    static TenantCalendarJpaEntity defaultsFor(String tenantId, Instant at, String by) {
        TenantCalendarJpaEntity entity = new TenantCalendarJpaEntity();
        entity.tenantId = tenantId;
        entity.zoneId = "UTC";
        entity.weekend = WeekendPattern.SATURDAY_SUNDAY.name();
        entity.updatedAt = at;
        entity.updatedBy = by;
        return entity;
    }

    void applySettings(String zoneId, WeekendPattern weekend) {
        this.zoneId = zoneId;
        this.weekend = weekend.name();
    }

    void touch(Instant at, String by) {
        this.updatedAt = at;
        this.updatedBy = by;
    }

    String zoneId() {
        return zoneId;
    }

    WeekendPattern weekend() {
        return WeekendPattern.parse(weekend);
    }

    Instant updatedAt() {
        return updatedAt;
    }

    String updatedBy() {
        return updatedBy;
    }
}
