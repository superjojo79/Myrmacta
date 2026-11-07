package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.deadline.model.BusinessCalendar;
import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.deadline.port.out.TenantCalendars;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TODO §120 et §121 — tables {@code tenant_calendar} (046), {@code tenant_calendar_source} et
 * {@code tenant_calendar_entry} (047). Une saisie manuelle a {@code source_id} nul ; supprimer une source supprime
 * ses jours (cascade).
 */
@Component
class JdbcTenantCalendar implements TenantCalendarRepository, TenantCalendars {

    private final JdbcTemplate jdbc;

    JdbcTenantCalendar(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessCalendar get(String tenantId) {
        return find(tenantId).map(stored -> stored.calendar().toCalendar()).orElseGet(BusinessCalendar::defaults);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredTenantCalendar> find(String tenantId) {
        List<Header> headers = jdbc.query(
                "select zone_id, weekend, updated_at, updated_by from tenant_calendar where tenant_id = ?",
                (rs, i) -> new Header(rs.getString("zone_id"), WeekendPattern.parse(rs.getString("weekend")),
                        instant(rs.getTimestamp("updated_at")), rs.getString("updated_by")), tenantId);
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        Header header = headers.getFirst();
        List<CalendarEntry> manual = new ArrayList<>();
        Map<UUID, List<CalendarEntry>> bySource = new LinkedHashMap<>();
        jdbc.query("select source_id, start_date, end_date, nature, label from tenant_calendar_entry where tenant_id = ?",
                rs -> {
                    CalendarEntry entry = entry(rs);
                    UUID sourceId = rs.getObject("source_id", UUID.class);
                    if (sourceId == null) {
                        manual.add(entry);
                    } else {
                        bySource.computeIfAbsent(sourceId, id -> new ArrayList<>()).add(entry);
                    }
                }, tenantId);
        List<CalendarSource> sources = jdbc.query("""
                select id, name, kind, loaded_at, loaded_by, content_sha256, last_error
                from tenant_calendar_source where tenant_id = ? order by lower(name)
                """, (rs, i) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new CalendarSource(id, rs.getString("name"), CalendarSource.Kind.parse(rs.getString("kind")),
                            bySource.getOrDefault(id, List.of()), instant(rs.getTimestamp("loaded_at")),
                            rs.getString("loaded_by"), rs.getString("content_sha256"), rs.getString("last_error"));
                }, tenantId);
        return Optional.of(new StoredTenantCalendar(tenantId,
                new TenantCalendar(header.zoneId(), header.weekend(), manual, sources), header.updatedAt(), header.updatedBy()));
    }

    @Override
    @Transactional
    public void save(StoredTenantCalendar calendar) {
        TenantCalendar value = calendar.calendar();
        jdbc.update("""
                insert into tenant_calendar (tenant_id, zone_id, weekend, updated_at, updated_by)
                values (?, ?, ?, ?, ?)
                on conflict (tenant_id) do update set
                  zone_id = excluded.zone_id, weekend = excluded.weekend,
                  updated_at = excluded.updated_at, updated_by = excluded.updated_by
                """, calendar.tenantId(), value.zoneId(), value.weekend().name(),
                Timestamp.from(calendar.updatedAt()), calendar.updatedBy());
        jdbc.update("delete from tenant_calendar_entry where tenant_id = ? and source_id is null", calendar.tenantId());
        insertEntries(calendar.tenantId(), null, value.manual());
    }

    @Override
    @Transactional
    public void saveSource(String tenantId, CalendarSource source) {
        jdbc.update("""
                insert into tenant_calendar_source (id, tenant_id, name, kind, loaded_at, loaded_by, content_sha256, last_error)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                  name = excluded.name, kind = excluded.kind, loaded_at = excluded.loaded_at,
                  loaded_by = excluded.loaded_by, content_sha256 = excluded.content_sha256, last_error = excluded.last_error
                where tenant_calendar_source.tenant_id = excluded.tenant_id
                """, source.id(), tenantId, source.name(), source.kind().name(),
                source.loadedAt() == null ? null : Timestamp.from(source.loadedAt()), source.loadedBy(),
                source.contentSha256(), source.lastError());
        jdbc.update("delete from tenant_calendar_entry where tenant_id = ? and source_id = ?", tenantId, source.id());
        insertEntries(tenantId, source.id(), source.closedDays());
    }

    @Override
    @Transactional
    public boolean deleteSource(String tenantId, UUID sourceId) {
        return jdbc.update("delete from tenant_calendar_source where tenant_id = ? and id = ?", tenantId, sourceId) > 0;
    }

    private void insertEntries(String tenantId, UUID sourceId, List<CalendarEntry> entries) {
        jdbc.batchUpdate("""
                insert into tenant_calendar_entry (id, tenant_id, source_id, start_date, end_date, nature, label)
                values (?, ?, ?, ?, ?, ?, ?)
                """, entries, 500, (ps, entry) -> {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setString(2, tenantId);
                    ps.setObject(3, sourceId);
                    ps.setDate(4, Date.valueOf(entry.start()));
                    ps.setDate(5, Date.valueOf(entry.end()));
                    ps.setString(6, entry.nature().name());
                    ps.setString(7, entry.label());
                });
    }

    private static CalendarEntry entry(ResultSet rs) throws SQLException {
        return new CalendarEntry(rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(),
                CalendarEntry.Nature.parse(rs.getString("nature")), rs.getString("label"));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record Header(String zoneId, WeekendPattern weekend, Instant updatedAt, String updatedBy) {}
}
