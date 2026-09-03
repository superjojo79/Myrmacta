package com.flowforge.infrastructure.connector.builtin;

import com.flowforge.domain.connector.model.CallbackConnector;
import com.flowforge.domain.connector.model.CallbackResolution;
import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.WebhookAuthType;
import com.flowforge.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Generic callback connector (D-CALLBACK, TODO §51) : resumes a process execution waiting on a
 * Flowable message via {@code WorkflowEnginePort.correlateMessage}. Independent of any SERVICE
 * connector that emitted the outbound call -- never coupled to {@code http-rest} specifically.
 */
@Component
public class FlowableMessageCallbackConnector implements ConnectorDefinition, CallbackConnector {

    private final SimpleJsonPathMapper mapper;

    public FlowableMessageCallbackConnector(SimpleJsonPathMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String getType() {
        return "flowable-callback";
    }

    @Override
    public String getLabel() {
        return "Flowable message callback (async correlation)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.CALLBACK;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField(
                        "webhookAuthType",
                        "Webhook auth",
                        ConfigFieldType.SELECT,
                        true,
                        WebhookAuthType.HMAC_SHA256.name(),
                        List.of(
                                WebhookAuthType.HMAC_SHA256.name(),
                                WebhookAuthType.SHARED_SECRET_TOKEN.name()),
                        "IP_ALLOWLIST is not supported in V1"),
                new ConfigField(
                        "webhookSecret",
                        "Webhook secret",
                        ConfigFieldType.PASSWORD,
                        true,
                        null,
                        List.of(),
                        "HMAC or shared token secret"),
                new ConfigField(
                        "messageName",
                        "BPMN message name",
                        ConfigFieldType.STRING,
                        true,
                        null,
                        List.of(),
                        "Must match the messageRef of the intermediateCatchEvent/receiveTask in the BPMN process"),
                new ConfigField(
                        "correlationVariableName",
                        "Correlation process variable",
                        ConfigFieldType.STRING,
                        true,
                        null,
                        List.of(),
                        "Name of the process variable set by the outbound serviceTask, matched against the callback"
                                + " payload. Must be set as a String process variable -- Flowable variable equality"
                                + " is type-sensitive (a String \"123\" never matches an Integer 123)"),
                new ConfigField(
                        "correlationValuePath",
                        "Correlation value JSONPath",
                        ConfigFieldType.STRING,
                        true,
                        null,
                        List.of(),
                        "JSONPath into the callback payload yielding the correlation value, e.g. $.orderId."
                                + " Authentication is per connector instance, not per correlation value: use a"
                                + " high-entropy, non-sequential identifier (e.g. a UUID), never a guessable one"
                                + " (sequential dossier number), or any caller holding this instance's secret could"
                                + " correlate to an unrelated waiting process by guessing it"));
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        Object authType = config.get("webhookAuthType");
        if (authType == null) {
            throw new ConnectorValidationException("webhookAuthType is required");
        }
        String auth = String.valueOf(authType);
        if ("IP_ALLOWLIST".equals(auth)) {
            throw new ConnectorValidationException("IP_ALLOWLIST is not supported in V1");
        }
        try {
            WebhookAuthType.valueOf(auth);
        } catch (IllegalArgumentException ex) {
            throw new ConnectorValidationException("Unsupported webhookAuthType: " + auth);
        }
        stringValue(config, "webhookSecret");
        stringValue(config, "messageName");
        stringValue(config, "correlationVariableName");
        stringValue(config, "correlationValuePath");
    }

    @Override
    public CallbackResolution resolve(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        String messageName = stringValue(instance.config(), "messageName");
        String correlationVariableName = stringValue(instance.config(), "correlationVariableName");
        String correlationValuePath = stringValue(instance.config(), "correlationValuePath");

        Object payload = rawPayload == null ? Map.of() : rawPayload;
        Object rawCorrelationValue = mapper.read(payload, correlationValuePath);
        if (rawCorrelationValue == null) {
            throw new ConnectorValidationException(
                    "No value found at correlationValuePath '" + correlationValuePath + "' in callback payload");
        }
        // D-CALLBACK-TYPE (Phase 7) : Flowable process-variable equality is type-sensitive --
        // a String variable "123" never matches an Integer query value 123 (proven against the
        // real engine). JSON payloads routinely carry identifiers as numbers; normalizing to
        // String here is the only side of this equation this connector controls -- the BPMN
        // process variable set by the outbound serviceTask must also be a String (documented on
        // the correlationVariableName config field).
        String correlationValue = String.valueOf(rawCorrelationValue);

        Map<String, Object> mapping = asMap(instance.config().get("mapping"));
        Map<String, Object> variablePaths = asMap(mapping.get("variables"));
        Map<String, Object> variables = mapPaths(payload, variablePaths);

        return new CallbackResolution(messageName, correlationVariableName, correlationValue, variables);
    }

    private Map<String, Object> mapPaths(Object item, Map<String, Object> paths) {
        if (paths == null || paths.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            Object value = mapper.read(item, String.valueOf(entry.getValue()));
            if (value != null) {
                out.put(entry.getKey(), value);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String stringValue(Map<String, Object> config, String field) {
        Object value = config.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConnectorValidationException(field + " is required");
        }
        return text;
    }
}
