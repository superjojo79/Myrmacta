package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.TriggerConnector;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * TRIGGER connector (connecteurs-cible, lot B1) : mappe **exactement un** message Kafka déjà
 * consommé en un {@link WorkflowTriggerEvent} — {@link KafkaConsumerReconciler} décide du
 * <em>quand</em>/<em>comment consommer</em> (cycle de vie du conteneur par instance), cette classe
 * ignore tout ce qui concerne l'abonnement. Contrairement à {@code http-rest}, pas de notion
 * {@code itemsPath} : un message = un événement (D-KAFKA-ROLE, requirements — TRIGGER seul,
 * ROADMAP.md §2.2).
 */
@Component
public class KafkaConnector implements ConnectorDefinition, TriggerConnector {

    private final SimpleJsonPathMapper mapper;

    public KafkaConnector(SimpleJsonPathMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public String getType() {
        return "kafka";
    }

    @Override
    public String getLabel() {
        return "Kafka (consumer trigger)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.TRIGGER;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField(
                        "bootstrapServers", "Bootstrap servers", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("topic", "Topic", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("groupId", "Consumer group", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField(
                        "securityProtocol",
                        "Security protocol",
                        ConfigFieldType.SELECT,
                        true,
                        "PLAINTEXT",
                        List.of("PLAINTEXT", "SASL_SSL"),
                        null),
                new ConfigField(
                        "saslUsername", "SASL username", ConfigFieldType.STRING, false, null, List.of(), null),
                new ConfigField(
                        "saslPassword", "SASL password", ConfigFieldType.PASSWORD, false, null, List.of(), null),
                new ConfigField("processKey", "Process key", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField(
                        "dossierTypeCode", "Dossier type", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField(
                        "mapping",
                        "Message mapping",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "JSON object of JSONPath mappings (fields, variables, processKeyPath, …), "
                                + "same style as http-rest.mapping"));
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        requireString(config, "bootstrapServers");
        requireString(config, "topic");
        requireString(config, "groupId");
        String securityProtocol = requireString(config, "securityProtocol");
        if (!"PLAINTEXT".equals(securityProtocol) && !"SASL_SSL".equals(securityProtocol)) {
            throw new ConnectorValidationException("Unsupported securityProtocol: " + securityProtocol);
        }
        if ("SASL_SSL".equals(securityProtocol)) {
            requireString(config, "saslUsername");
            requireString(config, "saslPassword");
        }
        requireString(config, "processKey");
        requireString(config, "dossierTypeCode");
    }

    @Override
    public List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        Map<String, Object> config = instance.config();
        Map<String, Object> mapping = asMap(config.get("mapping"));
        Map<String, Object> fieldPaths = asMap(mapping.get("fields"));
        Map<String, Object> variablePaths = asMap(mapping.get("variables"));
        String processKeyPath = stringOr(mapping.get("processKeyPath"), null);
        String typePath = stringOr(mapping.get("dossierTypeCodePath"), null);
        String existingRefPath = stringOr(mapping.get("existingDossierReferencePath"), null);

        Map<String, Object> fields = mapPaths(rawPayload, fieldPaths);
        if (hasBlankMappedField(fields)) {
            throw new ConnectorValidationException("mapped field missing/blank in message");
        }
        Map<String, Object> variables = mapPaths(rawPayload, variablePaths);
        String defaultProcessKey = stringOr(config.get("processKey"), null);
        String defaultType = stringOr(config.get("dossierTypeCode"), null);
        String processKey = processKeyPath == null
                ? defaultProcessKey
                : stringOr(mapper.read(rawPayload, processKeyPath), defaultProcessKey);
        String dossierType = typePath == null
                ? defaultType
                : stringOr(mapper.read(rawPayload, typePath), defaultType);
        String existingRef = existingRefPath == null ? null : stringOr(mapper.read(rawPayload, existingRefPath), null);
        if (processKey == null || processKey.isBlank()) {
            throw new ConnectorValidationException("processKey is required for trigger event");
        }
        return List.of(new WorkflowTriggerEvent(processKey, dossierType, fields, variables, existingRef));
    }

    /** Same rationale as {@code BatchImportConnector.hasBlankMappedField}. */
    private static boolean hasBlankMappedField(Map<String, Object> fields) {
        for (Object value : fields.values()) {
            if (value == null || (value instanceof String text && text.isBlank())) {
                return true;
            }
        }
        return false;
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

    private static String requireString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConnectorValidationException(key + " is required");
        }
        return text;
    }
}
