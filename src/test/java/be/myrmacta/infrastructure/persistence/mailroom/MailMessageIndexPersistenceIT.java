package be.myrmacta.infrastructure.persistence.mailroom;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import be.myrmacta.domain.mailroom.model.MailIndexEntry.Direction;
import be.myrmacta.domain.mailroom.model.MailIndexEntry.Kind;
import be.myrmacta.domain.mailroom.port.out.MailMessageIndex;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** TODO §113 (D-MAILROOM-INDEX) — index des e-mails : idempotence, ordre de lecture, bornes, isolation du tenant. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class MailMessageIndexPersistenceIT {

    private static final String TENANT = "tenant_dev";
    private static final String OTHER = "tenant_autre";

    @Autowired
    private MailMessageIndex index;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> dossiers = new ArrayList<>();

    @AfterEach
    void clean() {
        dossiers.forEach(id -> jdbc.update("delete from dossier where id = ?", id));
        dossiers.clear();
    }

    private UUID dossier() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into dossier (id, tenant_id, reference, dossier_type_code, label, status, fields, version, created_by, created_at,
                                     updated_by, updated_at)
                values (?, ?, ?, 'COURRIER', 'x', 'OUVERT', '{}', 0, 't', now(), 't', now())
                """, id, TENANT, "DOS-IDX-" + id.toString().substring(0, 8));
        dossiers.add(id);
        return id;
    }

    private static String key() {
        return "<" + UUID.randomUUID() + "@exemple.test>";
    }

    private static MailIndexEntry entry(String tenant, Kind kind, String key, Direction direction, UUID dossierId, String counterpart, String at) {
        return new MailIndexEntry(tenant, kind, key, direction, dossierId, counterpart, Instant.parse(at));
    }

    @Test
    void recordingTheSameEntryTwiceKeepsOneRow() {
        UUID d = dossier();
        String key = key();
        MailIndexEntry entry = entry(TENANT, Kind.MESSAGE_ID, key, Direction.OUT, d, "a@exemple.test", "2026-11-01T08:00:00Z");

        index.record(entry);
        index.record(entry);

        assertThat(jdbc.queryForObject("select count(*) from mail_message_index where message_key = ?", Integer.class, key)).isEqualTo(1);
    }

    @Test
    void anEntryIsReadBackWithAllItsFields_andTheCounterpartIsLowercased() {
        UUID d = dossier();
        String key = key();

        index.record(entry(TENANT, Kind.MESSAGE_ID, key, Direction.IN, d, "  Compta@Fournisseur.EXAMPLE ", "2026-11-01T08:00:00Z"));

        MailIndexEntry found = index.find(TENANT, Kind.MESSAGE_ID, key).orElseThrow();
        assertThat(found).isEqualTo(entry(TENANT, Kind.MESSAGE_ID, key, Direction.IN, d, "compta@fournisseur.example", "2026-11-01T08:00:00Z"));
    }

    @Test
    void aMissingCounterpartRoundTripsAsNull() {
        UUID d = dossier();
        String key = key();

        index.record(entry(TENANT, Kind.CONVERSATION, key, Direction.IN, d, null, "2026-11-01T08:00:00Z"));

        assertThat(index.find(TENANT, Kind.CONVERSATION, key).orElseThrow().counterpart()).isNull();
    }

    @Test
    void theSameKeyOfAnotherKindIsAnotherEntry() {
        UUID d = dossier();
        UUID e = dossier();
        String key = key();
        index.record(entry(TENANT, Kind.MESSAGE_ID, key, Direction.IN, d, null, "2026-11-01T08:00:00Z"));
        index.record(entry(TENANT, Kind.CONVERSATION, key, Direction.IN, e, null, "2026-11-01T09:00:00Z"));

        assertThat(index.find(TENANT, Kind.MESSAGE_ID, key).orElseThrow().dossierId()).isEqualTo(d);
        assertThat(index.find(TENANT, Kind.CONVERSATION, key).orElseThrow().dossierId()).isEqualTo(e);
    }

    @Test
    void anUnknownKeyIsNotFound() {
        assertThat(index.find(TENANT, Kind.MESSAGE_ID, key())).isEmpty();
    }

    @Test
    void whenSeveralEntriesShareAKeyOutboundComesFirstThenTheOldest() {
        UUID a = dossier();
        UUID b = dossier();
        UUID c = dossier();
        String key = key();
        index.record(entry(TENANT, Kind.MESSAGE_ID, key, Direction.OUT, a, null, "2026-11-01T12:00:00Z"));
        index.record(entry(TENANT, Kind.MESSAGE_ID, key, Direction.OUT, b, null, "2026-11-01T10:00:00Z"));
        index.record(entry(TENANT, Kind.MESSAGE_ID, key, Direction.OUT, c, null, "2026-11-01T11:00:00Z"));

        assertThat(index.find(TENANT, Kind.MESSAGE_ID, key).orElseThrow().dossierId()).isEqualTo(b);
    }

    @Test
    void findByDossierIsNewestFirstAndBoundedBetweenOneAndTwoHundred() {
        UUID d = dossier();
        for (int i = 0; i < 5; i++) {
            index.record(entry(TENANT, Kind.MESSAGE_ID, key(), Direction.IN, d, null, "2026-11-01T0" + i + ":00:00Z"));
        }

        List<MailIndexEntry> three = index.findByDossier(TENANT, d, 3);
        assertThat(three).hasSize(3);
        assertThat(three).extracting(MailIndexEntry::recordedAt)
                .containsExactly(Instant.parse("2026-11-01T04:00:00Z"), Instant.parse("2026-11-01T03:00:00Z"), Instant.parse("2026-11-01T02:00:00Z"));
        assertThat(index.findByDossier(TENANT, d, 0)).hasSize(1);
        assertThat(index.findByDossier(TENANT, d, -5)).hasSize(1);
        assertThat(index.findByDossier(TENANT, d, 100_000)).hasSize(5);
        assertThat(index.findByDossier(TENANT, UUID.randomUUID(), 10)).isEmpty();
    }

    @Test
    void anotherTenantsEntriesAreNeverFoundNorListed() {
        UUID d = dossier();
        String key = key();
        index.record(entry(OTHER, Kind.MESSAGE_ID, key, Direction.IN, d, null, "2026-11-01T08:00:00Z"));

        assertThat(index.find(TENANT, Kind.MESSAGE_ID, key)).isEmpty();
        assertThat(index.findByDossier(TENANT, d, 10)).isEmpty();
        assertThat(index.find(OTHER, Kind.MESSAGE_ID, key)).isPresent();
    }

    @Test
    void entriesGoWithTheirDossier() {
        UUID d = dossier();
        String key = key();
        index.record(entry(TENANT, Kind.MESSAGE_ID, key, Direction.IN, d, null, "2026-11-01T08:00:00Z"));

        jdbc.update("delete from dossier where id = ?", d);

        assertThat(index.find(TENANT, Kind.MESSAGE_ID, key)).isEmpty();
    }
}
