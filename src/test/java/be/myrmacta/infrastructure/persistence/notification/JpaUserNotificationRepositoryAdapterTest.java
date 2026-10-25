package be.myrmacta.infrastructure.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.notification.model.UserNotificationRecord;
import be.myrmacta.domain.notification.port.out.UserNotificationRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JpaUserNotificationRepositoryAdapterTest {

    @Autowired
    private UserNotificationRepository repository;

    private static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder().build();

    @Test
    void save_persistsUnreadNotification() {
        Instant now = Instant.parse("2026-09-22T10:00:00Z");
        String recipient = "user-" + System.nanoTime();

        UUID id = repository.save(UUID.randomUUID(), "tenant_dev", recipient, "{\"type\":\"X\"}", now);

        PageResult<UserNotificationRecord> page = repository.page("tenant_dev", recipient, PageQuery.defaults());
        assertThat(page.content()).hasSize(1);
        UserNotificationRecord record = page.content().getFirst();
        assertThat(record.id()).isEqualTo(id);
        assertThat(record.tenantId()).isEqualTo("tenant_dev");
        assertThat(record.recipient()).isEqualTo(recipient);
        // jsonb (PostgreSQL) normalise la mise en forme : comparaison sémantique, pas textuelle.
        assertThat(JSON.readTree(record.payloadJson()))
                .isEqualTo(JSON.readTree("{\"type\":\"X\"}"));
        assertThat(record.createdAt()).isEqualTo(now);
        assertThat(record.readAt()).isNull();
        assertThat(record.isRead()).isFalse();
    }

    @Test
    void markRead_ownNotification_returnsTrueAndPersistsReadAt() {
        String recipient = "user-" + System.nanoTime();
        UUID id = repository.save(
                UUID.randomUUID(), "tenant_dev", recipient, "{}", Instant.parse("2026-09-22T10:00:00Z"));
        Instant readAt = Instant.parse("2026-09-22T11:00:00Z");

        boolean updated = repository.markRead(id, "tenant_dev", recipient, readAt);

        assertThat(updated).isTrue();
        UserNotificationRecord record =
                repository.page("tenant_dev", recipient, PageQuery.defaults()).content().getFirst();
        assertThat(record.readAt()).isEqualTo(readAt);
        assertThat(record.isRead()).isTrue();
    }

    @Test
    void markRead_belongsToAnotherRecipient_returnsFalseWithoutMutating() {
        String owner = "owner-" + System.nanoTime();
        String stranger = "stranger-" + System.nanoTime();
        UUID id = repository.save(
                UUID.randomUUID(), "tenant_dev", owner, "{}", Instant.parse("2026-09-22T10:00:00Z"));

        boolean updated = repository.markRead(id, "tenant_dev", stranger, Instant.parse("2026-09-22T11:00:00Z"));

        assertThat(updated).isFalse();
        UserNotificationRecord record =
                repository.page("tenant_dev", owner, PageQuery.defaults()).content().getFirst();
        assertThat(record.readAt()).isNull();
    }

    @Test
    void markRead_calledTwice_keepsTheOriginalReadAt() {
        // TODO §66 F7 (Phase 10 adversarial review): a repeat markRead (stale client resend, two
        // tabs racing) must not keep sliding readAt forward.
        String recipient = "user-" + System.nanoTime();
        UUID id = repository.save(
                UUID.randomUUID(), "tenant_dev", recipient, "{}", Instant.parse("2026-09-22T10:00:00Z"));
        Instant firstReadAt = Instant.parse("2026-09-22T11:00:00Z");
        Instant secondReadAt = Instant.parse("2026-09-22T12:00:00Z");

        repository.markRead(id, "tenant_dev", recipient, firstReadAt);
        repository.markRead(id, "tenant_dev", recipient, secondReadAt);

        UserNotificationRecord record =
                repository.page("tenant_dev", recipient, PageQuery.defaults()).content().getFirst();
        assertThat(record.readAt()).isEqualTo(firstReadAt);
    }

    @Test
    void markRead_unknownId_returnsFalse() {
        boolean updated = repository.markRead(
                UUID.randomUUID(), "tenant_dev", "nobody", Instant.parse("2026-09-22T11:00:00Z"));

        assertThat(updated).isFalse();
    }

    @Test
    void page_ordersByCreatedAtDescending() {
        String recipient = "user-" + System.nanoTime();
        Instant t0 = Instant.parse("2026-09-22T10:00:00Z");
        UUID older = repository.save(UUID.randomUUID(), "tenant_dev", recipient, "{\"n\":1}", t0);
        UUID newer = repository.save(UUID.randomUUID(), "tenant_dev", recipient, "{\"n\":2}", t0.plusSeconds(60));

        PageResult<UserNotificationRecord> page = repository.page("tenant_dev", recipient, PageQuery.defaults());

        assertThat(page.content()).extracting(UserNotificationRecord::id).containsExactly(newer, older);
    }

    @Test
    void page_scopedToRecipientAndTenant_neverLeaksOtherUsersOrTenants() {
        String recipient = "user-" + System.nanoTime();
        Instant now = Instant.parse("2026-09-22T10:00:00Z");
        repository.save(UUID.randomUUID(), "tenant_dev", recipient, "{}", now);
        repository.save(UUID.randomUUID(), "tenant_dev", "someone-else-" + System.nanoTime(), "{}", now);

        PageResult<UserNotificationRecord> page = repository.page("tenant_dev", recipient, PageQuery.defaults());

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().recipient()).isEqualTo(recipient);
    }

    @Test
    void page_scopedToTenant_sameRecipientSubjectAcrossTenantsStaysIsolated() {
        // IT-NOTIF-2 — multi-tenant isolation verified at the SQL layer: the app itself is
        // single-tenant-per-instance (app.tenant.id fixed at boot, decision n°13), so two tenants
        // can never coexist behind one REST context to exercise this via an HTTP round trip.
        String sameRecipient = "shared-sub-" + System.nanoTime();
        Instant now = Instant.parse("2026-09-22T10:00:00Z");
        repository.save(UUID.randomUUID(), "tenant_a", sameRecipient, "{\"tenant\":\"a\"}", now);
        repository.save(UUID.randomUUID(), "tenant_b", sameRecipient, "{\"tenant\":\"b\"}", now);

        PageResult<UserNotificationRecord> tenantAPage =
                repository.page("tenant_a", sameRecipient, PageQuery.defaults());
        PageResult<UserNotificationRecord> tenantBPage =
                repository.page("tenant_b", sameRecipient, PageQuery.defaults());

        assertThat(tenantAPage.content()).hasSize(1);
        assertThat(tenantAPage.content().getFirst().payloadJson()).contains("\"a\"");
        assertThat(tenantBPage.content()).hasSize(1);
        assertThat(tenantBPage.content().getFirst().payloadJson()).contains("\"b\"");
    }

    @Test
    void countUnread_onlyCountsReadAtNull() {
        String recipient = "user-" + System.nanoTime();
        Instant now = Instant.parse("2026-09-22T10:00:00Z");
        UUID read = repository.save(UUID.randomUUID(), "tenant_dev", recipient, "{}", now);
        repository.save(UUID.randomUUID(), "tenant_dev", recipient, "{}", now);
        repository.markRead(read, "tenant_dev", recipient, now.plusSeconds(1));

        assertThat(repository.countUnread("tenant_dev", recipient)).isEqualTo(1);
    }

    @Test
    void markAllRead_marksOnlyThatRecipientsUnreadNotifications() {
        String recipient = "user-" + System.nanoTime();
        String other = "other-" + System.nanoTime();
        Instant now = Instant.parse("2026-09-22T10:00:00Z");
        repository.save(UUID.randomUUID(), "tenant_dev", recipient, "{}", now);
        repository.save(UUID.randomUUID(), "tenant_dev", recipient, "{}", now);
        repository.save(UUID.randomUUID(), "tenant_dev", other, "{}", now);

        int updated = repository.markAllRead("tenant_dev", recipient, now.plusSeconds(1));

        assertThat(updated).isEqualTo(2);
        assertThat(repository.countUnread("tenant_dev", recipient)).isZero();
        assertThat(repository.countUnread("tenant_dev", other)).isEqualTo(1);
    }

    @Test
    void markAllRead_idempotent_secondCallUpdatesNothing() {
        String recipient = "user-" + System.nanoTime();
        Instant now = Instant.parse("2026-09-22T10:00:00Z");
        repository.save(UUID.randomUUID(), "tenant_dev", recipient, "{}", now);
        repository.markAllRead("tenant_dev", recipient, now.plusSeconds(1));

        int secondCall = repository.markAllRead("tenant_dev", recipient, now.plusSeconds(2));

        assertThat(secondCall).isZero();
    }

    @Test
    void purgeOlderThan_deletesOnlyOlderRows_readOrUnread() {
        String recipient = "user-" + System.nanoTime();
        Instant old = Instant.parse("2026-08-01T00:00:00Z");
        Instant recent = Instant.parse("2026-09-20T00:00:00Z");
        UUID oldUnread = repository.save(UUID.randomUUID(), "tenant_dev", recipient, "{}", old);
        UUID oldRead =
                repository.save(UUID.randomUUID(), "tenant_dev", recipient, "{}", old.minus(1, ChronoUnit.DAYS));
        repository.markRead(oldRead, "tenant_dev", recipient, old);
        UUID keepRecent = repository.save(UUID.randomUUID(), "tenant_dev", recipient, "{}", recent);

        int purged = repository.purgeOlderThan(Instant.parse("2026-09-01T00:00:00Z"));

        assertThat(purged).isGreaterThanOrEqualTo(2);
        PageResult<UserNotificationRecord> remaining =
                repository.page("tenant_dev", recipient, PageQuery.of(0, 50));
        assertThat(remaining.content()).extracting(UserNotificationRecord::id).contains(keepRecent);
        assertThat(remaining.content())
                .extracting(UserNotificationRecord::id)
                .doesNotContain(oldUnread, oldRead);
    }
}
