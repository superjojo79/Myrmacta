package be.myrmacta.infrastructure.deadline;

import be.myrmacta.domain.deadline.port.in.EvaluateDeadlinesUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.observability.MdcScope;
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

/**
 * TODO §115 (D-DEADLINE-ESCALATE) — passe d'échéances : une transaction par dossier ; un échec est repris au passage suivant, la
 * prise du palier étant annulée avec le reste. Plusieurs pods : la clé primaire des paliers arbitre (TODO §79, même motif que
 * l'alerte SLA).
 */
@Component
public class DeadlineAlertJob {

    private static final Logger log = LoggerFactory.getLogger(DeadlineAlertJob.class);

    private final EvaluateDeadlinesUseCase deadlines;
    private final TenantProperties tenantProperties;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Autowired
    public DeadlineAlertJob(EvaluateDeadlinesUseCase deadlines, TenantProperties tenantProperties, PlatformTransactionManager transactionManager) {
        this(deadlines, tenantProperties, transactionManager, Clock.systemUTC());
    }

    DeadlineAlertJob(EvaluateDeadlinesUseCase deadlines, TenantProperties tenantProperties, PlatformTransactionManager transactionManager,
            Clock clock) {
        this.deadlines = Objects.requireNonNull(deadlines, "deadlines");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(initialDelayString = "${app.deadlines.initial-delay-ms:120000}", fixedDelayString = "${app.deadlines.scan-interval-ms:900000}")
    public void tick() {
        process(clock.instant());
    }

    public void process(Instant now) {
        try (MdcScope ignored = MdcScope.tenant(tenantProperties.id())) {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            int failures = deadlines.scan(tenantProperties.id(), now, action -> template.executeWithoutResult(status -> action.run()));
            if (failures > 0) {
                log.warn("Deadline scan left {} dossier(s) for the next interval", failures);
            }
        }
    }
}
