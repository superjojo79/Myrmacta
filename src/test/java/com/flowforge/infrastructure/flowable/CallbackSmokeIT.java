package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.CallbackAmbiguousCorrelationException;
import com.flowforge.domain.connector.model.CallbackNoMatchingInstanceException;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.support.OidcClientTestConfiguration;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Smoke BPMN ({@code callback-smoke.bpmn20.xml}) : proves {@link WorkflowEnginePort#correlateMessage}
 * against the real Flowable engine (D-CALLBACK: {@code createMessageCorrelation} does not exist on
 * this Flowable 8.0.0 {@code RuntimeService} -- {@code ExecutionQuery} + {@code messageEventReceived}
 * is the real mechanism, never mocked for this proof).
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class CallbackSmokeIT {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired
    private WorkflowEnginePort workflowEnginePort;

    @Test
    void correlateMessage_singleWaitingExecution_resumesWithVariables() {
        String tenantId = tenantProperties.id();
        String correlationValue = "order-" + UUID.randomUUID();
        ProcessInstance waiting = runtimeService.startProcessInstanceByKeyAndTenantId(
                "callbackWaitSmoke",
                "callback-smoke-" + UUID.randomUUID(),
                Map.of("correlationKey", correlationValue),
                tenantId);

        workflowEnginePort.correlateMessage(
                "connectorSmokeCallbackMessage",
                "correlationKey",
                correlationValue,
                Map.of("callbackResult", "approved"));

        // The process lands on afterCallbackTask (userTask), not gone -- proves the injected
        // variable is really visible on the resumed instance, not just that correlate() didn't throw.
        assertThat(runtimeService.createProcessInstanceQuery()
                        .processInstanceId(waiting.getId())
                        .count())
                .isEqualTo(1);
        assertThat(runtimeService.getVariable(waiting.getId(), "callbackResult")).isEqualTo("approved");
    }

    @Test
    void correlateMessage_processVariableScope_notLocalExecutionScope() {
        // Regression guard for the Phase 3 correction: the correlation variable is set on the
        // process instance (via startProcessInstanceByKeyAndTenantId variables), never as a local
        // variable of the waiting execution -- processVariableValueEquals must still find it.
        String tenantId = tenantProperties.id();
        String correlationValue = "process-scope-" + UUID.randomUUID();
        ProcessInstance waiting = runtimeService.startProcessInstanceByKeyAndTenantId(
                "callbackWaitSmoke",
                "callback-smoke-" + UUID.randomUUID(),
                Map.of("correlationKey", correlationValue),
                tenantId);

        assertThat(runtimeService.createExecutionQuery()
                        .messageEventSubscriptionName("connectorSmokeCallbackMessage")
                        .processVariableValueEquals("correlationKey", correlationValue)
                        .list())
                .hasSize(1);

        workflowEnginePort.correlateMessage(
                "connectorSmokeCallbackMessage", "correlationKey", correlationValue, Map.of());

        assertThat(runtimeService.createProcessInstanceQuery()
                        .processInstanceId(waiting.getId())
                        .count())
                .isEqualTo(1);
    }

    @Test
    void correlateMessage_noWaitingExecution_throwsNoMatchingInstance() {
        assertThatThrownBy(() -> workflowEnginePort.correlateMessage(
                        "connectorSmokeCallbackMessage",
                        "correlationKey",
                        "no-such-value-" + UUID.randomUUID(),
                        Map.of()))
                .isInstanceOf(CallbackNoMatchingInstanceException.class);
    }

    @Test
    void correlateMessage_multipleWaitingExecutions_throwsAmbiguousCorrelation() {
        String tenantId = tenantProperties.id();
        String correlationValue = "dup-" + UUID.randomUUID();
        runtimeService.startProcessInstanceByKeyAndTenantId(
                "callbackWaitSmoke",
                "callback-smoke-" + UUID.randomUUID(),
                Map.of("correlationKey", correlationValue),
                tenantId);
        runtimeService.startProcessInstanceByKeyAndTenantId(
                "callbackWaitSmoke",
                "callback-smoke-" + UUID.randomUUID(),
                Map.of("correlationKey", correlationValue),
                tenantId);

        assertThatThrownBy(() -> workflowEnginePort.correlateMessage(
                        "connectorSmokeCallbackMessage", "correlationKey", correlationValue, Map.of()))
                .isInstanceOf(CallbackAmbiguousCorrelationException.class)
                .satisfies(ex ->
                        assertThat(((CallbackAmbiguousCorrelationException) ex).matchCount()).isEqualTo(2));
    }
}
