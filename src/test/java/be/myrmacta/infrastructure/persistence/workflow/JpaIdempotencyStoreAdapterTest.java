package be.myrmacta.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.infrastructure.config.WorkflowProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * DEV-PURGE (follow-up ops `workflow-engine`) : {@code purgeExpired()} était un stub
 * (`return 0`) jamais réellement branché sur une suppression — comblé ici.
 */
class JpaIdempotencyStoreAdapterTest {

    private final SpringDataWorkflowIdempotencyRepository repository =
            mock(SpringDataWorkflowIdempotencyRepository.class);
    private final WorkflowProperties properties = new WorkflowProperties(
            Duration.ofMinutes(5),
            new WorkflowProperties.Outbox(20, 5, Duration.ofSeconds(1), Duration.ofDays(7)),
            Duration.ofDays(7));
    private final JpaIdempotencyStoreAdapter adapter = new JpaIdempotencyStoreAdapter(repository, properties);

    @Test
    void purgeExpired_deletesRowsOlderThanConfiguredRetention() {
        when(repository.deleteByCreatedAtBefore(any())).thenReturn(3);

        int purged = adapter.purgeExpired();

        assertThat(purged).isEqualTo(3);
        ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteByCreatedAtBefore(thresholdCaptor.capture());
        Instant expectedThreshold = Instant.now().minus(Duration.ofDays(7));
        assertThat(thresholdCaptor.getValue()).isCloseTo(expectedThreshold, within(5, ChronoUnit.SECONDS));
    }
}
