package com.flowforge.infrastructure.reporting;

import com.flowforge.domain.reporting.port.in.EvaluateSlaAlertsUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.observability.MdcScope;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SlaAlertJob {

    private static final Logger log = LoggerFactory.getLogger(SlaAlertJob.class);

    private final EvaluateSlaAlertsUseCase evaluateSlaAlertsUseCase;
    private final TenantProperties tenantProperties;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Autowired
    public SlaAlertJob(
            EvaluateSlaAlertsUseCase evaluateSlaAlertsUseCase,
            TenantProperties tenantProperties,
            PlatformTransactionManager transactionManager) {
        this(
                evaluateSlaAlertsUseCase,
                tenantProperties,
                transactionManager,
                Clock.systemUTC());
    }

    SlaAlertJob(
            EvaluateSlaAlertsUseCase evaluateSlaAlertsUseCase,
            TenantProperties tenantProperties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.evaluateSlaAlertsUseCase = Objects.requireNonNull(evaluateSlaAlertsUseCase, "evaluateSlaAlertsUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void tick() {
        process(clock.instant());
    }

    public void process(Instant now) {
        try (MdcScope ignored = MdcScope.tenant(tenantProperties.id())) {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            int failures = evaluateSlaAlertsUseCase.scan(
                    tenantProperties.id(), now, action -> template.executeWithoutResult(status -> action.run()));
            if (failures > 0) {
                log.warn("SLA alert scan left {} dossier(s) for the next interval", failures);
            }
        }
    }
}
