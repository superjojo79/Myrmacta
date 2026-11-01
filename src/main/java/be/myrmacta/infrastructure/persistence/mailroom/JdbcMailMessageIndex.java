package be.myrmacta.infrastructure.persistence.mailroom;

import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import be.myrmacta.domain.mailroom.port.out.MailMessageIndex;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** TODO §113 (D-MAILROOM-INDEX) — table {@code mail_message_index} (changeset 043). */
@Component
class JdbcMailMessageIndex implements MailMessageIndex {

    private final JdbcTemplate jdbc;

    JdbcMailMessageIndex(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(MailIndexEntry entry) {
        jdbc.update("""
                insert into mail_message_index (id, tenant_id, kind, message_key, direction, dossier_id, counterpart, recorded_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (tenant_id, kind, message_key, direction, dossier_id) do nothing
                """, UUID.randomUUID(), entry.tenantId(), entry.kind().name(), entry.key(), entry.direction().name(), entry.dossierId(),
                entry.counterpart(), Timestamp.from(entry.recordedAt()));
    }

    @Override
    public Optional<MailIndexEntry> find(String tenantId, MailIndexEntry.Kind kind, String key) {
        List<MailIndexEntry> found = jdbc.query("""
                select tenant_id, kind, message_key, direction, dossier_id, counterpart, recorded_at from mail_message_index
                where tenant_id = ? and kind = ? and message_key = ? order by recorded_at desc limit 1
                """, (rs, i) -> new MailIndexEntry(rs.getString("tenant_id"), MailIndexEntry.Kind.valueOf(rs.getString("kind")),
                        rs.getString("message_key"), MailIndexEntry.Direction.valueOf(rs.getString("direction")),
                        rs.getObject("dossier_id", UUID.class), rs.getString("counterpart"), rs.getTimestamp("recorded_at").toInstant()),
                tenantId, kind.name(), key);
        return found.stream().findFirst();
    }

    @Override
    public List<MailIndexEntry> findByDossier(String tenantId, UUID dossierId, int limit) {
        return jdbc.query("""
                select tenant_id, kind, message_key, direction, dossier_id, counterpart, recorded_at from mail_message_index
                where tenant_id = ? and dossier_id = ? order by recorded_at desc limit ?
                """, (rs, i) -> new MailIndexEntry(rs.getString("tenant_id"), MailIndexEntry.Kind.valueOf(rs.getString("kind")),
                        rs.getString("message_key"), MailIndexEntry.Direction.valueOf(rs.getString("direction")),
                        rs.getObject("dossier_id", UUID.class), rs.getString("counterpart"), rs.getTimestamp("recorded_at").toInstant()),
                tenantId, dossierId, Math.max(1, Math.min(limit, 200)));
    }
}
