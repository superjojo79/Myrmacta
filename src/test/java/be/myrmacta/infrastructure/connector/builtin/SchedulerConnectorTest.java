package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.TriggerConnector;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SchedulerConnectorTest {

    private final SchedulerConnector connector = new SchedulerConnector();

    @Test
    void implementsConnectorDefinitionAndTriggerConnector() {
        assertThat(connector).isInstanceOf(ConnectorDefinition.class);
        assertThat(connector).isInstanceOf(TriggerConnector.class);
    }

    @Test
    void getType_returnsScheduler() {
        assertThat(connector.getType()).isEqualTo("scheduler");
    }

    @Test
    void getCapability_isTrigger() {
        assertThat(connector.getCapability()).isEqualTo(ConnectorCapability.TRIGGER);
    }

    @Test
    void getConfigSchema_exposesCronExpressionProcessKeyDossierTypeCode() {
        List<String> names = connector.getConfigSchema().stream().map(f -> f.name()).toList();
        assertThat(names).containsExactlyInAnyOrder("cronExpression", "processKey", "dossierTypeCode");
        assertThat(connector.getConfigSchema()).allMatch(f -> f.required());
    }

    @Test
    void validate_validConfig_doesNotThrow() {
        connector.validate(Map.of(
                "cronExpression", "0 0 * * * *",
                "processKey", "smokeProcess",
                "dossierTypeCode", "CLAIM"));
    }

    @Test
    void validate_invalidCronExpression_throws() {
        assertThatThrownBy(() -> connector.validate(Map.of(
                        "cronExpression", "not-a-cron",
                        "processKey", "smokeProcess",
                        "dossierTypeCode", "CLAIM")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("cronExpression");
    }

    @Test
    void validate_missingProcessKey_throws() {
        assertThatThrownBy(() -> connector.validate(Map.of(
                        "cronExpression", "0 0 * * * *",
                        "dossierTypeCode", "CLAIM")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("processKey");
    }

    @Test
    void validate_blankDossierTypeCode_throws() {
        assertThatThrownBy(() -> connector.validate(Map.of(
                        "cronExpression", "0 0 * * * *",
                        "processKey", "smokeProcess",
                        "dossierTypeCode", "   ")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("dossierTypeCode");
    }

    @Test
    void onEvent_producesWorkflowTriggerEventFromConfig_ignoresRawPayload() {
        ConnectorInstanceData instance = new ConnectorInstanceData(
                UUID.randomUUID(),
                "tenant_dev",
                "Scheduler smoke",
                "scheduler",
                true,
                Map.of("cronExpression", "0 0 * * * *", "processKey", "smokeProcess", "dossierTypeCode", "CLAIM"));

        List<WorkflowTriggerEvent> events = connector.onEvent(instance, Map.of("ignored", "payload"));

        assertThat(events).hasSize(1);
        WorkflowTriggerEvent event = events.get(0);
        assertThat(event.processKey()).isEqualTo("smokeProcess");
        assertThat(event.dossierTypeCode()).isEqualTo("CLAIM");
        assertThat(event.existingDossierReference()).isNull();
    }

    @Test
    void onEvent_nullRawPayload_stillProducesEvent() {
        ConnectorInstanceData instance = new ConnectorInstanceData(
                UUID.randomUUID(),
                "tenant_dev",
                "Scheduler smoke",
                "scheduler",
                true,
                Map.of("cronExpression", "0 0 * * * *", "processKey", "smokeProcess", "dossierTypeCode", "CLAIM"));

        List<WorkflowTriggerEvent> events = connector.onEvent(instance, null);

        assertThat(events).hasSize(1);
    }
}
