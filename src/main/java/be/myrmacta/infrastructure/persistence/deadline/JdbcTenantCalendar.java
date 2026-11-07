package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.deadline.model.BusinessCalendar;
import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.connector.port.out.ConnectorSecretCipher;
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
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TODO §120 et §121 — tables {@code tenant_calendar} (046), {@code tenant_calendar_source} et
 * {@code tenant_calendar_entry} (047), {@code masked_url} (048). Une saisie manuelle a {@code source_id} nul ; supprimer
 * une source supprime ses jours (cascade). L'URL d'une source ICS_URL est un secret (D-CAL-ICS-URL) : elle est chiffrée
 * ici, par {@link ConnectorSecretCipher}, et ne sort de cette classe que pour être téléchargée ; la base ne garde en
 * clair que sa forme masquée.
 */
@Component
class JdbcTenantCalendar implements TenantCalendarRepository, TenantCalendars {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcTenantCalendar.class);

    private final JdbcTemplate jdbc;
    private final ConnectorSecretCipher cipher;
    private final TransactionTemplate transaction;

    JdbcTenantCalendar(JdbcTemplate jdbc, ConnectorSecretCipher cipher, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Transaction qui tient le verrou de la ligne {@code tenant_calendar} du tenant jusqu'à sa fin : les écritures des
     * administrateurs se suivent, chacune relisant l'état de la précédente. La ligne est créée avec les défauts si elle
     * manque (elle serait créée par la première écriture de toute façon).
     */
    @Override
    public <T> T locked(String tenantId, Supplier<T> work) {
        return transaction.execute(status -> {
            jdbc.update("""
                    insert into tenant_calendar (tenant_id, zone_id, weekend, updated_at)
                    values (?, 'UTC', 'SATURDAY_SUNDAY', now())
                    on conflict (tenant_id) do nothing
                    """, tenantId);
            jdbc.queryForObject("select tenant_id from tenant_calendar where tenant_id = ? for update", String.class, tenantId);
            return work.get();
        });
    }

    @Override
    @Transactional
    public void touch(String tenantId, Instant at, String by) {
        jdbc.update("""
                insert into tenant_calendar (tenant_id, zone_id, weekend, updated_at, updated_by)
                values (?, 'UTC', 'SATURDAY_SUNDAY', ?, ?)
                on conflict (tenant_id) do update set updated_at = excluded.updated_at, updated_by = excluded.updated_by
                """, tenantId, Timestamp.from(at), by);
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
                select id, name, kind, loaded_at, loaded_by, content_sha256, last_error, masked_url
                from tenant_calendar_source where tenant_id = ? order by lower(name)
                """, (rs, i) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new CalendarSource(id, rs.getString("name"), CalendarSource.Kind.parse(rs.getString("kind")),
                            bySource.getOrDefault(id, List.of()), instant(rs.getTimestamp("loaded_at")),
                            rs.getString("loaded_by"), rs.getString("content_sha256"), rs.getString("last_error"),
                            rs.getString("masked_url"));
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
    public void saveSource(String tenantId, CalendarSource source, String url) {
        int written = jdbc.update("""
                insert into tenant_calendar_source (id, tenant_id, name, kind, loaded_at, loaded_by, content_sha256, last_error, masked_url)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                  name = excluded.name, kind = excluded.kind, loaded_at = excluded.loaded_at,
                  loaded_by = excluded.loaded_by, content_sha256 = excluded.content_sha256, last_error = excluded.last_error,
                  masked_url = excluded.masked_url
                where tenant_calendar_source.tenant_id = excluded.tenant_id
                """, source.id(), tenantId, source.name(), source.kind().name(),
                source.loadedAt() == null ? null : Timestamp.from(source.loadedAt()), source.loadedBy(),
                source.contentSha256(), source.lastError(), source.maskedUrl());
        if (written == 0) {
            // L'identifiant existe déjà pour un autre tenant : ne rien écrire, surtout pas des périodes qui y seraient rattachées.
            throw new IllegalStateException("calendar source id is already used by another tenant");
        }
        if (url != null) {
            jdbc.update("update tenant_calendar_source set url_ciphertext = ? where tenant_id = ? and id = ?",
                    cipher.encrypt(tenantId, url), tenantId, source.id());
        } else if (source.kind() != CalendarSource.Kind.ICS_URL || source.maskedUrl() == null) {
            // Une source qui n'est (plus) une source par URL, ou dont l'URL est à ressaisir, ne garde aucun secret.
            jdbc.update("update tenant_calendar_source set url_ciphertext = null where tenant_id = ? and id = ?", tenantId, source.id());
        }
        jdbc.update("delete from tenant_calendar_entry where tenant_id = ? and source_id = ?", tenantId, source.id());
        insertEntries(tenantId, source.id(), source.closedDays());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findSourceUrl(String tenantId, UUID sourceId) {
        List<String> stored = jdbc.query("select url_ciphertext from tenant_calendar_source where tenant_id = ? and id = ?",
                (rs, i) -> rs.getString("url_ciphertext"), tenantId, sourceId);
        if (stored.isEmpty() || stored.getFirst() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(cipher.decrypt(tenantId, stored.getFirst()));
        } catch (RuntimeException unreadable) {
            // Clé changée ou valeur altérée : on demande l'URL de nouveau. Ni l'URL ni la cause n'est journalisée.
            LOG.warn("The stored URL of calendar source {} cannot be decrypted", sourceId);
            return Optional.empty();
        }
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
