package be.myrmacta.infrastructure.signature;

import be.myrmacta.infrastructure.assistant.BoundedResponseBody;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Client HTTP du JDK vers l'API eSignature DocuSign (TODO §109, D-SIGNATURE-HTTP). Jeton JWT
 * (RS256), pas de redirection, corps borné. Le secret, l'assertion et le lien de signature sont
 * absents des erreurs. L'hôte OAuth est fixé par l'environnement. L'hôte du compte, rendu par
 * {@code userinfo}, est contrôlé avant tout appel REST (D-SIGNATURE-LINK).
 */
final class DocusignHttpClient implements DocusignClient {

    static final URI SANDBOX_OAUTH = URI.create("https://account-d.docusign.com");
    static final URI PRODUCTION_OAUTH = URI.create("https://account.docusign.com");
    private static final String ANCHOR_MISSING = "ANCHOR_TAB_STRING_NOT_FOUND";
    private static final String NOT_IN_SEQUENCE = "RECIPIENT_NOT_IN_SEQUENCE";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_JSON_BYTES = 1024 * 1024;
    private static final int MAX_BINARY_BYTES = 20 * 1024 * 1024;
    private static final long ASSERTION_SECONDS = 600;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    static URI oauthFor(String environment) {
        return switch (environment == null ? "" : environment) {
            case "sandbox" -> SANDBOX_OAUTH;
            case "production" -> PRODUCTION_OAUTH;
            default -> throw new DocusignCallException("DocuSign environment is not allowed");
        };
    }

    /**
     * Hôte REST du compte. Bac à sable : {@code demo.docusign.net}. Production : un hôte
     * {@code *.docusign.net} autre que le bac à sable. {@code permitLoopback} ne sert qu'aux tests.
     */
    static URI accountBase(String environment, String baseUri, boolean permitLoopback) {
        URI uri;
        try {
            uri = URI.create(baseUri);
        } catch (IllegalArgumentException ex) {
            throw new DocusignCallException("DocuSign account host is not allowed");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean loopback = permitLoopback && ("127.0.0.1".equals(host) || "localhost".equals(host));
        boolean sandbox = "sandbox".equals(environment) && "demo.docusign.net".equals(host);
        boolean production = "production".equals(environment)
                && host.endsWith(".docusign.net")
                && !host.equals("demo.docusign.net")
                && host.matches("[a-z0-9.-]+");
        String path = uri.getPath() == null ? "" : uri.getPath();
        int port = uri.getPort();
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean httpLoop = loopback && "http".equalsIgnoreCase(uri.getScheme());
        boolean portOk = loopback || port == -1 || port == 443;
        if (uri.getUserInfo() != null || uri.getRawQuery() != null || !(path.isEmpty() || "/".equals(path))
                || !(sandbox || production || loopback) || !(https || httpLoop) || !portOk) {
            throw new DocusignCallException("DocuSign account host is not allowed");
        }
        return uri;
    }

    /** Lien https dont l'hôte est docusign.net ou docusign.com. Le lien n'est pas recopié dans l'erreur. */
    static String allowedSigningLink(String link) {
        URI uri;
        try {
            uri = URI.create(link);
        } catch (IllegalArgumentException ex) {
            throw new DocusignCallException("DocuSign signing URL is not allowed");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowedHost = host.equals("docusign.net") || host.endsWith(".docusign.net")
                || host.equals("docusign.com") || host.endsWith(".docusign.com");
        int port = uri.getPort();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || !allowedHost
                || (port != -1 && port != 443)) {
            throw new DocusignCallException("DocuSign signing URL is not allowed");
        }
        return link;
    }

    private final HttpClient http;
    private final URI oauthBase;
    private final String environment;
    private final String integrationKey;
    private final String userId;
    private final String privateKeyPem;
    private final String configuredAccountId;
    private final Clock clock;
    private final boolean permitLoopback;
    private final DocusignTokenCache cache;

    DocusignHttpClient(
            String environment,
            String integrationKey,
            String userId,
            String privateKeyPem,
            String configuredAccountId,
            Clock clock,
            DocusignTokenCache cache) {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), oauthFor(environment), environment, integrationKey, userId, privateKeyPem,
                configuredAccountId, clock, false, cache);
    }

    DocusignHttpClient(
            HttpClient http,
            URI oauthBase,
            String environment,
            String integrationKey,
            String userId,
            String privateKeyPem,
            String configuredAccountId,
            Clock clock,
            boolean permitLoopback,
            DocusignTokenCache cache) {
        this.http = http;
        this.oauthBase = oauthBase;
        this.environment = environment;
        this.integrationKey = integrationKey;
        this.userId = userId;
        this.privateKeyPem = privateKeyPem;
        this.configuredAccountId = blankToNull(configuredAccountId);
        this.clock = clock;
        this.permitLoopback = permitLoopback;
        this.cache = cache;
    }

    @Override
    public UUID createEnvelope(
            String name, boolean ordered, String externalId, Instant deadline, List<SignerSpec> signers, byte[] pdf) {
        Session session = session();
        ObjectNode body = JSON.createObjectNode();
        body.put("emailSubject", clip(name == null || name.isBlank() ? "document" : name, 100));
        body.put("status", "sent");
        if (externalId != null && !externalId.isBlank()) {
            ObjectNode field = JSON.createObjectNode();
            field.put("name", "myrmactaRequestId");
            field.put("value", clip(externalId, 100));
            field.put("show", "false");
            ArrayNode fields = JSON.createArrayNode();
            fields.add(field);
            ObjectNode custom = JSON.createObjectNode();
            custom.set("textCustomFields", fields);
            body.set("customFields", custom);
        }
        if (deadline != null) {
            long days = Math.max(1, Math.min(999, Duration.between(clock.instant(), deadline).toDays()));
            ObjectNode expirations = JSON.createObjectNode();
            expirations.put("expireEnabled", "true");
            expirations.put("expireAfter", Long.toString(days));
            ObjectNode notification = JSON.createObjectNode();
            notification.put("useAccountDefaults", "false");
            notification.set("expirations", expirations);
            body.set("notification", notification);
        }
        ObjectNode document = JSON.createObjectNode();
        document.put("documentBase64", Base64.getEncoder().encodeToString(pdf == null ? new byte[0] : pdf));
        document.put("name", clip(name == null || name.isBlank() ? "document.pdf" : name, 100));
        document.put("fileExtension", "pdf");
        document.put("documentId", "1");
        ArrayNode documents = JSON.createArrayNode();
        documents.add(document);
        body.set("documents", documents);
        ArrayNode recipients = JSON.createArrayNode();
        for (SignerSpec signer : signers) {
            int rank = signer.position() + 1;
            ObjectNode tab = JSON.createObjectNode();
            tab.put("anchorString", "{{s" + rank + "|signature|85|37}}");
            tab.put("anchorUnits", "pixels");
            tab.put("anchorXOffset", "0");
            tab.put("anchorYOffset", "0");
            tab.put("anchorIgnoreIfNotPresent", "false");
            ArrayNode tabs = JSON.createArrayNode();
            tabs.add(tab);
            ObjectNode signHere = JSON.createObjectNode();
            signHere.set("signHereTabs", tabs);
            ObjectNode one = JSON.createObjectNode();
            one.put("email", signer.email());
            one.put("name", clip(signer.name(), 100));
            one.put("recipientId", Integer.toString(rank));
            one.put("clientUserId", Integer.toString(rank));
            one.put("routingOrder", ordered ? Integer.toString(rank) : "1");
            one.set("tabs", signHere);
            recipients.add(one);
        }
        ObjectNode recipientNode = JSON.createObjectNode();
        recipientNode.set("signers", recipients);
        body.set("recipients", recipientNode);
        JsonNode created = postJson(session, "/envelopes", body.toString());
        return uuid(created, "envelopeId");
    }

    @Override
    public void voidEnvelope(UUID envelopeId) {
        ObjectNode body = JSON.createObjectNode();
        body.put("status", "voided");
        body.put("voidedReason", "cancelled");
        putJson(session(), "/envelopes/" + envelopeId, body.toString());
    }

    @Override
    public Optional<String> recipientView(UUID envelopeId, int signerPosition, String returnUrl) {
        Session session = session();
        String clientUserId = Integer.toString(signerPosition + 1);
        JsonNode signers = getJson(session, "/envelopes/" + envelopeId + "/recipients").path("signers");
        JsonNode signer = null;
        if (signers.isArray()) {
            for (JsonNode candidate : signers) {
                if (clientUserId.equals(candidate.path("recipientId").asString())
                        || clientUserId.equals(candidate.path("clientUserId").asString())) {
                    signer = candidate;
                    break;
                }
            }
        }
        if (signer == null) {
            throw new DocusignCallException("DocuSign signer position is unknown");
        }
        ObjectNode body = JSON.createObjectNode();
        body.put("returnUrl", returnUrl);
        body.put("authenticationMethod", "none");
        body.put("email", signer.path("email").asString());
        body.put("userName", signer.path("name").asString());
        body.put("clientUserId", clientUserId);
        HttpRequest request = authorized(session, session.account.resolve("/restapi/v2.1/accounts/"
                + session.accountId + "/envelopes/" + envelopeId + "/views/recipient"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<byte[]> response = exchange(request, MAX_JSON_BYTES);
        if (response.statusCode() == 400 && NOT_IN_SEQUENCE.equals(errorCode(response.body()))) {
            return Optional.empty();
        }
        JsonNode created = read(response);
        JsonNode url = created.get("url");
        if (url == null || url.isNull() || url.asString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(allowedSigningLink(url.asString()));
    }

    @Override
    public byte[] downloadSignedPdf(UUID envelopeId) {
        return getBytes(session(), "/envelopes/" + envelopeId + "/documents/1");
    }

    @Override
    public byte[] downloadCertificate(UUID envelopeId) {
        return getBytes(session(), "/envelopes/" + envelopeId + "/documents/certificate");
    }

    private Session session() {
        if (integrationKey == null || integrationKey.isBlank() || userId == null || userId.isBlank()) {
            throw new DocusignCallException("DocuSign credentials are missing");
        }
        String cacheKey = environment + "|" + integrationKey + "|" + userId + "|"
                + (configuredAccountId == null ? "" : configuredAccountId);
        Instant now = clock.instant();
        Optional<DocusignTokenCache.Entry> cached = cache.valid(cacheKey, now);
        if (cached.isPresent()) {
            DocusignTokenCache.Entry entry = cached.get();
            return new Session(entry.accessToken(), entry.accountId(), entry.accountBase());
        }
        Issued issued = accessToken(now);
        Account account = account(issued.token());
        cache.remember(cacheKey, new DocusignTokenCache.Entry(
                issued.token(), now.plusSeconds(Math.max(30, issued.expiresInSeconds() - 60)),
                account.accountId(), account.base()));
        return new Session(issued.token(), account.accountId(), account.base());
    }

    private Issued accessToken(Instant now) {
        String assertion = assertion(now);
        String form = "grant_type=" + url("urn:ietf:params:oauth:grant-type:jwt-bearer")
                + "&assertion=" + url(assertion);
        HttpRequest request = HttpRequest.newBuilder(oauthBase.resolve("/oauth/token"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        JsonNode token = read(exchange(request, MAX_JSON_BYTES));
        String access = token.path("access_token").asString();
        if (access.isBlank()) {
            throw new DocusignCallException("DocuSign returned an unexpected payload");
        }
        JsonNode expires = token.get("expires_in");
        long seconds = expires == null || !expires.isNumber() ? 3600 : expires.asLong();
        if (seconds < 120) {
            seconds = 120;
        }
        if (seconds > 3600) {
            seconds = 3600;
        }
        return new Issued(access, seconds);
    }

    private Account account(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(oauthBase.resolve("/oauth/userinfo"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        JsonNode info = read(exchange(request, MAX_JSON_BYTES));
        JsonNode accounts = info.path("accounts");
        if (!accounts.isArray() || accounts.isEmpty()) {
            throw new DocusignCallException("DocuSign account is not available");
        }
        JsonNode chosen = null;
        if (configuredAccountId != null) {
            for (JsonNode account : accounts) {
                if (configuredAccountId.equals(account.path("account_id").asString())) {
                    chosen = account;
                    break;
                }
            }
            if (chosen == null) {
                throw new DocusignCallException("DocuSign account is not available");
            }
        } else if (accounts.size() == 1) {
            chosen = accounts.get(0);
        } else {
            for (JsonNode account : accounts) {
                if (truth(account.get("is_default"))) {
                    chosen = account;
                    break;
                }
            }
            if (chosen == null) {
                throw new DocusignCallException("DocuSign account is ambiguous");
            }
        }
        String accountId = chosen.path("account_id").asString();
        if (accountId.isBlank()) {
            throw new DocusignCallException("DocuSign returned an unexpected payload");
        }
        URI base = accountBase(environment, chosen.path("base_uri").asString(), permitLoopback);
        return new Account(accountId, base);
    }

    private String assertion(Instant now) {
        long iat = now.getEpochSecond();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", integrationKey);
        claims.put("sub", userId);
        claims.put("aud", oauthBase.getHost());
        claims.put("iat", iat);
        claims.put("exp", iat + ASSERTION_SECONDS);
        claims.put("scope", "signature impersonation");
        try {
            String signingInput = B64URL.encodeToString(JSON.writeValueAsBytes(header)) + "."
                    + B64URL.encodeToString(JSON.writeValueAsBytes(claims));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(RsaPrivateKeys.parse(privateKeyPem));
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + B64URL.encodeToString(signature.sign());
        } catch (GeneralSecurityException | JacksonException ex) {
            throw new DocusignCallException("DocuSign private key could not be read");
        }
    }

    private JsonNode postJson(Session session, String path, String json) {
        HttpRequest request = authorized(session, accountUri(session, path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return read(exchange(request, MAX_JSON_BYTES));
    }

    private void putJson(Session session, String path, String json) {
        HttpRequest request = authorized(session, accountUri(session, path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        read(exchange(request, MAX_JSON_BYTES));
    }

    private JsonNode getJson(Session session, String path) {
        HttpRequest request = authorized(session, accountUri(session, path)).GET().build();
        return read(exchange(request, MAX_JSON_BYTES));
    }

    private byte[] getBytes(Session session, String path) {
        HttpRequest request = authorized(session, accountUri(session, path)).GET().build();
        HttpResponse<byte[]> response = exchange(request, MAX_BINARY_BYTES);
        if (response.statusCode() / 100 != 2) {
            throw new DocusignCallException("DocuSign answered HTTP " + response.statusCode());
        }
        return response.body();
    }

    private HttpRequest.Builder authorized(Session session, URI uri) {
        return HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("Authorization", "Bearer " + session.accessToken);
    }

    private static URI accountUri(Session session, String path) {
        return session.account.resolve("/restapi/v2.1/accounts/" + session.accountId + path);
    }

    private HttpResponse<byte[]> exchange(HttpRequest request, int maxBytes) {
        try {
            HttpResponse<byte[]> response = BoundedResponseBody.send(http, request, TIMEOUT, maxBytes);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                throw new DocusignCallException("DocuSign redirected the call");
            }
            return response;
        } catch (DocusignCallException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new DocusignCallException("DocuSign call failed");
        }
    }

    private static JsonNode read(HttpResponse<byte[]> response) {
        if (response.statusCode() / 100 != 2) {
            if (ANCHOR_MISSING.equals(errorCode(response.body()))) {
                throw new DocusignAnchorMissingException();
            }
            throw new DocusignCallException("DocuSign answered HTTP " + response.statusCode());
        }
        try {
            return JSON.readTree(response.body());
        } catch (JacksonException ex) {
            throw new DocusignCallException("DocuSign returned an unexpected payload");
        }
    }

    private static String errorCode(byte[] body) {
        try {
            JsonNode code = JSON.readTree(body == null ? new byte[0] : body).get("errorCode");
            if (code != null && code.isString() && code.asString().matches("[A-Z0-9_]{1,64}")) {
                return code.asString();
            }
        } catch (JacksonException ex) {
            return null;
        }
        return null;
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(node.path(field).asString());
        } catch (IllegalArgumentException ex) {
            throw new DocusignCallException("DocuSign returned an unexpected payload");
        }
    }

    private static boolean truth(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return "true".equalsIgnoreCase(node.asString());
    }

    private static String clip(String value, int max) {
        String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Session(String accessToken, String accountId, URI account) {}

    private record Issued(String token, long expiresInSeconds) {}

    private record Account(String accountId, URI base) {}

    /** Jeton et compte, réutilisés jusqu'à une minute avant l'expiration annoncée. */
    static final class DocusignTokenCache {
        private final Map<String, Entry> entries = new java.util.HashMap<>();

        synchronized Optional<Entry> valid(String key, Instant now) {
            Entry entry = entries.get(key);
            if (entry == null || !entry.expiresAt.isAfter(now)) {
                return Optional.empty();
            }
            return Optional.of(entry);
        }

        synchronized void remember(String key, Entry entry) {
            if (entries.size() > 8) {
                entries.clear();
            }
            entries.put(key, entry);
        }

        record Entry(String accessToken, Instant expiresAt, String accountId, URI accountBase) {}
    }
}
