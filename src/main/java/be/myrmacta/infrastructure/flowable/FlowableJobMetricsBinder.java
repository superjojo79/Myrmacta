package be.myrmacta.infrastructure.flowable;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Objects;
import org.flowable.engine.ManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlowableJobMetricsBinder implements MeterBinder {

    static final String DEAD_LETTER = "myrmacta.flowable.jobs.dead.letter";
    static final String EXCEPTION = "myrmacta.flowable.jobs.exception";

    private static final Logger log = LoggerFactory.getLogger(FlowableJobMetricsBinder.class);

    private final ManagementService managementService;

    public FlowableJobMetricsBinder(ManagementService managementService) {
        this.managementService = Objects.requireNonNull(managementService, "managementService");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(DEAD_LETTER, this, ignored -> (double) deadLetterCount()).register(registry);
        Gauge.builder(EXCEPTION, this, ignored -> (double) exceptionCount()).register(registry);
    }

    public long deadLetterCount() {
        try {
            return managementService.createDeadLetterJobQuery().count();
        } catch (RuntimeException ex) {
            log.warn("Failed to collect Flowable dead-letter job count", ex);
            return 0L;
        }
    }

    public long exceptionCount() {
        try {
            long executable = managementService.createJobQuery().withException().count();
            long timers = managementService.createTimerJobQuery().withException().count();
            return executable + timers;
        } catch (RuntimeException ex) {
            log.warn("Failed to collect Flowable exception job count", ex);
            return 0L;
        }
    }
}
