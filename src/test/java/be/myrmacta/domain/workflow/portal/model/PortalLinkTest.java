package be.myrmacta.domain.workflow.portal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortalLinkTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    static PortalLink pending(int ttlDays) {
        return PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", UUID.randomUUID(), "task-1", "pi-1",
                "avis", "alice@example.org", ttlDays, NOW, "flowable-task-created");
    }

    @Test
    void activated_setsTokenHashAndExpiryFromSendTime() {
        PortalLink active = pending(7).activated("h".repeat(64), NOW);

        assertThat(active.status()).isEqualTo(PortalLinkStatus.ACTIVE);
        assertThat(active.expiresAt()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(active.sendAttempts()).isEqualTo(1);
        assertThat(active.isUsable(NOW)).isTrue();
        assertThat(active.isUsable(Instant.parse("2026-10-01T10:00:00Z"))).isFalse();
    }

    @Test
    void sendFailed_retriesThenGivesUpAtMaxAttempts() {
        PortalLink link = pending(7);
        link = link.sendFailed(3);
        assertThat(link.status()).isEqualTo(PortalLinkStatus.PENDING_SEND);
        link = link.sendFailed(3);
        assertThat(link.status()).isEqualTo(PortalLinkStatus.PENDING_SEND);
        link = link.sendFailed(3);

        assertThat(link.status()).isEqualTo(PortalLinkStatus.NOT_SENT);
        assertThat(link.statusReason()).isEqualTo(PortalLinkStatusReason.SEND_FAILED);
        assertThat(link.sendAttempts()).isEqualTo(3);
    }

    @Test
    void transitionsFromWrongStatus_areRejected() {
        PortalLink pending = pending(7);
        assertThatThrownBy(() -> pending.completed(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(pending::expired).isInstanceOf(IllegalStateException.class);
        PortalLink completed = pending.activated("h".repeat(64), NOW).completed(NOW);
        assertThatThrownBy(() -> completed.revoked(NOW, "u1", PortalLinkStatusReason.REVOKED_BY_USER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revoked_isAllowedFromEveryLiveStatus() {
        PortalLink pending = pending(7);
        PortalLink active = pending.activated("h".repeat(64), NOW);
        PortalLink notSent = PortalLink.notSent(UUID.randomUUID(), "tenant_dev", UUID.randomUUID(), "task-2", "pi",
                "avis", null, 7, PortalLinkStatusReason.NO_ADDRESS, NOW, "flowable-task-created");

        for (PortalLink live : new PortalLink[] {pending, active, notSent}) {
            PortalLink revoked = live.revoked(NOW, "u1", PortalLinkStatusReason.REVOKED_BY_USER);
            assertThat(revoked.status()).isEqualTo(PortalLinkStatus.REVOKED);
            assertThat(revoked.revokedBy()).isEqualTo("u1");
            assertThat(revoked.isUsable(NOW)).isFalse();
        }
    }

    @Test
    void activeLink_requiresTokenHashAndExpiry() {
        PortalLink p = pending(7);
        assertThatThrownBy(() -> new PortalLink(p.id(), p.tenantId(), p.dossierId(), p.taskId(), p.processInstanceId(),
                        p.schemaKey(), p.recipientEmail(), null, PortalLinkStatus.ACTIVE, null, 7, NOW, 1, NOW, NOW,
                        null, null, null, "x", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actorIdAndMaskedRecipient() {
        PortalLink link = pending(7);
        assertThat(link.actorId()).isEqualTo("portal:" + link.id());
        assertThat(link.maskedRecipient()).isEqualTo("a***@example.org");
    }
}
