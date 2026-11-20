package be.myrmacta.infrastructure.persistence.mailroom;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.mailroom.model.EboxTakenMessage;
import be.myrmacta.domain.mailroom.port.out.EboxReadJournal;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** TODO §150 (D-EBOX-JOURNAL, D-EBOX-WINDOW) — journal par eBox et curseur par instance, sur PostgreSQL (changeset 053). */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class EboxReadJournalPersistenceIT {

    private static final String TENANT = "tenant_dev";

    @Autowired private EboxReadJournal journal;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void aMessageIsTakenOncePerEbox_whateverTheInstance() {
        String bce = "0" + (100_000_000 + (int) (Math.random() * 899_999_999));
        String id = "m-" + UUID.randomUUID();
        assertThat(journal.recordTaken(taken(bce, "socsec_v3", id, UUID.randomUUID()))).isTrue();
        assertThat(journal.recordTaken(taken(bce, "socsec_v3", id, UUID.randomUUID()))).isFalse();

        assertThat(journal.recordTaken(taken(bce, "minfin_v3", id, UUID.randomUUID()))).isTrue();
        assertThat(journal.recordTaken(taken("1" + bce.substring(1), "socsec_v3", id, UUID.randomUUID()))).isTrue();

        assertThat(journal.alreadyTaken(TENANT, bce, "socsec_v3", List.of(id, "absent"))).containsExactly(id);
        assertThat(journal.alreadyTaken(TENANT, bce, "autre", List.of(id))).isEmpty();
    }

    @Test
    void anEntryGivesThePartitionItWasReadFrom() {
        String bce = "0" + (100_000_000 + (int) (Math.random() * 899_999_999));
        String id = "m-" + UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        journal.recordTaken(taken(bce, "socsec_v3", id, instance));

        assertThat(journal.taken(TENANT, bce, "socsec_v3", id)).get()
                .satisfies(entry -> {
                    assertThat(entry.partition()).isEqualTo("myrmacta");
                    assertThat(entry.connectorInstanceId()).isEqualTo(instance);
                });
        assertThat(journal.taken(TENANT, bce, "autre", id)).isEmpty();
        assertThat(journal.taken("autre_tenant", bce, "socsec_v3", id)).isEmpty();
    }

    @Test
    void theLookupIsBatchedBeyondOnePage() {
        String bce = "0" + (100_000_000 + (int) (Math.random() * 899_999_999));
        List<String> ids = IntStream.range(0, 450).mapToObj(i -> "m-" + i + "-" + UUID.randomUUID()).toList();
        ids.forEach(id -> journal.recordTaken(taken(bce, "socsec_v3", id, UUID.randomUUID())));

        assertThat(journal.alreadyTaken(TENANT, bce, "socsec_v3", ids)).hasSize(450);
    }

    @Test
    void theCursorNeverMovesBack_andGoesWithItsInstance() {
        UUID instance = instance();
        assertThat(journal.readUntil(instance, "socsec_v3", "myrmacta")).isEmpty();

        journal.advance(instance, "socsec_v3", "myrmacta", LocalDate.of(2026, 11, 20));
        journal.advance(instance, "socsec_v3", "myrmacta", LocalDate.of(2026, 11, 18));
        assertThat(journal.readUntil(instance, "socsec_v3", "myrmacta")).contains(LocalDate.of(2026, 11, 20));
        assertThat(journal.readUntil(instance, "socsec_v3", "rh")).isEmpty();

        jdbc.update("delete from connector_instance where id = ?", instance);
        assertThat(journal.readUntil(instance, "socsec_v3", "myrmacta")).isEmpty();
    }

    private static EboxTakenMessage taken(String bce, String registry, String id, UUID instance) {
        return new EboxTakenMessage(TENANT, bce, registry, id, "myrmacta", instance, Instant.parse("2026-11-20T09:00:00Z"));
    }

    private UUID instance() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into connector_instance (id, tenant_id, name, connector_type, enabled, config, created_at, updated_at)
                values (?, ?, ?, 'ebox-inbound', false, '{}', now(), now())
                """, id, TENANT, "eBox " + id);
        return id;
    }
}
