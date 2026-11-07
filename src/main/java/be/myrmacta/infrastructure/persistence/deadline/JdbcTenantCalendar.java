package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.deadline.model.BusinessCalendar;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.deadline.port.out.TenantCalendars;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** TODO §120 — table {@code tenant_calendar} (changeset 046). Les fériés sont des dates ISO séparées par une virgule. */
@Component
class JdbcTenantCalendar implements TenantCalendarRepository, TenantCalendars {

    private final JdbcTemplate jdbc;

    JdbcTenantCalendar(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BusinessCalendar get(String tenantId) {
        return find(tenantId).map(stored -> stored.calendar().toCalendar()).orElseGet(BusinessCalendar::defaults);
    }

    @Override
    public Optional<StoredTenantCalendar> find(String tenantId) {
        return jdbc.query("select tenant_id, zone_id, weekend, holidays, updated_at, updated_by from tenant_calendar where tenant_id = ?",
                (rs, i) -> map(rs), tenantId).stream().findFirst();
    }

    @Override
    @Transactional
    public void save(StoredTenantCalendar calendar) {
        TenantCalendar value = calendar.calendar();
        jdbc.update("""
                insert into tenant_calendar (tenant_id, zone_id, weekend, holidays, updated_at, updated_by)
                values (?, ?, ?, ?, ?, ?)
                on conflict (tenant_id) do update set
                  zone_id = excluded.zone_id, weekend = excluded.weekend, holidays = excluded.holidays,
                  updated_at = excluded.updated_at, updated_by = excluded.updated_by
                """, calendar.tenantId(), value.zoneId(), value.weekend().name(), join(value.holidays()),
                Timestamp.from(calendar.updatedAt()), calendar.updatedBy());
    }

    private static StoredTenantCalendar map(ResultSet rs) throws SQLException {
        Timestamp updated = rs.getTimestamp("updated_at");
        return new StoredTenantCalendar(rs.getString("tenant_id"),
                new TenantCalendar(rs.getString("zone_id"), WeekendPattern.parse(rs.getString("weekend")), parse(rs.getString("holidays"))),
                updated == null ? null : updated.toInstant(), rs.getString("updated_by"));
    }

    static String join(List<LocalDate> holidays) {
        return String.join(",", holidays.stream().map(LocalDate::toString).toList());
    }

    static List<LocalDate> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::strip).filter(part -> !part.isEmpty()).map(LocalDate::parse).toList();
    }
}
