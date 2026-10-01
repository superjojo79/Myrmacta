package com.flowforge.infrastructure.persistence.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.connector.port.out.ConnectorScheduleStateRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "app.connector.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Import(OidcClientTestConfiguration.class)
class JpaConnectorScheduleStateRepositoryAdapterTest {

    @Autowired
    private ConnectorInstanceRepository connectorInstanceRepository;

    @Autowired
    private ConnectorScheduleStateRepository repository;

    @Test
    void findLastTriggeredAt_beforeAnyUpsert_isEmpty() {
        UUID instanceId = seedConnectorInstance();

        assertThat(repository.findLastTriggeredAt(instanceId)).isEmpty();
    }

    @Test
    void upsert_thenFind_roundTripsCorrectly() {
        UUID instanceId = seedConnectorInstance();
        Instant triggeredAt = Instant.parse("2026-09-01T12:00:00Z");

        repository.upsert(instanceId, triggeredAt);

        assertThat(repository.findLastTriggeredAt(instanceId)).contains(triggeredAt);
    }

    @Test
    void upsert_calledTwice_overwritesWithoutDuplicating() {
        UUID instanceId = seedConnectorInstance();
        repository.upsert(instanceId, Instant.parse("2026-09-01T12:00:00Z"));

        Instant secondFiring = Instant.parse("2026-09-01T12:01:00Z");
        repository.upsert(instanceId, secondFiring);

        assertThat(repository.findLastTriggeredAt(instanceId)).contains(secondFiring);
    }

    /** D5 (TODO §79) : pour une même échéance, un seul des exécutants concurrents prend le créneau. */
    @Test
    void claimTrigger_firstEverSlot_oneConcurrentExecutorWins() throws Exception {
        UUID instanceId = seedConnectorInstance();
        Instant now = Instant.parse("2026-09-01T12:00:00Z");

        assertThat(concurrentClaims(8, () -> repository.claimTrigger(instanceId, null, now))).isEqualTo(1);
        assertThat(repository.findLastTriggeredAt(instanceId)).contains(now);
    }

    @Test
    void claimTrigger_nextSlot_oneConcurrentExecutorWins_andARaceLoserCannotClaimAgain() throws Exception {
        UUID instanceId = seedConnectorInstance();
        Instant last = Instant.parse("2026-09-01T12:00:00Z");
        Instant next = Instant.parse("2026-09-01T12:01:00Z");
        repository.upsert(instanceId, last);

        assertThat(concurrentClaims(8, () -> repository.claimTrigger(instanceId, last, next))).isEqualTo(1);
        assertThat(repository.findLastTriggeredAt(instanceId)).contains(next);
        // Un exécutant resté sur l'ancienne observation ne reprend pas le créneau déjà pris.
        assertThat(repository.claimTrigger(instanceId, last, next.plusSeconds(1))).isFalse();
    }

    @Test
    void releaseTrigger_restoresThePreviousStateOnlyIfTheClaimIsStillTheCurrentOne() {
        UUID instanceId = seedConnectorInstance();
        Instant last = Instant.parse("2026-09-01T12:00:00Z");
        Instant claimed = Instant.parse("2026-09-01T12:01:00Z");
        repository.upsert(instanceId, last);
        assertThat(repository.claimTrigger(instanceId, last, claimed)).isTrue();

        repository.releaseTrigger(instanceId, last, claimed);
        assertThat(repository.findLastTriggeredAt(instanceId)).contains(last);
        // Libération tardive d'un créneau qui n'est plus le nôtre : sans effet.
        repository.upsert(instanceId, claimed.plusSeconds(60));
        repository.releaseTrigger(instanceId, last, claimed);
        assertThat(repository.findLastTriggeredAt(instanceId)).contains(claimed.plusSeconds(60));
    }

    @Test
    void releaseTrigger_ofAFirstEverClaim_removesTheRowSoTheNextTickRetries() {
        UUID instanceId = seedConnectorInstance();
        Instant claimed = Instant.parse("2026-09-01T12:00:00Z");
        assertThat(repository.claimTrigger(instanceId, null, claimed)).isTrue();

        repository.releaseTrigger(instanceId, null, claimed);

        assertThat(repository.findLastTriggeredAt(instanceId)).isEmpty();
        assertThat(repository.claimTrigger(instanceId, null, claimed.plusSeconds(60))).isTrue();
    }

    private static long concurrentClaims(int executors, java.util.concurrent.Callable<Boolean> claim) throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(executors);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.List<java.util.concurrent.Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < executors; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return claim.call();
                }));
            }
            go.countDown();
            long winners = 0;
            for (var f : futures) {
                winners += f.get() ? 1 : 0;
            }
            return winners;
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID seedConnectorInstance() {
        UUID id = UUID.randomUUID();
        connectorInstanceRepository.save(new ConnectorInstanceData(
                id, "tenant_dev", "Scheduler smoke " + id, "scheduler", true, Map.of()));
        return id;
    }
}
