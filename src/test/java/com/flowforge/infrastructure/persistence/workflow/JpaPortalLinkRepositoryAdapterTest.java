package com.flowforge.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatus;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatusReason;
import com.flowforge.domain.workflow.portal.port.out.PortalLinkConflictException;
import com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JpaPortalLinkRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Autowired
    private PortalLinkRepository repository;

    private static PortalLink pending(UUID dossierId, String taskId) {
        return PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", dossierId, taskId, "pi-" + taskId, "avis",
                "alice@example.org", 7, NOW, "flowable-task-created");
    }

    private static String task() {
        return "task-" + System.nanoTime();
    }

    @Test
    void insertThenFind_roundTripsEveryField() {
        PortalLink link = pending(UUID.randomUUID(), task());
        repository.insert(link);

        assertThat(repository.findById(link.id())).contains(link);
    }

    @Test
    void transition_isCompareAndSetOnStatus() {
        PortalLink pending = pending(UUID.randomUUID(), task());
        repository.insert(pending);
        String hash = UUID.randomUUID().toString().replace("-", "") + "0".repeat(32);
        PortalLink active = pending.activated(hash, NOW);

        assertThat(repository.transition(pending, active)).isTrue();
        // second writer still believes the link is PENDING_SEND: rejected (T-SUBMIT-3 mechanism)
        assertThat(repository.transition(pending, active)).isFalse();
        assertThat(repository.findByTokenHash(hash)).contains(active);

        PortalLink completed = active.completed(NOW.plusSeconds(60));
        assertThat(repository.transition(active, completed)).isTrue();
        assertThat(repository.transition(active, active.completed(NOW.plusSeconds(61)))).isFalse();
        assertThat(repository.findById(pending.id()).orElseThrow().status()).isEqualTo(PortalLinkStatus.COMPLETED);
    }

    @Test
    void secondLiveLinkForSameTask_isRejectedByDatabase() {
        // T-MANAGE-5 (exigence H2)
        String taskId = task();
        PortalLink first = pending(UUID.randomUUID(), taskId);
        repository.insert(first);

        assertThatThrownBy(() -> repository.insert(pending(UUID.randomUUID(), taskId)))
                .isInstanceOf(PortalLinkConflictException.class);

        // once revoked, a new live link is allowed (renvoi)
        repository.transition(first, first.revoked(NOW, "u1", PortalLinkStatusReason.RESENT));
        PortalLink second = pending(UUID.randomUUID(), taskId);
        repository.insert(second);
        assertThat(repository.findLiveByTaskId(taskId)).containsExactly(second);
    }

    @Test
    void findActiveExpiringBefore_returnsOnlyExpiredActiveLinks() {
        PortalLink expiring = pending(UUID.randomUUID(), task());
        repository.insert(expiring);
        PortalLink expiringActive = expiring.activated(UUID.randomUUID().toString().replace("-", "") + "1".repeat(32),
                Instant.parse("2020-01-01T00:00:00Z"));
        repository.transition(expiring, expiringActive);
        PortalLink fresh = pending(UUID.randomUUID(), task());
        repository.insert(fresh);
        repository.transition(fresh, fresh.activated(UUID.randomUUID().toString().replace("-", "") + "2".repeat(32), NOW));

        assertThat(repository.findActiveExpiringBefore(NOW, 500))
                .extracting(PortalLink::id)
                .contains(expiring.id())
                .doesNotContain(fresh.id());
    }

    @Test
    void findByDossierIdAndDeleteAllByDossierId() {
        UUID dossierId = UUID.randomUUID();
        UUID otherDossier = UUID.randomUUID();
        repository.insert(pending(dossierId, task()));
        repository.insert(pending(dossierId, task()));
        repository.insert(pending(otherDossier, task()));

        assertThat(repository.findByDossierId(dossierId)).hasSize(2);
        assertThat(repository.deleteAllByDossierId(dossierId)).isEqualTo(2);
        assertThat(repository.findByDossierId(dossierId)).isEmpty();
        assertThat(repository.findByDossierId(otherDossier)).hasSize(1);
    }

    @Test
    void findByTokenHash_blankIsEmpty() {
        assertThat(repository.findByTokenHash(null)).isEmpty();
        assertThat(repository.findByTokenHash(" ")).isEmpty();
    }
}
