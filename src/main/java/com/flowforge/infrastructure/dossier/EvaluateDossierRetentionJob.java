package com.flowforge.infrastructure.dossier;

import com.flowforge.domain.dossier.port.in.EvaluateDossierRetentionUseCase;
import com.flowforge.infrastructure.config.RetentionProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-SCHED: polls {@code EvaluateDossierRetentionUseCase} on a fixed delay. The whole batch run
 * is wrapped in one transaction so a mid-batch failure never leaves a dossier archived without
 * its purge outbox row (D-ARCHIVE-PATH) — a conservative, simpler superset of the per-dossier
 * atomicity the design calls for; the use case's inner repository writes join this transaction.
 */
@Component
class EvaluateDossierRetentionJob {

    private final EvaluateDossierRetentionUseCase evaluateDossierRetentionUseCase;
    private final TenantProperties tenantProperties;
    private final RetentionProperties retentionProperties;

    EvaluateDossierRetentionJob(
            EvaluateDossierRetentionUseCase evaluateDossierRetentionUseCase,
            TenantProperties tenantProperties,
            RetentionProperties retentionProperties) {
        this.evaluateDossierRetentionUseCase =
                Objects.requireNonNull(evaluateDossierRetentionUseCase, "evaluateDossierRetentionUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.retentionProperties = Objects.requireNonNull(retentionProperties, "retentionProperties");
    }

    @Scheduled(fixedDelayString = "${app.retention.poll-delay:1h}")
    @Transactional
    void evaluate() {
        evaluateDossierRetentionUseCase.evaluate(tenantProperties.id(), retentionProperties.batchSize());
    }
}
