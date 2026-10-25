package be.myrmacta.infrastructure.dossier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.port.in.EvaluateDossierRetentionUseCase;
import be.myrmacta.infrastructure.config.RetentionProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Duration;
import java.time.Instant;
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
        when(useCase.evaluate(any(Instant.class), eq("tenant_dev"), eq(50)))
                .thenReturn(EvaluateDossierRetentionUseCase.EvaluationResult.of(3, 1));

        job.evaluate();

        verify(useCase).evaluate(any(Instant.class), eq("tenant_dev"), eq(50));
    }

    @Test
    void evaluate_whenSettingsAbsent_stillCompletes() {
        when(useCase.evaluate(any(Instant.class), eq("tenant_dev"), eq(50)))
                .thenReturn(EvaluateDossierRetentionUseCase.EvaluationResult.absentSettings());

        job.evaluate();

        verify(useCase).evaluate(any(Instant.class), eq("tenant_dev"), eq(50));
    }
}
