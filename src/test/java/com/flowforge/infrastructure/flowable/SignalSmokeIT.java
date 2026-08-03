package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.connector.builtin.FlowableSignalConnector;
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
 * Smoke BPMN ({@code signal-smoke.bpmn20.xml}) : catch global signal then complete
 * via {@link FlowableSignalConnector} SERVICE ({@code signalEventReceivedWithTenantId}).
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class SignalSmokeIT {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired
    private FlowableSignalConnector flowableSignalConnector;

    @Test
    void serviceSignal_wakesCatchingProcess() {
        String tenantId = tenantProperties.id();
        ProcessInstance waiting = runtimeService.startProcessInstanceByKeyAndTenantId(
                "signalCatchSmoke",
                "signal-smoke-" + UUID.randomUUID(),
                Map.of(),
                tenantId);

        assertThat(runtimeService.createProcessInstanceQuery()
                        .processInstanceId(waiting.getId())
                        .count())
                .isEqualTo(1);

        var instance = new com.flowforge.domain.connector.model.ConnectorInstanceData(
                UUID.randomUUID(),
                tenantId,
                "Smoke signal",
                FlowableSignalConnector.TYPE,
                true,
                Map.of(
                        "signalName", "connectorSmokeSignal",
                        "processKey", "signalCatchSmoke",
                        "dossierTypeCode", "CLAIM"));

        Map<String, Object> result = flowableSignalConnector.execute(instance, Map.of());

        assertThat(result).containsEntry("signaled", true);
        assertThat(runtimeService.createProcessInstanceQuery()
                        .processInstanceId(waiting.getId())
                        .count())
                .isZero();
    }
}
