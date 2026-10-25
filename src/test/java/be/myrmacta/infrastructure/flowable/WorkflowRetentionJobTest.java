package be.myrmacta.infrastructure.flowable;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.port.out.IdempotencyStore;
import be.myrmacta.domain.workflow.port.out.OutboxRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.config.WorkflowProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WorkflowRetentionJobTest {

    @Test
    void purge_delegatesToOutboxAndIdempotencyPorts() {
        OutboxRepository outboxRepository = mock(OutboxRepository.class);
        IdempotencyStore idempotencyStore = mock(IdempotencyStore.class);
        WorkflowProperties properties = new WorkflowProperties(
                Duration.ofMinutes(5),
                new WorkflowProperties.Outbox(20, 5, Duration.ofSeconds(1), Duration.ofDays(7)),
                Duration.ofDays(7));
        when(outboxRepository.purgeProcessedOlderThanDays(7)).thenReturn(4);
        when(idempotencyStore.purgeExpired()).thenReturn(2);

        WorkflowRetentionJob job = new WorkflowRetentionJob(
                outboxRepository, idempotencyStore, properties, new TenantProperties("tenant_dev"));

        job.purge();

        verify(outboxRepository).purgeProcessedOlderThanDays(eq(7));
        verify(idempotencyStore).purgeExpired();
    }
}
