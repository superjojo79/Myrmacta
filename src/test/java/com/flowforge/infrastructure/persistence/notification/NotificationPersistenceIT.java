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
