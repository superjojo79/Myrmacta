package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flowable.engine.ManagementService;
import org.flowable.job.api.DeadLetterJobQuery;
import org.flowable.job.api.JobQuery;
import org.flowable.job.api.TimerJobQuery;
import org.junit.jupiter.api.Test;

class FlowableJobMetricsBinderTest {

    @Test
    void exposesDeadLetterAndExceptionGauges() {
        ManagementService managementService = mock(ManagementService.class);
        DeadLetterJobQuery deadLetter = mock(DeadLetterJobQuery.class);
        JobQuery jobs = mock(JobQuery.class);
        TimerJobQuery timers = mock(TimerJobQuery.class);
        when(managementService.createDeadLetterJobQuery()).thenReturn(deadLetter);
        when(deadLetter.count()).thenReturn(3L);
        when(managementService.createJobQuery()).thenReturn(jobs);
        when(jobs.withException()).thenReturn(jobs);
        when(jobs.count()).thenReturn(2L);
        when(managementService.createTimerJobQuery()).thenReturn(timers);
        when(timers.withException()).thenReturn(timers);
        when(timers.count()).thenReturn(1L);

        FlowableJobMetricsBinder binder = new FlowableJobMetricsBinder(managementService);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binder.bindTo(registry);

        assertThat(registry.get(FlowableJobMetricsBinder.DEAD_LETTER).gauge().value()).isEqualTo(3.0);
        assertThat(registry.get(FlowableJobMetricsBinder.EXCEPTION).gauge().value()).isEqualTo(3.0);
    }

    @Test
    void collectionFailure_returnsZero() {
        ManagementService managementService = mock(ManagementService.class);
        when(managementService.createDeadLetterJobQuery()).thenThrow(new IllegalStateException("engine"));
        FlowableJobMetricsBinder binder = new FlowableJobMetricsBinder(managementService);
        assertThat(binder.deadLetterCount()).isZero();
        assertThat(binder.exceptionCount()).isZero();
    }
}
