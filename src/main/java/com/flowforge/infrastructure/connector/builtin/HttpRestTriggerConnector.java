package com.flowforge.infrastructure.connector.builtin;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.TriggerConnector;
import com.flowforge.domain.connector.model.WebhookAuthType;
import com.flowforge.domain.connector.model.WorkflowTriggerEvent;
import com.flowforge.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class HttpRestTriggerConnector implements ConnectorDefinition, TriggerConnector {

    private final SimpleJsonPathMapper mapper;

    public HttpRestTriggerConnector(SimpleJsonPathMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String getType() {
        return "http-rest";
    }

    @Override
    public String getLabel() {
        return "HTTP REST (webhook trigger)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.TRIGGER;
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
                        null));
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
        requireString(config, "webhookSecret");
        requireString(config, "processKey");
        requireString(config, "dossierTypeCode");
    }

    @Override
    public List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        Map<String, Object> mapping = asMap(instance.config().get("mapping"));
        String itemsPath = stringOr(mapping.get("itemsPath"), "$");

        List<Object> items;
        if (rawPayload instanceof List<?> listPayload) {
            items = new ArrayList<>(listPayload);
        } else {
            items = mapper.readItems(rawPayload == null ? Map.of() : rawPayload, itemsPath);
        }

        String defaultProcessKey = stringOr(instance.config().get("processKey"), null);
        String defaultType = stringOr(instance.config().get("dossierTypeCode"), null);
        Map<String, Object> fieldPaths = asMap(mapping.get("fields"));
        Map<String, Object> variablePaths = asMap(mapping.get("variables"));
        String existingRefPath = stringOr(mapping.get("existingDossierReferencePath"), null);
        String processKeyPath = stringOr(mapping.get("processKeyPath"), null);
        String typePath = stringOr(mapping.get("dossierTypeCodePath"), null);

        List<WorkflowTriggerEvent> events = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> fields = mapPaths(item, fieldPaths);
            Map<String, Object> variables = mapPaths(item, variablePaths);
            String processKey = processKeyPath == null
                    ? defaultProcessKey
                    : stringOr(mapper.read(item, processKeyPath), defaultProcessKey);
            String dossierType = typePath == null
                    ? defaultType
                    : stringOr(mapper.read(item, typePath), defaultType);
            String existingRef =
                    existingRefPath == null ? null : stringOr(mapper.read(item, existingRefPath), null);
            if (processKey == null || processKey.isBlank()) {
                throw new ConnectorValidationException("processKey is required for trigger event");
            }
            events.add(new WorkflowTriggerEvent(processKey, dossierType, fields, variables, existingRef));
        }
        return List.copyOf(events);
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
