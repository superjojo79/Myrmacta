package com.flowforge.infrastructure.dossier;

import com.flowforge.domain.dossier.port.in.EvaluateDossierRetentionUseCase;
import com.flowforge.infrastructure.config.RetentionProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.observability.MdcScope;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-SCHED: polls {@code EvaluateDossierRetentionUseCase} on a fixed delay. The whole batch run
 * is wrapped in one transaction so a mid-batch failure never leaves a dossier archived without
 * its purge outbox row (D-ARCHIVE-PATH) — a conservative, simpler superset of the per-dossier
 * atomicity the design calls for; the use case's inner repository writes join this transaction.
 * Unique outbox conflicts are not swallowed (Phase 7) — they roll back the unit and retry later.
 */
@Component
class EvaluateDossierRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(EvaluateDossierRetentionJob.class);

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
        try (MdcScope ignored = MdcScope.tenant(tenantProperties.id())) {
            EvaluateDossierRetentionUseCase.EvaluationResult result =
                    evaluateDossierRetentionUseCase.evaluate(
                            Instant.now(), tenantProperties.id(), retentionProperties.batchSize());
            if (result.settingsAbsent()) {
                log.warn(
                        "Retention job no-op for tenant '{}': settings not configured (D-BOOTSTRAP)",
                        tenantProperties.id());
            }
        }
    }
}
