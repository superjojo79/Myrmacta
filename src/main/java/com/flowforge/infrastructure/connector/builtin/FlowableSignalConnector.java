package com.flowforge.infrastructure.connector.builtin;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.ServiceConnector;
import com.flowforge.domain.connector.model.TriggerConnector;
import com.flowforge.domain.connector.model.WorkflowTriggerEvent;
import com.flowforge.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Component;

/**
 * Builtin {@code flowable-signal} — capability BOTH (D-SIGNAL).
 *
 * <ul>
 *   <li>SERVICE : {@link RuntimeService#signalEventReceived} with optional variable map</li>
 *   <li>TRIGGER : maps signal payload → {@link WorkflowTriggerEvent} via déclarative mapping</li>
 * </ul>
 */
@Component
public class FlowableSignalConnector implements ConnectorDefinition, ServiceConnector, TriggerConnector {

    public static final String TYPE = "flowable-signal";

    private final RuntimeService runtimeService;
    private final SimpleJsonPathMapper mapper;

    public FlowableSignalConnector(RuntimeService runtimeService, SimpleJsonPathMapper mapper) {
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getLabel() {
        return "Flowable signal";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.BOTH;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField(
                        "signalName",
                        "Signal name",
                        ConfigFieldType.STRING,
                        true,
                        null,
                        List.of(),
                        "Flowable global signal name"),
                new ConfigField(
                        "processKey",
                        "Process key",
                        ConfigFieldType.STRING,
                        true,
                        null,
                        List.of(),
                        "Default process key for TRIGGER path"),
                new ConfigField(
                        "dossierTypeCode",
                        "Dossier type",
                        ConfigFieldType.STRING,
                        true,
                        null,
                        List.of(),
                        "Default dossier type for TRIGGER create"),
                new ConfigField(
                        "mapping",
                        "Payload mapping",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "JSON object of JSONPath mappings (fields, variables, …)."));
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        requireString(config, "signalName");
        requireString(config, "processKey");
        requireString(config, "dossierTypeCode");
    }

    @Override
    public Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> inputParams) {
        Objects.requireNonNull(instance, "instance");
        Map<String, Object> params = inputParams == null ? Map.of() : inputParams;
        String configured = stringOr(instance.config().get("signalName"), null);
        String override = stringOr(params.get("signalName"), null);
        String signalName = override != null ? override : configured;
        if (signalName == null || signalName.isBlank()) {
            throw new ConnectorValidationException("signalName is required");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> variables = params.get("variables") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        String tenantId = instance.tenantId();
        if (variables.isEmpty()) {
            runtimeService.signalEventReceivedWithTenantId(signalName, tenantId);
        } else {
            runtimeService.signalEventReceivedWithTenantId(signalName, variables, tenantId);
        }
        return Map.of("signalName", signalName, "signaled", true);
    }

    @Override
    public List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        Map<String, Object> mapping = asMap(instance.config().get("mapping"));
        Object root = rawPayload == null ? Map.of() : rawPayload;

        String defaultProcessKey = stringOr(instance.config().get("processKey"), null);
        String defaultType = stringOr(instance.config().get("dossierTypeCode"), null);
        Map<String, Object> fieldPaths = asMap(mapping.get("fields"));
        Map<String, Object> variablePaths = asMap(mapping.get("variables"));
        String existingRefPath = stringOr(mapping.get("existingDossierReferencePath"), null);
        String processKeyPath = stringOr(mapping.get("processKeyPath"), null);
        String typePath = stringOr(mapping.get("dossierTypeCodePath"), null);

        Map<String, Object> fields = mapPaths(root, fieldPaths);
        Map<String, Object> variables = mapPaths(root, variablePaths);
        // Preserve engine signal metadata when no explicit variable mapping.
        if (variables.isEmpty() && root instanceof Map<?, ?> payloadMap) {
            Object signalVars = payloadMap.get("variables");
            if (signalVars instanceof Map<?, ?> sv) {
                variables = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : sv.entrySet()) {
                    variables.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        String processKey = processKeyPath == null
                ? defaultProcessKey
                : stringOr(mapper.read(root, processKeyPath), defaultProcessKey);
        String dossierType = typePath == null
                ? defaultType
                : stringOr(mapper.read(root, typePath), defaultType);
        String existingRef =
                existingRefPath == null ? null : stringOr(mapper.read(root, existingRefPath), null);
        if (processKey == null || processKey.isBlank()) {
            throw new ConnectorValidationException("processKey is required for trigger event");
        }
        return List.of(new WorkflowTriggerEvent(processKey, dossierType, fields, variables, existingRef));
    }

    private Map<String, Object> mapPaths(Object item, Map<String, Object> paths) {
        if (paths == null || paths.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            out.put(entry.getKey(), mapper.read(item, String.valueOf(entry.getValue())));
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

    private static String stringOr(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private static void requireString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConnectorValidationException(key + " is required");
        }
    }
}
