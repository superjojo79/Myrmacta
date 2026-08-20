package com.flowforge.infrastructure.dossier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.port.in.ProcessPurgeOutboxUseCase;
import com.flowforge.infrastructure.config.PurgeProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PurgeOutboxJobTest {

    @Test
    void process_delegatesNowAndBatchSize() {
        ProcessPurgeOutboxUseCase useCase = mock(ProcessPurgeOutboxUseCase.class);
        when(useCase.processBatch(any(Instant.class), eq(50))).thenReturn(2);
        PurgeOutboxJob job = new PurgeOutboxJob(
                useCase, new PurgeProperties(Duration.ofHours(1), 50), new TenantProperties("tenant_dev"));

        job.process();

        verify(useCase).processBatch(any(Instant.class), eq(50));
    }
}
