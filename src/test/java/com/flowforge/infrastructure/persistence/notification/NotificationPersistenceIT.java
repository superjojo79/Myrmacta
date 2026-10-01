package com.flowforge.infrastructure.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.connector.port.out.ConnectorSecretCipher;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationOutboxRecord;
import com.flowforge.domain.notification.model.NotificationOutboxStatus;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.port.out.NotificationOutboxRepository;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.notification.port.out.PendingOutboxWrite;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class NotificationPersistenceIT {

    @Autowired
    private NotificationWebhookRepository webhooks;

    @Autowired
    private NotificationOutboxRepository outbox;

    @Autowired
    private ConnectorSecretCipher cipher;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void webhookSave_encryptsSecretAtRest_andLoadsCiphertext() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var saved = webhooks.save(new NotificationWebhookConfig(
                id,
                "tenant_dev",
                "Hook " + id,
                "https://example.com/hook",
                "plain-webhook-secret",
                true,
                null,
                now,
                now));

        assertThat(saved.secretCiphertext()).startsWith("enc:v1:");
        assertThat(saved.secretCiphertext()).doesNotContain("plain-webhook-secret");

        String raw = jdbc.queryForObject(
                "SELECT secret_ciphertext FROM notification_webhook WHERE id = ?",
                String.class,
                id);
        assertThat(raw).startsWith("enc:v1:");
        assertThat(raw).doesNotContain("plain-webhook-secret");
        assertThat(cipher.decrypt("tenant_dev", raw)).isEqualTo("plain-webhook-secret");

        assertThat(webhooks.findByIdAndTenantId(id, "tenant_dev"))
                .get()
                .extracting(NotificationWebhookConfig::secretCiphertext)
                .isEqualTo(raw);
    }

    @Test
    void softDelete_hidesFromFindAndList_butIncludingDeletedStillFinds() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        webhooks.save(new NotificationWebhookConfig(
                id,
                "tenant_dev",
                "ToDelete " + id,
                "https://example.com/del",
                null,
                true,
                null,
                now,
                now));

        webhooks.softDelete(id, "tenant_dev", now.plusSeconds(1));

        assertThat(webhooks.findByIdAndTenantId(id, "tenant_dev")).isEmpty();
        assertThat(webhooks.listByTenantId("tenant_dev")).noneMatch(w -> w.id().equals(id));
        assertThat(webhooks.findByIdAndTenantIdIncludingDeleted(id, "tenant_dev")).isPresent();
        assertThat(webhooks.existsActiveByName("tenant_dev", "ToDelete " + id, null)).isFalse();
    }

    /** D5 (TODO §79) : deux exécutants qui interrogent en même temps ne reçoivent jamais la même ligne. */
    @Test
    void outbox_lockNextPending_concurrentPollersNeverReceiveTheSameRow() throws Exception {
        Instant t = Instant.parse("2020-01-01T00:00:00Z");
        java.util.Set<UUID> created = new java.util.HashSet<>();
        for (int i = 0; i < 60; i++) {
            created.add(outbox.savePending("tenant_dev", NotificationChannel.EMAIL,
                    "{\"to\":\"a@b.c\",\"subject\":\"s\",\"body\":\"b\"}", t));
        }
        int pollers = 6;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(pollers);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<List<UUID>>> futures = new java.util.ArrayList<>();
            for (int p = 0; p < pollers; p++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    List<UUID> mine = new java.util.ArrayList<>();
                    int emptyInARow = 0;
                    for (int round = 0; round < 500 && emptyInARow < 3; round++) {
                        List<NotificationOutboxRecord> got = outbox.lockNextPending(10, t.plusSeconds(10));
                        got.forEach(r -> mine.add(r.id()));
                        emptyInARow = got.isEmpty() ? emptyInARow + 1 : 0;
                    }
                    return mine;
                }));
            }
            go.countDown();
            List<UUID> all = new java.util.ArrayList<>();
            for (var f : futures) {
                all.addAll(f.get());
            }
            for (List<NotificationOutboxRecord> rest = outbox.lockNextPending(100, t.plusSeconds(10));
                    !rest.isEmpty(); rest = outbox.lockNextPending(100, t.plusSeconds(10))) {
                rest.forEach(r -> all.add(r.id()));
            }
            assertThat(all).as("aucune ligne reçue par deux exécutants").doesNotHaveDuplicates();
            assertThat(new java.util.HashSet<>(all)).containsAll(created);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void outbox_aClaimedRowBecomesEligibleAgainOnlyAfterTheLease() {
        Instant t = Instant.parse("2020-02-01T00:00:00Z");
        UUID id = outbox.savePending("tenant_dev", NotificationChannel.EMAIL,
                "{\"to\":\"a@b.c\",\"subject\":\"s\",\"body\":\"b\"}", t);
        Instant poll = t.plusSeconds(5);

        assertThat(outbox.lockNextPending(500, poll)).anyMatch(r -> r.id().equals(id));
        assertThat(outbox.lockNextPending(500, poll.plusSeconds(60))).noneMatch(r -> r.id().equals(id));
        assertThat(outbox.lockNextPending(500, poll.plusSeconds(5 * 60 + 1))).anyMatch(r -> r.id().equals(id));
        outbox.markProcessed(id, poll.plusSeconds(400));
    }

    @Test
    void outbox_savePending_lock_backoff_dead_and_purge() {
        Instant t0 = Instant.parse("2026-08-07T12:00:00Z");
        UUID id = outbox.savePending(
                "tenant_dev", NotificationChannel.EMAIL, "{\"to\":\"a@b.c\",\"subject\":\"s\",\"body\":\"b\"}", t0);

        List<NotificationOutboxRecord> locked = outbox.lockNextPending(20, t0);
        assertThat(locked).anyMatch(r -> r.id().equals(id));
        assertThat(locked.stream().filter(r -> r.id().equals(id)).findFirst())
                .get()
                .extracting(NotificationOutboxRecord::status, NotificationOutboxRecord::attempts)
                .containsExactly(NotificationOutboxStatus.PENDING, 0);

        // future next_attempt_at should not be eligible
        outbox.recordFailure(id, "smtp down", 3, t0);
        assertThat(outbox.lockNextPending(20, t0)).noneMatch(r -> r.id().equals(id));

        List<NotificationOutboxRecord> after1s = outbox.lockNextPending(20, t0.plusSeconds(1));
        assertThat(after1s.stream().filter(r -> r.id().equals(id)).findFirst())
                .get()
                .extracting(NotificationOutboxRecord::attempts, NotificationOutboxRecord::status)
                .containsExactly(1, NotificationOutboxStatus.PENDING);

        outbox.recordFailure(id, "smtp down again", 3, t0.plusSeconds(1));
        List<NotificationOutboxRecord> after2s = outbox.lockNextPending(20, t0.plusSeconds(1).plusSeconds(2));
        assertThat(after2s.stream().filter(r -> r.id().equals(id)).findFirst())
                .get()
                .extracting(NotificationOutboxRecord::attempts)
                .isEqualTo(2);

        Instant deadAt = t0.plusSeconds(10);
        outbox.recordFailure(id, "final", 3, deadAt);
        String status = jdbc.queryForObject(
                "SELECT status FROM notification_outbox WHERE id = ?", String.class, id);
        assertThat(status).isEqualTo("DEAD");

        Instant cutoff = deadAt.plusSeconds(1);
        int purged = outbox.purgeTerminalOlderThan(cutoff);
        assertThat(purged).isGreaterThanOrEqualTo(1);
        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE id = ?", Integer.class, id);
        assertThat(remaining).isZero();
    }

    @Test
    void outbox_markProcessed_thenPurgeUsesProcessedAt() {
        Instant now = Instant.parse("2026-08-07T13:00:00Z");
        UUID id = outbox.savePending(
                "tenant_dev", NotificationChannel.BROWSER, "{\"userId\":\"u1\",\"payload\":{}}", now);
        outbox.markProcessed(id, now);
        assertThat(outbox.purgeTerminalOlderThan(now.plusSeconds(1))).isGreaterThanOrEqualTo(1);
    }

    @Test
    void outbox_savePendingBatch_persistsAllRows() {
        Instant now = Instant.parse("2026-08-07T14:00:00Z");
        List<UUID> ids = outbox.savePendingBatch(
                "tenant_dev",
                List.of(
                        new PendingOutboxWrite(
                                NotificationChannel.EMAIL,
                                "{\"to\":\"a@b.c\",\"subject\":\"s\",\"body\":\"b\"}"),
                        new PendingOutboxWrite(
                                NotificationChannel.BROWSER, "{\"userId\":\"u1\",\"payload\":{}}")),
                now);

        assertThat(ids).hasSize(2);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE id IN (?, ?)",
                Integer.class,
                ids.get(0),
                ids.get(1));
        assertThat(count).isEqualTo(2);

        // Nettoyage (trouvé en marge de la revue adversariale connecteurs-cible) : cette méthode
        // laissait 2 lignes PENDING en base indéfiniment, polluant oldestPendingCreatedAt() —
        // lecture globale, pas par tenant (D-OBS) — pour tout test exécuté après celui-ci dans le
        // même run (flake d'ordre d'exécution trouvé sur oldestPendingAndWebhookDeadCount).
        ids.forEach(id -> outbox.markProcessed(id, now.plusSeconds(1)));
        assertThat(outbox.purgeTerminalOlderThan(now.plusSeconds(2))).isGreaterThanOrEqualTo(2);
    }

    @Test
    void oldestPendingAndWebhookDeadCount() {
        // Isolation (flake trouvé en marge de la revue adversariale connecteurs-cible) :
        // oldestPendingCreatedAt() est une lecture globale, pas par tenant (D-OBS,
        // findOldestCreatedAtByStatus n'a pas de clause tenant) — une ligne PENDING oubliée par
        // un autre test de cette classe (ex. outbox_savePendingBatch_persistsAllRows avant son
        // propre nettoyage) rendait cette assertion dépendante de l'ordre d'exécution des
        // méthodes JUnit5 (non garanti). On repart d'un état propre pour que ce test ne possède
        // que les lignes PENDING qu'il crée lui-même.
        jdbc.update("DELETE FROM notification_outbox WHERE status = 'PENDING'");

        Instant now = Instant.parse("2026-08-20T10:00:00Z");
        UUID pending = outbox.savePending(
                "tenant_dev",
                NotificationChannel.EMAIL,
                "{\"to\":\"a@b.c\",\"subject\":\"s\",\"body\":\"b\"}",
                now);
        assertThat(outbox.oldestPendingCreatedAt()).contains(now);
        UUID webhookDead = outbox.savePending(
                "tenant_dev",
                NotificationChannel.WEBHOOK,
                "{\"webhookConfigId\":\"" + UUID.randomUUID() + "\",\"payload\":{}}",
                now.plusSeconds(1));
        outbox.markDead(webhookDead, "target down", now.plusSeconds(2));
        UUID emailDead = outbox.savePending(
                "tenant_dev",
                NotificationChannel.EMAIL,
                "{\"to\":\"a@b.c\",\"subject\":\"s\",\"body\":\"b\"}",
                now.plusSeconds(3));
        outbox.markDead(emailDead, "smtp", now.plusSeconds(4));
        assertThat(outbox.countByChannelAndStatus(NotificationChannel.WEBHOOK, NotificationOutboxStatus.DEAD))
                .isGreaterThanOrEqualTo(1);
        assertThat(pending).isNotNull();

        // Nettoyage : ne pas laisser de ligne PENDING/DEAD permanente dans la table partagée.
        outbox.markProcessed(pending, now.plusSeconds(5));
        assertThat(outbox.purgeTerminalOlderThan(now.plusSeconds(6))).isGreaterThanOrEqualTo(3);
    }

    @Test
    void webhook_activeNameUniquePerTenant() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String name = "UniqueHook " + UUID.randomUUID();
        webhooks.save(new NotificationWebhookConfig(
                UUID.randomUUID(),
                "tenant_dev",
                name,
                "https://example.com/a",
                null,
                true,
                null,
                now,
                now));

        NotificationWebhookConfig duplicate = new NotificationWebhookConfig(
                UUID.randomUUID(),
                "tenant_dev",
                name,
                "https://example.com/b",
                null,
                true,
                null,
                now,
                now);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> webhooks.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pageByTenantId_ordersByNameExcludesDeletedAndKeepsTotalsPastEnd() {
        String token = UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID deletedId = UUID.randomUUID();
        webhooks.save(webhook("aaa-hook-" + token, now));
        webhooks.save(webhook("mmm-hook-" + token, now));
        webhooks.save(webhook("zzz-hook-" + token, now));
        webhooks.save(new NotificationWebhookConfig(
                deletedId,
                "tenant_dev",
                "del-hook-" + token,
                "https://example.com/del-" + token,
                null,
                true,
                null,
                now,
                now));
        webhooks.softDelete(deletedId, "tenant_dev", now.plusSeconds(1));

        var page0 = webhooks.pageByTenantId("tenant_dev", PageQuery.of(0, 100));
        List<String> names = page0.content().stream()
                .map(NotificationWebhookConfig::name)
                .filter(name -> name.endsWith(token))
                .toList();
        assertThat(names).containsExactly("aaa-hook-" + token, "mmm-hook-" + token, "zzz-hook-" + token);
        assertThat(page0.content()).noneMatch(hook -> hook.id().equals(deletedId));

        var pastEnd = webhooks.pageByTenantId("tenant_dev", PageQuery.of(9_999, 20));
        assertThat(pastEnd.content()).isEmpty();
        assertThat(pastEnd.totalElements()).isEqualTo(page0.totalElements());
    }

    private static NotificationWebhookConfig webhook(String name, Instant now) {
        return new NotificationWebhookConfig(
                UUID.randomUUID(),
                "tenant_dev",
                name,
                "https://example.com/" + name,
                null,
                true,
                null,
                now,
                now);
    }
}
