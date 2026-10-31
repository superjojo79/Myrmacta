package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.ServiceConnector;
import be.myrmacta.domain.connector.model.TriggerConnector;
import be.myrmacta.domain.connector.model.WebhookAuthType;
import be.myrmacta.domain.connector.model.WebhookSecretPolicy;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final String STATIC_HEADER = "STATIC_HEADER";
    /** TODO §112 (D-ORCH-DELETE) : DELETE sans corps ; PATCH attend un autre client HTTP (issue #261). */
    private static final Set<String> SUPPORTED_METHODS = Set.of("GET", "POST", "PUT", "DELETE");
    private static final Set<String> BODYLESS_METHODS = Set.of("GET", "DELETE");
    /** TODO §112 — bornes d'une requête construite par le process (D-ORCH-BASEURL). */
    static final int MAX_PATH_PARAMS = 20;
    static final int MAX_QUERY_PARAMS = 50;
    static final int MAX_URL_CHARS = 8192;
    private static final Pattern PATH_TEMPLATE = Pattern.compile("(/([A-Za-z0-9\\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2}|\\{[A-Za-z][A-Za-z0-9_]{0,59}\\})*)*");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]{0,59})\\}");

    static {
        // Same as WebhookRestAdapter (D-44 / TODO §57 M3): allow setting the restricted Host
        // header so the pinned-IP connection still presents the original hostname.
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @FunctionalInterface
    interface HttpSender {
        HttpCallResponse send(HttpCallRequest request) throws IOException;
    }

    record HttpCallRequest(
            String url,
            String method,
            byte[] body,
            Map<String, String> headers,
            Duration connectTimeout,
            Duration requestTimeout,
            String tlsHostname,
            long maxResponseBytes) {}

    record HttpCallResponse(int status, String body) {}

    private final SimpleJsonPathMapper mapper;
    private final OutboundUrlGuard outboundUrlGuard;
    private final ObjectMapper objectMapper;
    private final ConnectorProperties connectorProperties;
    private final HttpSender httpSender;
    private final OAuth2TokenProvider tokenProvider;

    @Autowired
    public HttpRestConnector(
            SimpleJsonPathMapper mapper,
            OutboundUrlGuard outboundUrlGuard,
            ObjectMapper objectMapper,
            ConnectorProperties connectorProperties) {
        this(mapper, outboundUrlGuard, objectMapper, connectorProperties, urlConnectionSender());
    }

    HttpRestConnector(
            SimpleJsonPathMapper mapper,
            OutboundUrlGuard outboundUrlGuard,
            ObjectMapper objectMapper,
            ConnectorProperties connectorProperties,
            HttpSender httpSender) {
        this(mapper, outboundUrlGuard, objectMapper, connectorProperties, httpSender, Clock.systemUTC());
    }

    HttpRestConnector(
            SimpleJsonPathMapper mapper,
            OutboundUrlGuard outboundUrlGuard,
            ObjectMapper objectMapper,
            ConnectorProperties connectorProperties,
            HttpSender httpSender,
            Clock clock) {
        this.mapper = mapper;
        this.outboundUrlGuard = Objects.requireNonNull(outboundUrlGuard, "outboundUrlGuard");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.connectorProperties = Objects.requireNonNull(connectorProperties, "connectorProperties");
        this.httpSender = Objects.requireNonNull(httpSender, "httpSender");
        this.tokenProvider =
                new OAuth2TokenProvider(outboundUrlGuard, objectMapper, connectorProperties, httpSender, clock);
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
                        "webhookEnabled",
                        "Inbound webhook enabled",
                        ConfigFieldType.BOOLEAN,
                        false,
                        "true",
                        List.of(),
                        "Uncheck for a SERVICE-only instance (outbound calls only): the webhook fields below are then "
                                + "not required and the inbound webhook of this instance answers 404."),
                new ConfigField(
                        "baseUrl",
                        "Base URL (SERVICE calls)",
                        ConfigFieldType.URL,
                        false,
                        null,
                        List.of(),
                        "https base URL of the called API, e.g. https://hr.example.com/api/v2. When set, a call gives a "
                                + "relative path (with encoded pathParams and query) and an absolute url is refused: the "
                                + "authentication header never leaves this host."),
                new ConfigField(
                        "webhookAuthType",
                        "Webhook auth",
                        ConfigFieldType.SELECT,
                        false,
                        WebhookAuthType.HMAC_SHA256.name(),
                        List.of(
                                WebhookAuthType.HMAC_SHA256.name(),
                                WebhookAuthType.SHARED_SECRET_TOKEN.name()),
                        "IP_ALLOWLIST is not supported in V1. HMAC_SHA256 requires an"
                                + " X-Webhook-Timestamp header (Unix epoch seconds) and covers"
                                + " timestamp + \".\" + Idempotency-Key + \".\" + body"
                                + " (X-Signature-256), checked against a 5-minute freshness window"),
                new ConfigField(
                        "webhookSecret",
                        "Webhook secret",
                        ConfigFieldType.PASSWORD,
                        false,
                        null,
                        List.of(),
                        "HMAC or shared token secret -- at least " + WebhookSecretPolicy.MIN_LENGTH
                                + " characters. Required when the inbound webhook is enabled."),
                new ConfigField(
                        "processKey",
                        "Process key",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "Required when the inbound webhook is enabled."),
                new ConfigField(
                        "dossierTypeCode",
                        "Dossier type",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "Required when the inbound webhook is enabled."),
                new ConfigField(
                        "authHeaderValue",
                        "Authorization header value",
                        ConfigFieldType.PASSWORD,
                        false,
                        null,
                        List.of(),
                        "Sent verbatim as the Authorization header on SERVICE calls, "
                                + "e.g. 'Bearer xxx' or 'Basic yyy'. Leave empty for no auth header. "
                                + "Ignored (never sent) when authMode is OAUTH2_CLIENT_CREDENTIALS."),
                new ConfigField(
                        "authMode",
                        "Outbound authentication",
                        ConfigFieldType.SELECT,
                        false,
                        STATIC_HEADER,
                        List.of(STATIC_HEADER, OAuth2TokenProvider.MODE),
                        "SERVICE calls only. STATIC_HEADER sends authHeaderValue as is (default). "
                                + "OAUTH2_CLIENT_CREDENTIALS obtains, caches and renews a Bearer token "
                                + "(e.g. Microsoft Entra ID for D365 F&O, Dataverse, Graph)."),
                new ConfigField(
                        "oauthTokenUrl",
                        "OAuth2 token URL",
                        ConfigFieldType.URL,
                        false,
                        null,
                        List.of(),
                        "https token endpoint, e.g. https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token. "
                                + "Used only when authMode is OAUTH2_CLIENT_CREDENTIALS."),
                new ConfigField(
                        "oauthClientId",
                        "OAuth2 client id",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "Application (client) id. Used only with OAUTH2_CLIENT_CREDENTIALS."),
                new ConfigField(
                        "oauthClientAuthMethod",
                        "OAuth2 client authentication",
                        ConfigFieldType.SELECT,
                        false,
                        OAuth2TokenProvider.METHOD_POST,
                        List.of(OAuth2TokenProvider.METHOD_POST, OAuth2TokenProvider.METHOD_BASIC,
                                OAuth2TokenProvider.METHOD_JWT),
                        "CLIENT_SECRET_POST / CLIENT_SECRET_BASIC use oauthClientSecret; PRIVATE_KEY_JWT "
                                + "(certificate) uses oauthPrivateKey and oauthCertThumbprint."),
                new ConfigField(
                        "oauthClientSecret",
                        "OAuth2 client secret",
                        ConfigFieldType.PASSWORD,
                        false,
                        null,
                        List.of(),
                        "Encrypted at rest. Required for CLIENT_SECRET_POST and CLIENT_SECRET_BASIC."),
                new ConfigField(
                        "oauthPrivateKey",
                        "OAuth2 certificate private key",
                        ConfigFieldType.SECRET_TEXTAREA,
                        false,
                        null,
                        List.of(),
                        "PEM PKCS#8 RSA private key (BEGIN PRIVATE KEY). Encrypted at rest. "
                                + "Required for PRIVATE_KEY_JWT."),
                new ConfigField(
                        "oauthCertThumbprint",
                        "OAuth2 certificate thumbprint",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "Hexadecimal SHA-1 (40 chars, as shown by the Entra portal) or SHA-256 (64 chars) "
                                + "thumbprint of the uploaded certificate. Required for PRIVATE_KEY_JWT."),
                new ConfigField(
                        "oauthAllowedHosts",
                        "OAuth2 allowed API hosts",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "Comma-separated host names that may receive the Bearer token, e.g. "
                                + "'myorg.operations.dynamics.com, *.crm4.dynamics.com'. A call to any other host is "
                                + "refused before a token is requested. Required with OAUTH2_CLIENT_CREDENTIALS."),
                new ConfigField(
                        "oauthScope",
                        "OAuth2 scope",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "e.g. https://<org>.operations.dynamics.com/.default. Optional."),
                new ConfigField(
                        "mapping",
                        "Payload mapping",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "JSON object of JSONPath mappings (itemsPath, processKeyPath, …)."));
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        validateBaseUrl(config.get("baseUrl"));
        if (webhookEnabled(config)) {
            validateWebhook(config);
        }
        validateOutboundAuth(config);
    }

    /** TODO §112 (D-ORCH-SERVICE-ONLY) : absent = vrai (comportement d'avant) ; seul un faux explicite le coupe. */
    private static boolean webhookEnabled(Map<String, Object> config) {
        Object value = config.get(ConnectorInstanceData.WEBHOOK_ENABLED);
        if (value == null || value instanceof Boolean) {
            return !Boolean.FALSE.equals(value);
        }
        String text = String.valueOf(value).strip().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "", "true" -> true;
            case "false" -> false;
            default -> throw new ConnectorValidationException("webhookEnabled must be true or false");
        };
    }

    /** TODO §112 (D-ORCH-BASEURL) : https public (garde SSRF), sans identifiants, requête ni fragment. */
    private void validateBaseUrl(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return;
        }
        String text = String.valueOf(raw).strip();
        URI uri;
        try {
            uri = new URI(text);
        } catch (java.net.URISyntaxException ex) {
            throw new ConnectorValidationException("baseUrl is not a valid URL");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getHost() == null) {
            throw new ConnectorValidationException("baseUrl must be scheme://host[:port][/path], without credentials, "
                    + "query or fragment");
        }
        try {
            outboundUrlGuard.assertSafe(text);
        } catch (IllegalArgumentException ex) {
            throw new ConnectorValidationException("baseUrl is not an acceptable https endpoint");
        }
    }

    private static void validateWebhook(Map<String, Object> config) {
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

    private void validateOutboundAuth(Map<String, Object> config) {
        String mode = stringOr(config.get("authMode"), STATIC_HEADER);
        if (STATIC_HEADER.equals(mode)) {
            return;
        }
        if (!OAuth2TokenProvider.MODE.equals(mode)) {
            throw new ConnectorValidationException("Unsupported authMode: " + mode);
        }
        String tokenUrl = requireString(config, "oauthTokenUrl");
        try {
            outboundUrlGuard.assertSafe(tokenUrl);
        } catch (IllegalArgumentException ex) {
            throw new ConnectorValidationException("oauthTokenUrl is not an acceptable https endpoint");
        }
        requireString(config, "oauthClientId");
        parseAllowedHosts(requireString(config, "oauthAllowedHosts"));
        String method = stringOr(config.get("oauthClientAuthMethod"), OAuth2TokenProvider.METHOD_POST);
        switch (method) {
            case OAuth2TokenProvider.METHOD_POST, OAuth2TokenProvider.METHOD_BASIC ->
                requireString(config, "oauthClientSecret");
            case OAuth2TokenProvider.METHOD_JWT -> {
                requireString(config, "oauthPrivateKey");
                String thumbprint = requireString(config, "oauthCertThumbprint").replace(":", "").trim();
                if (!thumbprint.matches("(?i)[0-9a-f]{40}|[0-9a-f]{64}")) {
                    throw new ConnectorValidationException(
                            "oauthCertThumbprint must be a hexadecimal SHA-1 (40) or SHA-256 (64) thumbprint");
                }
            }
            default -> throw new ConnectorValidationException("Unsupported oauthClientAuthMethod: " + method);
        }
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
        String method = parseMethod(inputParams);
        Object body = inputParams.get("body");
        if (body != null && BODYLESS_METHODS.contains(method)) {
            throw new IllegalArgumentException(method + " does not support a body");
        }
        String urlString = targetUrl(instance.config(), inputParams);

        OutboundUrlGuard.PinnedEndpoint pinned = outboundUrlGuard.resolveSafe(urlString);
        byte[] requestBody = body == null ? new byte[0] : serializeBody(body).getBytes(StandardCharsets.UTF_8);
        boolean oauth = OAuth2TokenProvider.MODE.equals(stringOr(instance.config().get("authMode"), STATIC_HEADER));
        if (oauth) {
            assertHostAllowed(instance.config(), pinned.originalHost());
        }

        HttpCallResponse response = call(instance, inputParams, pinned, method, requestBody, oauth);
        if (oauth && response.status() == 401) {
            // Jeton révoqué ou expiré côté IdP avant l'heure annoncée : un nouveau jeton, un seul nouvel essai.
            if (tokenProvider.invalidate(instance)) {
                response = call(instance, inputParams, pinned, method, requestBody, true);
            }
        }
        byte[] responseBytes = response.body().getBytes(StandardCharsets.UTF_8);
        if (responseBytes.length > connectorProperties.http().maxResponseBytes()) {
            throw new IllegalStateException("http-rest response exceeds maxResponseBytes");
        }
        return Map.of("status", response.status(), "body", response.body());
    }

    /**
     * TODO §112 (D-ORCH-BASEURL) — URL appelée. Sans {@code baseUrl} : {@code url} absolue (comportement d'avant), plus
     * {@code query}. Avec {@code baseUrl} : {@code url} refusée ; {@code path} relatif (gabarit {@code /a/{x}} dont chaque
     * {@code {x}} reçoit {@code pathParams.x} encodé comme <b>un seul segment</b>), puis {@code query} encodée. Le chemin ne
     * peut ni changer d'hôte ni remonter ({@code ..}) : l'en-tête d'authentification ne quitte pas l'hôte de base.
     */
    private static String targetUrl(Map<String, Object> config, Map<String, Object> input) {
        String baseUrl = stringOr(config.get("baseUrl"), null);
        Object url = input.get("url");
        Object path = input.get("path");
        Map<String, Object> pathParams = optionalMap(input.get("pathParams"), "pathParams", MAX_PATH_PARAMS);
        Map<String, Object> query = optionalMap(input.get("query"), "query", MAX_QUERY_PARAMS);
        String target;
        if (baseUrl == null) {
            if (path != null || !pathParams.isEmpty()) {
                throw new IllegalArgumentException("path requires the connector's baseUrl");
            }
            if (!(url instanceof String urlString)) {
                throw new IllegalArgumentException("url is required");
            }
            target = urlString;
        } else {
            if (url != null) {
                throw new IllegalArgumentException("url is not accepted when the connector has a baseUrl; give a path");
            }
            if (path != null && !(path instanceof String)) {
                throw new IllegalArgumentException("path must be a string");
            }
            String base = baseUrl.strip();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            target = base + expandPath(path == null ? "" : (String) path, pathParams);
            assertSameOrigin(base, target);
        }
        target = appendQuery(target, query);
        if (target.length() > MAX_URL_CHARS) {
            throw new IllegalArgumentException("http-rest URL exceeds " + MAX_URL_CHARS + " characters");
        }
        return target;
    }

    private static String expandPath(String template, Map<String, Object> params) {
        if (!template.isEmpty() && (!template.startsWith("/") || template.startsWith("//")
                || !PATH_TEMPLATE.matcher(template).matches())) {
            throw new IllegalArgumentException("path must be a relative path starting with a single / (letters, digits, "
                    + "-._~!$&'()*+,;=:@, %XX escapes and {name} placeholders; no query, fragment, space or backslash)");
        }
        for (String segment : template.split("/", -1)) {
            String decoded = segment.replace("%2e", ".").replace("%2E", ".");
            if (".".equals(decoded) || "..".equals(decoded)) {
                throw new IllegalArgumentException("path must not contain . or .. segments");
            }
        }
        java.util.regex.Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        java.util.Set<String> used = new java.util.HashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!params.containsKey(name)) {
                throw new IllegalArgumentException("pathParams has no value for {" + name + "}");
            }
            String value = scalarText(params.get(name), "pathParams." + name);
            if (value.isEmpty() || ".".equals(value) || "..".equals(value)) {
                throw new IllegalArgumentException("pathParams." + name + " must not be empty, . or ..");
            }
            used.add(name);
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(encode(value, "")));
        }
        matcher.appendTail(out);
        for (String name : params.keySet()) {
            if (!used.contains(name)) {
                throw new IllegalArgumentException("pathParams." + name + " has no {" + name + "} placeholder in path");
            }
        }
        return out.toString();
    }

    /** Défense en profondeur : le chemin validé ne peut pas changer de schéma, d'hôte ni de port. */
    private static void assertSameOrigin(String base, String target) {
        URI baseUri = URI.create(base);
        URI targetUri = URI.create(target);
        if (!Objects.equals(baseUri.getScheme(), targetUri.getScheme())
                || !Objects.equals(baseUri.getRawAuthority(), targetUri.getRawAuthority())
                || targetUri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("path must stay on the connector's baseUrl");
        }
    }

    private static String appendQuery(String url, Map<String, Object> query) {
        if (query.isEmpty()) {
            return url;
        }
        StringBuilder out = new StringBuilder(url);
        char separator = url.contains("?") ? '&' : '?';
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("query parameter names must not be blank");
            }
            List<?> values = entry.getValue() instanceof List<?> list ? list : java.util.Collections.singletonList(entry.getValue());
            for (Object value : values) {
                out.append(separator).append(encode(entry.getKey(), QUERY_SAFE)).append('=')
                        .append(encode(scalarText(value, "query." + entry.getKey()), QUERY_SAFE));
                separator = '&';
            }
        }
        return out.toString();
    }

    /** Caractères laissés tels quels dans une requête (RFC 3986, hors {@code & = + # %}) : {@code $filter} reste lisible. */
    private static final String QUERY_SAFE = "$!'()*,;:@/";

    /** Encodage pour-cent strict : non réservés RFC 3986 plus {@code extraSafe} ; UTF-8 ; espace en {@code %20}. */
    private static String encode(String value, String extraSafe) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            boolean unreserved = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~';
            if (b >= 0 && (unreserved || extraSafe.indexOf(c) >= 0)) {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return out.toString();
    }

    private static String scalarText(Object value, String owner) {
        return switch (value) {
            case String text -> text;
            case Boolean bool -> bool.toString();
            case Double d when d == Math.rint(d) && !d.isInfinite() && Math.abs(d) < 1e15 -> Long.toString(d.longValue());
            case Float f when f == Math.rint(f) && !f.isInfinite() && Math.abs(f) < 1e7 -> Long.toString(f.longValue());
            case java.math.BigDecimal d -> d.stripTrailingZeros().toPlainString();
            case Number n -> n.toString();
            case null -> throw new IllegalArgumentException(owner + " must not be null");
            default -> throw new IllegalArgumentException(owner + " must be a text, number or boolean");
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> optionalMap(Object value, String name, int max) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(name + " must be a map");
        }
        if (map.size() > max) {
            throw new IllegalArgumentException(name + " has more than " + max + " entries");
        }
        return (Map<String, Object>) map;
    }

    private static final Pattern HOST_PATTERN =
            Pattern.compile("(\\*\\.)?[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+");
    private static final Pattern NUMERIC_TLD = Pattern.compile(".*\\.[0-9]+");
    /** Domaines mutualisés dont les sous-domaines appartiennent à des tiers : jamais de joker dessus. */
    private static final Set<String> SHARED_HOSTING_SUFFIXES = Set.of(
            "azurewebsites.net", "cloudapp.azure.com", "cloudapp.net", "blob.core.windows.net", "trafficmanager.net",
            "azureedge.net", "herokuapp.com", "github.io", "vercel.app", "netlify.app", "amazonaws.com",
            "appspot.com", "onrender.com", "pages.dev", "workers.dev", "web.app", "firebaseapp.com");

    private static List<String> parseAllowedHosts(String raw) {
        List<String> hosts = new ArrayList<>();
        for (String part : raw.split(",")) {
            String host = part.trim().toLowerCase(Locale.ROOT);
            if (host.isEmpty()) {
                continue;
            }
            if (!HOST_PATTERN.matcher(host).matches() || NUMERIC_TLD.matcher(host).matches()) {
                throw new ConnectorValidationException(
                        "oauthAllowedHosts contains an invalid host name (a fully qualified name with at least two labels is required)");
            }
            if (host.startsWith("*.")) {
                String base = host.substring(2);
                if (!base.contains(".")) {
                    throw new ConnectorValidationException("oauthAllowedHosts: a wildcard needs a domain with at least two labels");
                }
                for (String shared : SHARED_HOSTING_SUFFIXES) {
                    if (base.equals(shared) || base.endsWith("." + shared)) {
                        throw new ConnectorValidationException(
                                "oauthAllowedHosts: a wildcard on the shared hosting domain " + shared + " is not allowed");
                    }
                }
            }
            hosts.add(host);
        }
        if (hosts.isEmpty()) {
            throw new ConnectorValidationException("oauthAllowedHosts must list at least one host");
        }
        return hosts;
    }

    /** Le jeton n'est jamais émis vers un hôte non déclaré : évite qu'une URL de process l'exfiltre. */
    private static void assertHostAllowed(Map<String, Object> config, String host) {
        String target = host == null ? "" : host.toLowerCase(Locale.ROOT);
        List<String> allowed;
        try {
            allowed = parseAllowedHosts(stringOr(config.get("oauthAllowedHosts"), ""));
        } catch (ConnectorValidationException ex) {
            throw new IllegalStateException("http-rest OAuth2 configuration is incomplete: oauthAllowedHosts");
        }
        for (String pattern : allowed) {
            if (pattern.startsWith("*.")) {
                // Un seul niveau : « *.crm.exemple.com » couvre « org.crm.exemple.com » mais pas « a.org.crm.exemple.com ».
                String suffix = pattern.substring(1);
                if (target.endsWith(suffix)) {
                    String label = target.substring(0, target.length() - suffix.length());
                    if (!label.isEmpty() && label.indexOf('.') < 0) {
                        return;
                    }
                }
            } else if (target.equals(pattern)) {
                return;
            }
        }
        throw new IllegalArgumentException("Host is not allowed for this connector's OAuth2 credentials");
    }

    private HttpCallResponse call(
            ConnectorInstanceData instance,
            Map<String, Object> inputParams,
            OutboundUrlGuard.PinnedEndpoint pinned,
            String method,
            byte[] requestBody,
            boolean oauth) {
        Map<String, String> headers = new LinkedHashMap<>();
        Object authHeaderValue = instance.config().get("authHeaderValue");
        if (!oauth && authHeaderValue instanceof String authValue && !authValue.isBlank()) {
            headers.put("Authorization", authValue);
        }
        applyHeaders(headers, inputParams.get("headers"));
        if (oauth) {
            // Le jeton du connecteur prévaut toujours : un en-tête Authorization fourni par l'appelant est ignoré.
            headers.keySet().removeIf(name -> "authorization".equalsIgnoreCase(name.trim()));
            headers.put("Authorization", "Bearer " + tokenProvider.accessToken(instance));
        }
        headers.put("Host", pinned.originalHost());
        try {
            return httpSender.send(new HttpCallRequest(
                    pinned.requestUrl(),
                    method,
                    requestBody,
                    headers,
                    connectorProperties.http().connectTimeout(),
                    connectorProperties.http().requestTimeout(),
                    pinned.originalHost(),
                    connectorProperties.http().maxResponseBytes()));
        } catch (IOException ex) {
            throw new IllegalStateException("http-rest call failed: " + ex.getMessage(), ex);
        }
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
    private static void applyHeaders(Map<String, String> target, Object headers) {
        if (headers == null) {
            return;
        }
        if (!(headers instanceof Map<?, ?> headersMap)) {
            throw new IllegalArgumentException("headers must be a map");
        }
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) headersMap).entrySet()) {
            if (entry.getKey() != null && "host".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            target.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
    }

    static HttpSender urlConnectionSender() {
        return request -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) URI.create(request.url()).toURL().openConnection();
                connection.setConnectTimeout(Math.toIntExact(request.connectTimeout().toMillis()));
                connection.setReadTimeout(Math.toIntExact(request.requestTimeout().toMillis()));
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod(request.method());
                if (connection instanceof HttpsURLConnection https && request.tlsHostname() != null) {
                    https.setHostnameVerifier((hostname, session) ->
                            HttpsURLConnection.getDefaultHostnameVerifier()
                                    .verify(request.tlsHostname(), session));
                }
                request.headers().forEach(connection::setRequestProperty);
                if ("POST".equals(request.method()) || "PUT".equals(request.method())) {
                    connection.setDoOutput(true);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(request.body() == null ? new byte[0] : request.body());
                    }
                }
                int status = connection.getResponseCode();
                InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                byte[] bytes = readBounded(stream, request.maxResponseBytes());
                return new HttpCallResponse(status, new String(bytes, StandardCharsets.UTF_8));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        };
    }

    private static byte[] readBounded(InputStream stream, long maxResponseBytes) throws IOException {
        if (stream == null) {
            return new byte[0];
        }
        int limit = Math.toIntExact(Math.min(maxResponseBytes, Integer.MAX_VALUE - 1) + 1);
        byte[] bytes = stream.readNBytes(limit);
        if (bytes.length > maxResponseBytes) {
            throw new IOException("http-rest response exceeds maxResponseBytes");
        }
        return bytes;
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
