package com.flowforge.infrastructure.connector.builtin;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.TriggerConnector;
import com.flowforge.domain.connector.model.WorkflowTriggerEvent;
import com.flowforge.infrastructure.config.ConnectorProperties;
import com.flowforge.infrastructure.config.InboundConnectorProperties;
import com.flowforge.infrastructure.connector.mapping.SimpleJsonPathMapper;
import com.flowforge.infrastructure.notification.OutboundUrlGuard;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §87 (D3d) — connecteur TRIGGER « Azure Service Bus » (file ou abonnement de rubrique), par l'API REST en verrouillage de
 * lecture : un message JSON crée (ou alimente) un dossier, avec le même mapping que Kafka ({@code fields}, {@code variables},
 * {@code processKeyPath}, {@code dossierTypeCodePath}, {@code existingDossierReferencePath}). Débloque les Business Events D365 F&amp;O
 * sans relais. Le message n'est retiré de la file qu'une fois converti ; un message illisible est rendu au courtier, qui le met en
 * lettres mortes après {@code MaxDeliveryCount} (jamais de boucle sans fin côté FlowForge). Hôte limité aux domaines Service Bus.
 * Authentification : signature d'accès partagé (SAS) ou OAuth2 {@code client_credentials} (D3a, scope {@code https://servicebus.azure.net/.default}).
 */
@Component
public class AzureServiceBusConnector implements ConnectorDefinition, TriggerConnector {

    static final String SAS_MODE = "SAS";
    static final int WAIT_SECONDS = 5;
    private static final Pattern HOST = Pattern.compile(
            "[a-z0-9][a-z0-9-]{0,48}[a-z0-9]\\.servicebus\\.(windows\\.net|usgovcloudapi\\.net|chinacloudapi\\.cn)");
    private static final Pattern ENTITY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._~-]{0,259}(/subscriptions/[A-Za-z0-9][A-Za-z0-9._~-]{0,49})?");
    private static final Logger log = LoggerFactory.getLogger(AzureServiceBusConnector.class);

    private final SimpleJsonPathMapper mapper;
    private final OutboundUrlGuard outboundUrlGuard;
    private final ObjectMapper objectMapper;
    private final InboundConnectorProperties inbound;
    private final ServiceBusPort bus;
    private final OAuth2TokenProvider tokenProvider;
    private final Clock clock;

    @Autowired
    public AzureServiceBusConnector(
            SimpleJsonPathMapper mapper,
            OutboundUrlGuard outboundUrlGuard,
            ObjectMapper objectMapper,
            ConnectorProperties connectorProperties,
            InboundConnectorProperties inbound,
            ServiceBusPort bus) {
        this(mapper, outboundUrlGuard, objectMapper, connectorProperties, inbound, bus, Clock.systemUTC());
    }

    AzureServiceBusConnector(
            SimpleJsonPathMapper mapper,
            OutboundUrlGuard outboundUrlGuard,
            ObjectMapper objectMapper,
            ConnectorProperties connectorProperties,
            InboundConnectorProperties inbound,
            ServiceBusPort bus,
            Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.outboundUrlGuard = Objects.requireNonNull(outboundUrlGuard, "outboundUrlGuard");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokenProvider = new OAuth2TokenProvider(
                outboundUrlGuard, objectMapper, connectorProperties, HttpRestConnector.urlConnectionSender(), clock);
    }

    @Override
    public String getType() {
        return "azure-service-bus";
    }

    @Override
    public String getLabel() {
        return "Azure Service Bus (file ou abonnement, interrogation)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.TRIGGER;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        List<ConfigField> fields = new ArrayList<>(List.of(
                new ConfigField("namespaceHost", "Namespace host", ConfigFieldType.STRING, true, null, List.of(),
                        "e.g. mybus.servicebus.windows.net (Service Bus domains only)."),
                new ConfigField("entityPath", "Queue or subscription", ConfigFieldType.STRING, true, null, List.of(),
                        "A queue name, or <topic>/subscriptions/<subscription>."),
                new ConfigField(OAuth2ConfigSupport.AUTH_MODE, "Authentication", ConfigFieldType.SELECT, false, SAS_MODE,
                        List.of(SAS_MODE, OAuth2TokenProvider.MODE),
                        "SAS = shared access policy with the Listen right. OAUTH2_CLIENT_CREDENTIALS = Entra ID application "
                                + "(role Azure Service Bus Data Receiver, scope https://servicebus.azure.net/.default)."),
                new ConfigField("sasKeyName", "SAS policy name", ConfigFieldType.STRING, false, null, List.of(),
                        "Required with SAS. Give it the Listen right only."),
                new ConfigField("sasKey", "SAS policy key", ConfigFieldType.PASSWORD, false, null, List.of(),
                        "Encrypted at rest. Required with SAS."),
                new ConfigField("pollSeconds", "Polling interval (seconds)", ConfigFieldType.INTEGER, false, "60", List.of(), null),
                new ConfigField("processKey", "Process key", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("dossierTypeCode", "Dossier type", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("mapping", "Message mapping", ConfigFieldType.STRING, false, null, List.of(),
                        "JSON object of JSONPath mappings over the JSON message body: fields, variables, processKeyPath, "
                                + "dossierTypeCodePath, existingDossierReferencePath.")));
        fields.addAll(OAuth2ConfigSupport.fields());
        return fields;
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        String host = OAuth2ConfigSupport.text(config, "namespaceHost");
        if (!HOST.matcher(host).matches()) {
            throw new ConnectorValidationException("namespaceHost must be an Azure Service Bus namespace (*.servicebus.windows.net)");
        }
        try {
            outboundUrlGuard.assertSafe("https://" + host);
        } catch (IllegalArgumentException ex) {
            throw new ConnectorValidationException("namespaceHost is not an acceptable public address");
        }
        String entity = OAuth2ConfigSupport.text(config, "entityPath");
        if (!ENTITY.matcher(entity).matches()) {
            throw new ConnectorValidationException("entityPath must be a queue name or <topic>/subscriptions/<subscription>");
        }
        String mode = config.get(OAuth2ConfigSupport.AUTH_MODE) == null ? SAS_MODE : String.valueOf(config.get(OAuth2ConfigSupport.AUTH_MODE));
        if (SAS_MODE.equals(mode)) {
            String keyName = OAuth2ConfigSupport.text(config, "sasKeyName");
            if (!keyName.matches("[A-Za-z0-9._~-]{1,100}")) {
                throw new ConnectorValidationException("sasKeyName is not a valid policy name");
            }
            OAuth2ConfigSupport.text(config, "sasKey");
        } else if (OAuth2TokenProvider.MODE.equals(mode)) {
            OAuth2ConfigSupport.validate(config, outboundUrlGuard);
        } else {
            throw new ConnectorValidationException("Unsupported authMode: " + mode);
        }
        int poll = config.get("pollSeconds") == null ? 60 : parseInt(config.get("pollSeconds"));
        if (poll < inbound.minPollSeconds()) {
            throw new ConnectorValidationException("pollSeconds must be at least " + inbound.minPollSeconds());
        }
        OAuth2ConfigSupport.text(config, "processKey");
        OAuth2ConfigSupport.text(config, "dossierTypeCode");
    }

    @Override
    public List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        Map<String, Object> config = instance.config();
        String host = OAuth2ConfigSupport.text(config, "namespaceHost").toLowerCase(Locale.ROOT);
        String entity = OAuth2ConfigSupport.text(config, "entityPath");
        String baseUrl = "https://" + host;
        ServiceBusPort.Endpoint endpoint = new ServiceBusPort.Endpoint(baseUrl, entity, authorization(instance, baseUrl + "/" + entity));
        return process(instance, endpoint);
    }

    /** Séparé de {@link #onEvent} pour que les tests de bout en bout visent un serveur local. */
    List<WorkflowTriggerEvent> process(ConnectorInstanceData instance, ServiceBusPort.Endpoint endpoint) {
        List<ServiceBusPort.Received> messages =
                bus.receive(endpoint, inbound.maxMessagesPerPoll(), WAIT_SECONDS, inbound.maxBusMessageBytes());
        List<WorkflowTriggerEvent> events = new ArrayList<>();
        for (ServiceBusPort.Received message : messages) {
            WorkflowTriggerEvent event;
            try {
                event = toEvent(instance.config(), objectMapper.readValue(message.body(), Object.class));
            } catch (RuntimeException invalid) {
                // Illisible ou incomplet : rendu au courtier (compte une livraison ; lettres mortes après MaxDeliveryCount).
                log.warn("azure-service-bus: instance {} message {} (delivery {}) refused: {}", instance.id(), message.messageId(),
                        message.deliveryCount(), invalid.getClass().getSimpleName());
                safeAbandon(endpoint, message);
                continue;
            }
            events.add(event);
            try {
                bus.complete(endpoint, message);
            } catch (RuntimeException failed) {
                // Le verrou expirera et le message reviendra : un doublon vaut mieux qu'une perte.
                log.warn("azure-service-bus: instance {} message {} could not be completed ({})", instance.id(), message.messageId(),
                        failed.getClass().getSimpleName());
            }
        }
        return List.copyOf(events);
    }

    private void safeAbandon(ServiceBusPort.Endpoint endpoint, ServiceBusPort.Received message) {
        try {
            bus.abandon(endpoint, message);
        } catch (RuntimeException ignored) {
            // Le verrou expirera tout seul.
        }
    }

    private WorkflowTriggerEvent toEvent(Map<String, Object> config, Object payload) {
        Map<String, Object> mapping = asMap(config.get("mapping"));
        Map<String, Object> fields = mapPaths(payload, asMap(mapping.get("fields")));
        for (Object value : fields.values()) {
            if (value == null || (value instanceof String text && text.isBlank())) {
                throw new ConnectorValidationException("mapped field missing/blank in message");
            }
        }
        // Une variable absente du message est simplement absente (jamais une valeur nulle : un événement ne porte pas de null).
        Map<String, Object> variables = new LinkedHashMap<>(mapPaths(payload, asMap(mapping.get("variables"))));
        variables.values().removeIf(Objects::isNull);
        String defaultProcessKey = stringOr(config.get("processKey"), null);
        String defaultType = stringOr(config.get("dossierTypeCode"), null);
        String processKeyPath = stringOr(mapping.get("processKeyPath"), null);
        String typePath = stringOr(mapping.get("dossierTypeCodePath"), null);
        String refPath = stringOr(mapping.get("existingDossierReferencePath"), null);
        String processKey = processKeyPath == null ? defaultProcessKey : stringOr(mapper.read(payload, processKeyPath), defaultProcessKey);
        String type = typePath == null ? defaultType : stringOr(mapper.read(payload, typePath), defaultType);
        String ref = refPath == null ? null : stringOr(mapper.read(payload, refPath), null);
        if (processKey == null || processKey.isBlank()) {
            throw new ConnectorValidationException("processKey is required for trigger event");
        }
        return new WorkflowTriggerEvent(processKey, type, fields, variables, ref);
    }

    private java.util.function.Supplier<String> authorization(ConnectorInstanceData instance, String resourceUri) {
        Map<String, Object> config = instance.config();
        if (OAuth2ConfigSupport.isOAuth(config)) {
            return () -> "Bearer " + tokenProvider.accessToken(instance);
        }
        String keyName = OAuth2ConfigSupport.text(config, "sasKeyName");
        String key = OAuth2ConfigSupport.text(config, "sasKey");
        return () -> sasToken(resourceUri, keyName, key, clock.instant().getEpochSecond() + 600);
    }

    /** Signature d'accès partagé : {@code sig = base64(HMAC-SHA256(urlencode(uri) + "\n" + expiry))}. */
    static String sasToken(String resourceUri, String keyName, String key, long expirySeconds) {
        String encodedUri = URLEncoder.encode(resourceUri.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getEncoder().encodeToString(mac.doFinal((encodedUri + "\n" + expirySeconds).getBytes(StandardCharsets.UTF_8)));
            return "SharedAccessSignature sr=" + encodedUri + "&sig=" + URLEncoder.encode(signature, StandardCharsets.UTF_8)
                    + "&se=" + expirySeconds + "&skn=" + URLEncoder.encode(keyName, StandardCharsets.UTF_8);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("could not sign the request", ex);
        }
    }

    private Map<String, Object> mapPaths(Object item, Map<String, Object> paths) {
        if (paths.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            Object value = mapper.read(item, String.valueOf(entry.getValue()));
            out.put(entry.getKey(), value);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String stringOr(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static int parseInt(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new ConnectorValidationException("not a whole number: " + value);
        }
    }
}
