package be.myrmacta.infrastructure.reporting;

import be.myrmacta.domain.reporting.port.in.RunBiExportUseCase;
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

@Component
public class BiExportJob {

    private static final Logger log = LoggerFactory.getLogger(BiExportJob.class);

    private final RunBiExportUseCase runBiExportUseCase;
    private final TenantProperties tenantProperties;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Autowired
    public BiExportJob(
            RunBiExportUseCase runBiExportUseCase,
            TenantProperties tenantProperties,
            PlatformTransactionManager transactionManager) {
        this(runBiExportUseCase, tenantProperties, transactionManager, Clock.systemUTC());
    }

    BiExportJob(
            RunBiExportUseCase runBiExportUseCase,
            TenantProperties tenantProperties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.runBiExportUseCase = Objects.requireNonNull(runBiExportUseCase, "runBiExportUseCase");
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
            try {
                TransactionTemplate template = new TransactionTemplate(transactionManager);
                runBiExportUseCase.run(
                        tenantProperties.id(), now, action -> template.executeWithoutResult(status -> action.run()));
            } catch (RuntimeException ex) {
                log.warn("BI export failed", ex);
            }
        }
    }
}
