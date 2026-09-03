package com.flowforge.infrastructure.connector.builtin;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.ServiceConnector;
import com.flowforge.domain.connector.model.TriggerConnector;
import com.flowforge.domain.connector.model.WebhookAuthType;
import com.flowforge.domain.connector.model.WebhookSecretPolicy;
import com.flowforge.domain.connector.model.WorkflowTriggerEvent;
import com.flowforge.infrastructure.config.ConnectorProperties;
import com.flowforge.infrastructure.connector.mapping.SimpleJsonPathMapper;
import com.flowforge.infrastructure.notification.OutboundUrlGuard;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §48 (connecteur-http-rest-service) : `http-rest` en rôle SERVICE en plus de TRIGGER —
 * capability {@link ConnectorCapability#BOTH}. Fusionne l'ancien skeleton
 * {@code HttpRestServiceConnector} (non enregistré, D-CAPABILITY-SCOPE) : le registre de
 * connecteurs n'admet qu'un bean par {@link #getType()}, la capacité SERVICE doit donc être
 * portée par cette même classe.
 */
@Component
public class HttpRestConnector implements ConnectorDefinition, TriggerConnector, ServiceConnector {

    private static final Set<String> SUPPORTED_METHODS = Set.of("GET", "POST", "PUT");

    private final SimpleJsonPathMapper mapper;
    private final OutboundUrlGuard outboundUrlGuard;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ConnectorProperties connectorProperties;

    public HttpRestConnector(
            SimpleJsonPathMapper mapper,
            OutboundUrlGuard outboundUrlGuard,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            ConnectorProperties connectorProperties) {
        this.mapper = mapper;
        this.outboundUrlGuard = Objects.requireNonNull(outboundUrlGuard, "outboundUrlGuard");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.connectorProperties = Objects.requireNonNull(connectorProperties, "connectorProperties");
    }

    @Override
    public String getType() {
        return "http-rest";
    }

    @Override
    public String getLabel() {
        return "HTTP REST (webhook trigger + outbound call)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.BOTH;
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
                        "IP_ALLOWLIST is not supported in V1. HMAC_SHA256 requires an"
                                + " X-Webhook-Timestamp header (Unix epoch seconds) covered by the"
                                + " signature (X-Signature-256 = HMAC(secret, timestamp + \".\" + body)),"
                                + " checked against a 5-minute freshness window"),
                new ConfigField(
                        "webhookSecret",
                        "Webhook secret",
                        ConfigFieldType.PASSWORD,
                        true,
                        null,
                        List.of(),
                        "HMAC or shared token secret -- at least " + WebhookSecretPolicy.MIN_LENGTH
                                + " characters"),
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
                        null),
                new ConfigField(
                        "authHeaderValue",
                        "Authorization header value",
                        ConfigFieldType.PASSWORD,
                        false,
                        null,
                        List.of(),
                        "Sent verbatim as the Authorization header on SERVICE calls, "
                                + "e.g. 'Bearer xxx' or 'Basic yyy'. Leave empty for no auth header."));
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
        WebhookSecretPolicy.validate(requireString(config, "webhookSecret"));
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

    @Override
    public Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> inputParams) {
        Objects.requireNonNull(inputParams, "inputParams");
        Object url = inputParams.get("url");
        if (!(url instanceof String urlString)) {
            throw new IllegalArgumentException("url is required");
        }
        String method = parseMethod(inputParams);
        Object body = inputParams.get("body");
        if (body != null && "GET".equals(method)) {
            throw new IllegalArgumentException("GET does not support a body");
        }

        OutboundUrlGuard.PinnedEndpoint pinned = outboundUrlGuard.resolveSafe(urlString);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(pinned.requestUrl()))
                .timeout(connectorProperties.http().requestTimeout());

        Object authHeaderValue = instance.config().get("authHeaderValue");
        if (authHeaderValue instanceof String authValue && !authValue.isBlank()) {
            builder.setHeader("Authorization", authValue);
        }
        applyHeaders(builder, inputParams.get("headers"));

        HttpRequest.BodyPublisher bodyPublisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(serializeBody(body));
        switch (method) {
            case "POST" -> builder.POST(bodyPublisher);
            case "PUT" -> builder.PUT(bodyPublisher);
            default -> builder.GET();
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("http-rest call failed: " + ex.getMessage(), ex);
        }
        return Map.of("status", response.statusCode(), "body", response.body());
    }

    private static String parseMethod(Map<String, Object> inputParams) {
        Object method = inputParams.get("method");
        if (method == null) {
            return "GET";
        }
        String normalized = String.valueOf(method).toUpperCase(Locale.ROOT);
        if (!SUPPORTED_METHODS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported method: " + method);
        }
        return normalized;
    }

    private String serializeBody(Object body) {
        if (body instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize connector body: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyHeaders(HttpRequest.Builder builder, Object headers) {
        if (headers == null) {
            return;
        }
        if (!(headers instanceof Map<?, ?> headersMap)) {
            throw new IllegalArgumentException("headers must be a map");
        }
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) headersMap).entrySet()) {
            builder.setHeader(entry.getKey(), String.valueOf(entry.getValue()));
        }
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
