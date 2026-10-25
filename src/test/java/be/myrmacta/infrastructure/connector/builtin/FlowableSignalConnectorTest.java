package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableSignalConnectorTest {

    private RuntimeService runtimeService;
    private FlowableSignalConnector connector;

    @BeforeEach
    void setUp() {
        runtimeService = mock(RuntimeService.class);
        connector = new FlowableSignalConnector(runtimeService, new SimpleJsonPathMapper());
    }

    @Test
    void metadata_isBothCapability() {
        assertThat(connector.getType()).isEqualTo("flowable-signal");
        assertThat(connector.getCapability()).isEqualTo(ConnectorCapability.BOTH);
    }

    @Test
    void validate_requiresSignalAndProcessFields() {
        assertThatThrownBy(() -> connector.validate(Map.of("signalName", "s")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("processKey");
        connector.validate(Map.of(
                "signalName", "orderCreated",
                "processKey", "orderProcess",
                "dossierTypeCode", "ORDER"));
    }

    @Test
    void execute_signalsConfiguredName() {
        ConnectorInstanceData instance = instance(Map.of(
                "signalName", "orderCreated",
                "processKey", "p",
                "dossierTypeCode", "T"));

        Map<String, Object> result = connector.execute(instance, Map.of());

        verify(runtimeService).signalEventReceivedWithTenantId("orderCreated", "tenant_dev");
        assertThat(result).containsEntry("signaled", true).containsEntry("signalName", "orderCreated");
    }

    @Test
    void execute_overrideSignalNameAndVariables() {
        ConnectorInstanceData instance = instance(Map.of(
                "signalName", "configured",
                "processKey", "p",
                "dossierTypeCode", "T"));

        connector.execute(instance, Map.of(
                "signalName", "override",
                "variables", Map.of("x", 1)));

        verify(runtimeService)
                .signalEventReceivedWithTenantId(eq("override"), eq(Map.of("x", 1)), eq("tenant_dev"));
    }

    /**
     * Revue adversariale TODO §93 (I7) : un signal est diffusé à toutes les instances du tenant qui l'attendent ;
     * connectorInput étant posable à la complétion d'une tâche, ses variables ne doivent pas écraser dossierId & co.
     */
    @Test
    void execute_refusesReservedPlatformVariablesAndSignalsNothing() {
        ConnectorInstanceData instance = instance(Map.of(
                "signalName", "configured",
                "processKey", "p",
                "dossierTypeCode", "T"));

        for (String reserved : List.of("dossierId", "dossierWorkflowLinkId", "tenantId", "dossierFields", "connectorResult")) {
            assertThatThrownBy(() -> connector.execute(instance, Map.of("variables", Map.of(reserved, "x"))))
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining(reserved);
        }
        org.mockito.Mockito.verifyNoInteractions(runtimeService);
    }

    @Test
    void onEvent_mapsPayloadToTriggerEvent() {
        ConnectorInstanceData instance = instance(Map.of(
                "signalName", "orderCreated",
                "processKey", "orderProcess",
                "dossierTypeCode", "ORDER",
                "mapping", Map.of(
                        "fields", Map.of("ref", "$.variables.ref"),
                        "variables", Map.of("amount", "$.variables.amount"))));

        List<WorkflowTriggerEvent> events = connector.onEvent(
                instance,
                Map.of(
                        "signalName", "orderCreated",
                        "variables", Map.of("ref", "R-1", "amount", 42)));

        assertThat(events).hasSize(1);
        WorkflowTriggerEvent event = events.getFirst();
        assertThat(event.processKey()).isEqualTo("orderProcess");
        assertThat(event.dossierTypeCode()).isEqualTo("ORDER");
        assertThat(event.dossierFields()).containsEntry("ref", "R-1");
        assertThat(event.variables()).containsEntry("amount", 42);
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "Signal", "flowable-signal", true, config);
    }
}
