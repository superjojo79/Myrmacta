package be.myrmacta.infrastructure.persistence.mailroom;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import be.myrmacta.domain.mailroom.port.out.MailMessageIndex;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** TODO §113 (D-MAILROOM-INDEX, revue de clôture) — un Message-ID reçu réutilisé par un tiers ne masque pas l'entrée du fil. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class MailMessageIndexShadowingIT {

    private static final String TENANT = "tenant_dev";

    @Autowired private MailMessageIndex index;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void aReusedInboundMessageIdDoesNotShadowTheThread_anOutboundOneWins() {
        UUID thread = dossier();
        UUID intruder = dossier();
        String key = "<" + UUID.randomUUID() + "@fournisseur.example>";
        index.record(entry(key, MailIndexEntry.Direction.IN, thread, "compta@fournisseur.example", "2026-11-01T08:00:00Z"));
        index.record(entry(key, MailIndexEntry.Direction.IN, intruder, "escroc@autre.example", "2026-11-01T09:00:00Z"));

        assertThat(index.find(TENANT, MailIndexEntry.Kind.MESSAGE_ID, key)).get().extracting(MailIndexEntry::dossierId).isEqualTo(thread);
        assertThat(index.findByDossier(TENANT, intruder, 10)).isEmpty();

        String sent = "<" + UUID.randomUUID() + "@myrmacta.example>";
        index.record(entry(sent, MailIndexEntry.Direction.IN, intruder, "escroc@autre.example", "2026-11-01T07:00:00Z"));
        index.record(entry(sent, MailIndexEntry.Direction.OUT, thread, "compta@fournisseur.example", "2026-11-01T10:00:00Z"));
        assertThat(index.find(TENANT, MailIndexEntry.Kind.MESSAGE_ID, sent)).get().extracting(MailIndexEntry::dossierId).isEqualTo(thread);
    }

    private UUID dossier() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into dossier (id, tenant_id, reference, dossier_type_code, label, status, fields, version, created_by, created_at,
                                     updated_by, updated_at)
                values (?, ?, ?, 'COURRIER', 'x', 'OUVERT', '{}', 0, 't', now(), 't', now())
                """, id, TENANT, "DOS-IDX-" + id.toString().substring(0, 8));
        return id;
    }

    private static MailIndexEntry entry(String key, MailIndexEntry.Direction direction, UUID dossierId, String counterpart, String at) {
        return new MailIndexEntry(TENANT, MailIndexEntry.Kind.MESSAGE_ID, key, direction, dossierId, counterpart, Instant.parse(at));
    }
}
