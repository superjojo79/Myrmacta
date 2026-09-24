package com.flowforge.domain.workflow.portal.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatus;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatusReason;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvalidatePortalLinksForTaskUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Test
    void taskEnded_revokesLiveLinks_butNotCompletedOnes() {
        // T-INVAL-1
        InMemoryPortalLinkRepository repository = new InMemoryPortalLinkRepository();
        PortalLink active = PortalLink.pendingSend(UUID.randomUUID(), "t", UUID.randomUUID(), "task-1", "pi", "avis",
                "a@b.org", 7, NOW, "x").activated("h", NOW);
        repository.insert(active);
        PortalLink completedOtherTask = PortalLink.pendingSend(UUID.randomUUID(), "t", UUID.randomUUID(), "task-2",
                "pi", "avis", "a@b.org", 7, NOW, "x").activated("h2", NOW).completed(NOW);
        repository.insert(completedOtherTask);
        var useCase = new InvalidatePortalLinksForTaskUseCaseImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(useCase.onTaskEnded("task-1")).isEqualTo(1);
        assertThat(useCase.onTaskEnded("task-2")).isZero();
        PortalLink revoked = repository.findById(active.id()).orElseThrow();
        assertThat(revoked.status()).isEqualTo(PortalLinkStatus.REVOKED);
        assertThat(revoked.statusReason()).isEqualTo(PortalLinkStatusReason.TASK_GONE);
    }
}
