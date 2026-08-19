package com.flowforge.infrastructure.dossier;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.port.in.EvaluateDossierRetentionUseCase;
import com.flowforge.infrastructure.config.RetentionProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvaluateDossierRetentionJobTest {

    private EvaluateDossierRetentionUseCase useCase;
    private EvaluateDossierRetentionJob job;

    @BeforeEach
    void setUp() {
        useCase = mock(EvaluateDossierRetentionUseCase.class);
        job = new EvaluateDossierRetentionJob(
                useCase, new TenantProperties("tenant_dev"), new RetentionProperties(Duration.ofHours(1), 50));
    }

    @Test
    void evaluate_delegatesTenantAndBatchSize() {
        when(useCase.evaluate("tenant_dev", 50)).thenReturn(EvaluateDossierRetentionUseCase.EvaluationResult.of(3, 1));

        job.evaluate();

        verify(useCase).evaluate(eq("tenant_dev"), eq(50));
    }

    @Test
    void evaluate_whenSettingsAbsent_stillCompletes() {
        when(useCase.evaluate("tenant_dev", 50))
                .thenReturn(EvaluateDossierRetentionUseCase.EvaluationResult.absentSettings());

        job.evaluate();

        verify(useCase).evaluate("tenant_dev", 50);
    }
}
