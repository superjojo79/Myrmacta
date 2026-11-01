package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * D3a (TODO §77) : jeton d'accès OAuth2 {@code client_credentials} pour les appels SERVICE de {@code http-rest}.
 *
 * <p>Cache <b>en mémoire par pod</b> (pas de Redis, TODO n° 14), borné, à un seul vol (single-flight) par entrée.
 * La clé de cache contient le tenant, l'instance et une empreinte SHA-256 de la configuration d'authentification :
 * modifier le secret, le scope ou l'URL rend l'ancien jeton inatteignable, et aucun secret n'est conservé en clair.
 * Les messages d'erreur sont neutres : ni secret, ni jeton, ni corps de réponse de l'IdP.
 */
final class OAuth2TokenProvider {

    static final int MAX_CACHED_TOKENS = 256;
    /** Un jeton émis depuis moins longtemps n'est pas renouvelé de force sur un 401 (anti-tempête vers l'IdP). */
    static final Duration MIN_FORCED_REFRESH_AGE = Duration.ofSeconds(30);
    /** Un échec de l'IdP est mémorisé brièvement : pas de martelage d'un IdP en panne ou qui limite le débit. */
    static final Duration FAILURE_MEMORY = Duration.ofSeconds(5);
    static final int MAX_TOKEN_CHARS = 16 * 1024;
    static final String MODE = "OAUTH2_CLIENT_CREDENTIALS";
    static final String METHOD_POST = "CLIENT_SECRET_POST";
    static final String METHOD_BASIC = "CLIENT_SECRET_BASIC";
    static final String METHOD_JWT = "PRIVATE_KEY_JWT";

    private static final Duration MAX_LIFETIME = Duration.ofHours(24);
    private static final long DEFAULT_LIFETIME_SECONDS = 3600;
    private static final long MAX_SKEW_SECONDS = 60;
    private static final long ASSERTION_VALIDITY_SECONDS = 300;
    private static final Pattern ERROR_CODE = Pattern.compile("[a-z_]{1,40}");
    private static final Pattern NO_CONTROL = Pattern.compile("\\A[\\x21-\\x7E]+\\z");
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private record Key(String tenantId, UUID instanceId, String configDigest) {}

    /** Jeton, échéance de renouvellement et date d'émission lus ensemble (un seul champ volatil : lecture atomique). */
    private record Token(String value, Instant renewAt, Instant issuedAt) {}

    private record Failure(String message, Instant at) {}

    private static final class Entry {
        volatile Token token;
        volatile Failure failure;
        volatile boolean inFlight;
        volatile Instant lastUsed;

        Entry(Instant now) {
            this.lastUsed = now;
        }
    }

    private final OutboundUrlGuard outboundUrlGuard;
    private final ObjectMapper objectMapper;
    private final ConnectorProperties connectorProperties;
    private final HttpRestConnector.HttpSender httpSender;
    private final Clock clock;
    private final ConcurrentHashMap<Key, Entry> cache = new ConcurrentHashMap<>();

    OAuth2TokenProvider(
            OutboundUrlGuard outboundUrlGuard,
            ObjectMapper objectMapper,
            ConnectorProperties connectorProperties,
            HttpRestConnector.HttpSender httpSender,
            Clock clock) {
        this.outboundUrlGuard = Objects.requireNonNull(outboundUrlGuard, "outboundUrlGuard");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.connectorProperties = Objects.requireNonNull(connectorProperties, "connectorProperties");
        this.httpSender = Objects.requireNonNull(httpSender, "httpSender");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    String accessToken(ConnectorInstanceData instance) {
        Map<String, Object> config = instance.config();
        Entry entry = entryFor(keyOf(instance));
        Instant now = clock.instant();
        entry.lastUsed = now;
        Token cached = entry.token;
        if (cached != null && now.isBefore(cached.renewAt())) {
            return cached.value();
        }
        synchronized (entry) {
            entry.inFlight = true;
            try {
                now = clock.instant();
                Token current = entry.token;
                if (current != null && now.isBefore(current.renewAt())) {
                    return current.value();
                }
                Failure failure = entry.failure;
                if (failure != null && now.isBefore(failure.at().plus(FAILURE_MEMORY))) {
                    throw new IllegalStateException(failure.message());
                }
                try {
                    Fetched fetched = fetch(instance.tenantId(), config);
                    entry.token = new Token(fetched.token(), fetched.renewAt(), now);
                    entry.failure = null;
                    return fetched.token();
                } catch (IllegalStateException ex) {
                    entry.failure = new Failure(ex.getMessage(), now);
                    throw ex;
                }
            } finally {
                entry.inFlight = false;
            }
        }
    }

    /**
     * Retire le jeton courant (rejeté par l'API). Renvoie {@code false} — sans rien retirer — si ce jeton vient
     * d'être émis : un 401 qui persiste après un jeton neuf n'est pas un problème de jeton, et renouveler à chaque
     * appel martèlerait l'IdP.
     */
    boolean invalidate(ConnectorInstanceData instance) {
        Entry entry = cache.get(keyOf(instance));
        if (entry == null) {
            return false;
        }
        Token token = entry.token;
        if (token == null) {
            return true;
        }
        if (clock.instant().isBefore(token.issuedAt().plus(MIN_FORCED_REFRESH_AGE))) {
            return false;
        }
        entry.token = null;
        return true;
    }

    int cacheSize() {
        return cache.size();
    }

    private Entry entryFor(Key key) {
        Entry existing = cache.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (cache) {
            existing = cache.get(key);
            if (existing != null) {
                return existing;
            }
            if (cache.size() >= MAX_CACHED_TOKENS) {
                evict();
            }
            Entry created = new Entry(clock.instant());
            cache.put(key, created);
            return created;
        }
    }

    /**
     * Retire d'abord les entrées sans jeton valide, puis les moins récemment utilisées, jusqu'à repasser sous la borne.
     * Une entrée dont l'obtention est en cours n'est jamais retirée (sinon deux obtentions concurrentes pour une clé).
     */
    private void evict() {
        Instant now = clock.instant();
        cache.entrySet().removeIf(e -> {
            Entry v = e.getValue();
            Token t = v.token;
            return !v.inFlight && (t == null || !now.isBefore(t.renewAt()))
                    && now.isAfter(v.lastUsed.plus(FAILURE_MEMORY));
        });
        while (cache.size() >= MAX_CACHED_TOKENS) {
            var victim = cache.entrySet().stream()
                    .filter(e -> !e.getValue().inFlight)
                    .min(java.util.Comparator.comparing(e -> e.getValue().lastUsed));
            if (victim.isEmpty()) {
                return;
            }
            cache.remove(victim.get().getKey(), victim.get().getValue());
        }
    }

    private static Key keyOf(ConnectorInstanceData instance) {
        Map<String, Object> c = instance.config();
        StringBuilder sb = new StringBuilder();
        for (String name : new String[] {
            "oauthTokenUrl", "oauthClientId", "oauthClientAuthMethod", "oauthScope",
            "oauthClientSecret", "oauthPrivateKey", "oauthCertThumbprint"
        }) {
            sb.append(name).append('=').append(text(c.get(name))).append('\u0000');
        }
        return new Key(instance.tenantId(), instance.id(), sha256Hex(sb.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private record Fetched(String token, Instant renewAt) {}

    private Fetched fetch(String tenantId, Map<String, Object> config) {
        String tokenUrl = require(config, "oauthTokenUrl");
        String clientId = require(config, "oauthClientId");
        String method = text(config.get("oauthClientAuthMethod"));
        if (method.isBlank()) {
            method = METHOD_POST;
        }
        OutboundUrlGuard.PinnedEndpoint pinned = outboundUrlGuard.resolveSafe(tokenUrl);

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        String scope = text(config.get("oauthScope"));
        if (!scope.isBlank()) {
            form.put("scope", scope);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Accept", "application/json");
        headers.put("Host", pinned.originalHost());
        switch (method) {
            case METHOD_POST -> {
                form.put("client_id", clientId);
                form.put("client_secret", require(config, "oauthClientSecret"));
            }
            case METHOD_BASIC -> headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    (formEncode(clientId) + ":" + formEncode(require(config, "oauthClientSecret")))
                            .getBytes(StandardCharsets.UTF_8)));
            case METHOD_JWT -> {
                form.put("client_id", clientId);
                form.put("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
                form.put("client_assertion", clientAssertion(clientId, tokenUrl, config));
            }
            default -> throw new IllegalStateException("OAuth2 token request failed: unsupported client authentication method");
        }
        StringBuilder body = new StringBuilder();
        form.forEach((k, v) -> {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(formEncode(k)).append('=').append(formEncode(v));
        });

        long maxBytes = connectorProperties.http().maxResponseBytes();
        HttpRestConnector.HttpCallResponse response;
        try {
            response = httpSender.send(new HttpRestConnector.HttpCallRequest(
                    tokenUrl, pinned.address(), "POST", body.toString().getBytes(StandardCharsets.UTF_8), headers,
                    connectorProperties.http().connectTimeout(), connectorProperties.http().requestTimeout(),
                    pinned.originalHost(), maxBytes));
        } catch (IOException ex) {
            throw new IllegalStateException("OAuth2 token request failed: " + ex.getMessage());
        }
        if (response.body().getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalStateException("OAuth2 token response exceeds maxResponseBytes");
        }
        JsonNode json = parse(response.body());
        if (response.status() < 200 || response.status() >= 300) {
            String code = json != null && json.isObject() && json.path("error").isString()
                    ? json.path("error").asString() : "";
            throw new IllegalStateException("OAuth2 token request failed (HTTP " + response.status() + ")"
                    + (ERROR_CODE.matcher(code).matches() ? ": " + code : ""));
        }
        if (json == null || !json.isObject()) {
            throw new IllegalStateException("OAuth2 token response is not a JSON object");
        }
        if (!"bearer".equalsIgnoreCase(json.path("token_type").asString(""))) {
            throw new IllegalStateException("OAuth2 token response: unsupported token_type (Bearer required)");
        }
        String token = json.path("access_token").isString() ? json.path("access_token").asString() : "";
        if (token.length() > MAX_TOKEN_CHARS || !NO_CONTROL.matcher(token).matches()) {
            throw new IllegalStateException("OAuth2 token response: access_token missing or malformed");
        }
        long lifetime = lifetimeSeconds(json.get("expires_in"));
        long skew = Math.min(MAX_SKEW_SECONDS, lifetime / 2);
        return new Fetched(token, clock.instant().plusSeconds(Math.max(0, lifetime - skew)));
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException ex) {
            return null;
        }
    }

    private static long lifetimeSeconds(JsonNode node) {
        if (node == null || node.isNull()) {
            return DEFAULT_LIFETIME_SECONDS;
        }
        java.math.BigDecimal value;
        if (node.isNumber()) {
            value = node.decimalValue();
        } else if (node.isString() && node.asString().matches("-?\\d{1,12}")) {
            value = new java.math.BigDecimal(node.asString());
        } else {
            return DEFAULT_LIFETIME_SECONDS;
        }
        if (value.signum() <= 0) {
            throw new IllegalStateException("OAuth2 token response: expires_in must be positive");
        }
        return Math.min(value.min(java.math.BigDecimal.valueOf(MAX_LIFETIME.toSeconds())).longValue(), MAX_LIFETIME.toSeconds());
    }

    private String clientAssertion(String clientId, String tokenUrl, Map<String, Object> config) {
        String thumbprint = require(config, "oauthCertThumbprint").replace(":", "").trim();
        byte[] thumbBytes;
        try {
            thumbBytes = HexFormat.of().parseHex(thumbprint);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("OAuth2 client assertion: oauthCertThumbprint must be hexadecimal");
        }
        String header;
        if (thumbBytes.length == 20) {
            header = "x5t";
        } else if (thumbBytes.length == 32) {
            header = "x5t#S256";
        } else {
            throw new IllegalStateException("OAuth2 client assertion: oauthCertThumbprint must be a SHA-1 or SHA-256 thumbprint");
        }
        long now = clock.instant().getEpochSecond();
        Map<String, Object> jwtHeader = new LinkedHashMap<>();
        jwtHeader.put("alg", "RS256");
        jwtHeader.put("typ", "JWT");
        jwtHeader.put(header, B64URL.encodeToString(thumbBytes));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", clientId);
        claims.put("sub", clientId);
        claims.put("aud", tokenUrl);
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("iat", now);
        claims.put("nbf", now);
        claims.put("exp", now + ASSERTION_VALIDITY_SECONDS);
        try {
            String signingInput = B64URL.encodeToString(objectMapper.writeValueAsBytes(jwtHeader)) + "."
                    + B64URL.encodeToString(objectMapper.writeValueAsBytes(claims));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey(require(config, "oauthPrivateKey")));
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + B64URL.encodeToString(signature.sign());
        } catch (GeneralSecurityException | JacksonException ex) {
            throw new IllegalStateException("OAuth2 client assertion could not be signed (check oauthPrivateKey)");
        }
    }

    private static PrivateKey privateKey(String pem) throws GeneralSecurityException {
        String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (IllegalArgumentException ex) {
            throw new GeneralSecurityException("invalid key encoding");
        }
    }

    private static String require(Map<String, Object> config, String name) {
        String value = text(config.get(name));
        if (value.isBlank()) {
            throw new IllegalStateException("OAuth2 configuration is incomplete: " + name + " is required");
        }
        return value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String formEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
