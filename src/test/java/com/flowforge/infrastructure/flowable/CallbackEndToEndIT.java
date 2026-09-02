package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.CallbackNoMatchingInstanceException;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.in.CallbackConnectorRuntime;
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
 * M6 (planning doc) : bout-en-bout réel {@code CallbackConnectorRuntime} ->
 * {@code FlowableMessageCallbackConnector.resolve(...)} (extraction JSONPath du payload) ->
 * {@code WorkflowEnginePort.correlateMessage(...)} (vrai moteur Flowable) -- ni le connecteur ni
 * le moteur ne sont mockés ici, seule la couche HTTP (déjà prouvée avec mocks dans
 * CallbackWebhookControllerTest) est absente.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class CallbackEndToEndIT {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired
    private CallbackConnectorRuntime callbackConnectorRuntime;

    @Test
    void onIncomingCallback_realConnectorAndEngine_resumesWithMappedVariables() {
        String tenantId = tenantProperties.id();
        String correlationValue = "order-" + UUID.randomUUID();
        ProcessInstance waiting = runtimeService.startProcessInstanceByKeyAndTenantId(
                "callbackWaitSmoke",
                "callback-e2e-" + UUID.randomUUID(),
                Map.of("correlationKey", correlationValue),
                tenantId);

        ConnectorInstanceData instance = callbackInstance();
        Map<String, Object> payload = Map.of("orderId", correlationValue, "status", "approved");

        callbackConnectorRuntime.onIncomingCallback(instance, payload);

        assertThat(runtimeService.createProcessInstanceQuery()
                        .processInstanceId(waiting.getId())
                        .count())
                .isEqualTo(1);
        assertThat(runtimeService.getVariable(waiting.getId(), "callbackResult")).isEqualTo("approved");
    }

    @Test
    void onIncomingCallback_numericOrderIdInPayload_stillMatchesStringProcessVariable() {
        // D-CALLBACK-TYPE (Phase 7) : proves the real-engine fix end to end -- a process variable
        // set as a String must still be found when the callback payload carries the same
        // identifier as a JSON number (routine on real external systems).
        String tenantId = tenantProperties.id();
        String correlationValue = String.valueOf(System.nanoTime());
        ProcessInstance waiting = runtimeService.startProcessInstanceByKeyAndTenantId(
                "callbackWaitSmoke",
                "callback-e2e-" + UUID.randomUUID(),
                Map.of("correlationKey", correlationValue),
                tenantId);

        ConnectorInstanceData instance = callbackInstance();
        Map<String, Object> payload = Map.of("orderId", Long.parseLong(correlationValue), "status", "approved");

        callbackConnectorRuntime.onIncomingCallback(instance, payload);

        assertThat(runtimeService.createProcessInstanceQuery()
                        .processInstanceId(waiting.getId())
                        .count())
                .isEqualTo(1);
    }

    @Test
    void onIncomingCallback_noWaitingExecution_throwsThroughFullChain() {
        ConnectorInstanceData instance = callbackInstance();
        Map<String, Object> payload = Map.of("orderId", "no-such-order-" + UUID.randomUUID());

        assertThatThrownBy(() -> callbackConnectorRuntime.onIncomingCallback(instance, payload))
                .isInstanceOf(CallbackNoMatchingInstanceException.class);
    }

    private static ConnectorInstanceData callbackInstance() {
        return new ConnectorInstanceData(
                UUID.randomUUID(),
                "tenant_dev",
                "Callback E2E",
                "flowable-callback",
                true,
                Map.of(
                        "webhookAuthType", "HMAC_SHA256",
                        "webhookSecret", "s",
                        "messageName", "connectorSmokeCallbackMessage",
                        "correlationVariableName", "correlationKey",
                        "correlationValuePath", "$.orderId",
                        "mapping", Map.of("variables", Map.of("callbackResult", "$.status"))));
    }
}
