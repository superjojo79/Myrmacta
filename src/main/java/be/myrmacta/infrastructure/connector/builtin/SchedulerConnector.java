package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.TriggerConnector;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Cron-based TRIGGER connector — {@link SchedulerConnectorTriggerJob} decides when to invoke
 * {@link #onEvent}, this class only turns its own config into a {@link WorkflowTriggerEvent}
 * (ignores {@code rawPayload} entirely, unlike webhook/signal-based trigger connectors).
 */
@Component
public class SchedulerConnector implements ConnectorDefinition, TriggerConnector {

    @Override
    public String getType() {
        return "scheduler";
    }

    @Override
    public String getLabel() {
        return "Scheduler (cron trigger)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.TRIGGER;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField(
                        "cronExpression",
                        "Cron expression",
                        ConfigFieldType.STRING,
                        true,
                        null,
                        List.of(),
                        "Spring 6-field cron expression (seconds minutes hours day-of-month month day-of-week)"),
                new ConfigField(
                        "processKey",
                        "Process key",
                        ConfigFieldType.STRING,
                        true,
                        null,
                        List.of(),
                        null),
                new ConfigField(
                        "dossierTypeCode",
                        "Dossier type",
                        ConfigFieldType.STRING,
                        true,
                        null,
                        List.of(),
                        "A new dossier of this type is created on every firing"));
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        String cronExpression = requireString(config, "cronExpression");
        try {
            CronExpression.parse(cronExpression);
        } catch (IllegalArgumentException ex) {
            throw new ConnectorValidationException("Invalid cronExpression: " + ex.getMessage());
        }
        requireString(config, "processKey");
        requireString(config, "dossierTypeCode");
    }

    @Override
    public List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        String processKey = requireString(instance.config(), "processKey");
        String dossierTypeCode = requireString(instance.config(), "dossierTypeCode");
        return List.of(new WorkflowTriggerEvent(processKey, dossierTypeCode, Map.of(), Map.of(), null));
    }

    private static String requireString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConnectorValidationException(key + " is required");
        }
        return text;
    }
}
