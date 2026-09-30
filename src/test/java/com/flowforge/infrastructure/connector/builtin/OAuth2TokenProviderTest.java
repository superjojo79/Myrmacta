package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.infrastructure.config.ConnectorProperties;
import com.flowforge.infrastructure.notification.OutboundUrlGuard;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** D3a (TODO §77) — jeton OAuth2 client credentials : obtention, cache, renouvellement, sécurité. */
class OAuth2TokenProviderTest {

    private static final String TOKEN_URL = "https://login.example.com/tenant-1/oauth2/v2.0/token";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private MutableClock clock;
    private ScriptedSender sender;
    private OAuth2TokenProvider provider;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-30T10:00:00Z"));
        sender = new ScriptedSender();
        provider = provider(sender);
    }

    private OAuth2TokenProvider provider(HttpRestConnector.HttpSender httpSender) {
        OutboundUrlGuard guard = new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        return new OAuth2TokenProvider(guard, JSON, new ConnectorProperties(null, null, null, null, null, null), httpSender, clock);
    }

    private static Map<String, Object> secretConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("authMode", "OAUTH2_CLIENT_CREDENTIALS");
        config.put("oauthTokenUrl", TOKEN_URL);
        config.put("oauthClientId", "app-123");
        config.put("oauthClientSecret", "s3cr3t-value");
        config.put("oauthScope", "https://erp.example.com/.default");
        return config;
    }

    private static ConnectorInstanceData instance(UUID id, String tenant, Map<String, Object> config) {
        return new ConnectorInstanceData(id, tenant, "erp", "http-rest", true, config);
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return instance(UUID.randomUUID(), "tenant_dev", config);
    }

    private static Map<String, String> form(HttpRestConnector.HttpCallRequest request) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : new String(request.body(), StandardCharsets.UTF_8).split("&")) {
            String[] kv = pair.split("=", 2);
            out.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
        }
        return out;
    }

    @Test
    void fetchesATokenWithClientSecretPostAndCachesIt() {
        sender.respond(200, "{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        ConnectorInstanceData instance = instance(secretConfig());

        assertThat(provider.accessToken(instance)).isEqualTo("tok-1");
        assertThat(provider.accessToken(instance)).isEqualTo("tok-1");

        assertThat(sender.requests).hasSize(1);
        HttpRestConnector.HttpCallRequest request = sender.requests.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.url()).isEqualTo("https://93.184.216.34/tenant-1/oauth2/v2.0/token");
        assertThat(request.headers()).containsEntry("Host", "login.example.com")
                .containsEntry("Content-Type", "application/x-www-form-urlencoded")
                .doesNotContainKey("Authorization");
        assertThat(form(request)).containsEntry("grant_type", "client_credentials")
                .containsEntry("client_id", "app-123")
                .containsEntry("client_secret", "s3cr3t-value")
                .containsEntry("scope", "https://erp.example.com/.default");
    }

    @Test
    void clientSecretBasicUsesTheAuthorizationHeaderAndNoSecretInTheBody() {
        sender.respond(200, "{\"access_token\":\"tok\",\"token_type\":\"bearer\",\"expires_in\":3600}");
        Map<String, Object> config = secretConfig();
        config.put("oauthClientAuthMethod", "CLIENT_SECRET_BASIC");

        provider.accessToken(instance(config));

        HttpRestConnector.HttpCallRequest request = sender.requests.get(0);
        String expected = "Basic " + Base64.getEncoder().encodeToString("app-123:s3cr3t-value".getBytes(StandardCharsets.UTF_8));
        assertThat(request.headers()).containsEntry("Authorization", expected);
        assertThat(form(request)).doesNotContainKeys("client_secret", "client_id");
    }

    @Test
    void basicCredentialsAreFormUrlEncodedBeforeBase64() {
        sender.respond(200, "{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        Map<String, Object> config = secretConfig();
        config.put("oauthClientAuthMethod", "CLIENT_SECRET_BASIC");
        config.put("oauthClientId", "a:b");
        config.put("oauthClientSecret", "p w&d");

        provider.accessToken(instance(config));

        String expected = "Basic " + Base64.getEncoder().encodeToString("a%3Ab:p+w%26d".getBytes(StandardCharsets.UTF_8));
        assertThat(sender.requests.get(0).headers()).containsEntry("Authorization", expected);
    }

    @Test
    void privateKeyJwtSendsASignedAssertionWithTheCertificateThumbprint() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        sender.respond(200, "{\"access_token\":\"tok-jwt\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        Map<String, Object> config = secretConfig();
        config.remove("oauthClientSecret");
        config.put("oauthClientAuthMethod", "PRIVATE_KEY_JWT");
        config.put("oauthPrivateKey", pem);
        config.put("oauthCertThumbprint", "AB".repeat(20));

        assertThat(provider.accessToken(instance(config))).isEqualTo("tok-jwt");

        Map<String, String> body = form(sender.requests.get(0));
        assertThat(body).containsEntry("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                .containsEntry("client_id", "app-123").doesNotContainKey("client_secret");
        String[] parts = body.get("client_assertion").split("\\.");
        JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(header.get("alg").asString()).isEqualTo("RS256");
        assertThat(header.get("x5t").asString()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(hex("AB".repeat(20))));
        assertThat(claims.get("iss").asString()).isEqualTo("app-123");
        assertThat(claims.get("sub").asString()).isEqualTo("app-123");
        assertThat(claims.get("aud").asString()).isEqualTo(TOKEN_URL);
        assertThat(claims.get("jti").asString()).isNotBlank();
        assertThat(claims.get("exp").asLong() - clock.instant().getEpochSecond()).isBetween(1L, 600L);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    void aSha256ThumbprintUsesTheX5tS256Header() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        sender.respond(200, "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        Map<String, Object> config = secretConfig();
        config.remove("oauthClientSecret");
        config.put("oauthClientAuthMethod", "PRIVATE_KEY_JWT");
        config.put("oauthPrivateKey", pem);
        config.put("oauthCertThumbprint", "0a".repeat(32));

        provider.accessToken(instance(config));

        String assertion = form(sender.requests.get(0)).get("client_assertion");
        JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(assertion.split("\\.")[0]));
        assertThat(header.has("x5t#S256")).isTrue();
        assertThat(header.has("x5t")).isFalse();
    }

    @Test
    void renewsTheTokenBeforeItExpires() {
        sender.respond(200, "{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        ConnectorInstanceData instance = instance(secretConfig());
        provider.accessToken(instance);

        clock.advance(Duration.ofSeconds(3600 - 61));
        assertThat(provider.accessToken(instance)).isEqualTo("tok-1");
        assertThat(sender.requests).hasSize(1);

        sender.respond(200, "{\"access_token\":\"tok-2\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        clock.advance(Duration.ofSeconds(2));
        assertThat(provider.accessToken(instance)).isEqualTo("tok-2");
        assertThat(sender.requests).hasSize(2);
    }

    @Test
    void aShortLivedTokenIsStillUsableBriefly() {
        sender.respond(200, "{\"access_token\":\"short\",\"token_type\":\"Bearer\",\"expires_in\":10}");
        ConnectorInstanceData instance = instance(secretConfig());
        provider.accessToken(instance);
        clock.advance(Duration.ofSeconds(4));
        assertThat(provider.accessToken(instance)).isEqualTo("short");
        assertThat(sender.requests).hasSize(1);
    }

    @Test
    void invalidateForcesANewTokenOnTheNextCall() {
        sender.respond(200, "{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        ConnectorInstanceData instance = instance(secretConfig());
        provider.accessToken(instance);
        provider.invalidate(instance);
        sender.respond(200, "{\"access_token\":\"tok-2\",\"token_type\":\"Bearer\",\"expires_in\":3600}");

        assertThat(provider.accessToken(instance)).isEqualTo("tok-2");
    }

    @Test
    void aConfigurationChangeNeverReusesTheOldToken() {
        UUID id = UUID.randomUUID();
        sender.respond(200, "{\"access_token\":\"old\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        provider.accessToken(instance(id, "tenant_dev", secretConfig()));
        sender.respond(200, "{\"access_token\":\"new\",\"token_type\":\"Bearer\",\"expires_in\":3600}");

        Map<String, Object> rotated = secretConfig();
        rotated.put("oauthClientSecret", "rotated");
        assertThat(provider.accessToken(instance(id, "tenant_dev", rotated))).isEqualTo("new");

        Map<String, Object> otherScope = secretConfig();
        otherScope.put("oauthScope", "other/.default");
        sender.respond(200, "{\"access_token\":\"scoped\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        assertThat(provider.accessToken(instance(id, "tenant_dev", otherScope))).isEqualTo("scoped");
    }

    @Test
    void tenantsAndInstancesNeverShareAToken() {
        UUID id = UUID.randomUUID();
        sender.respond(200, "{\"access_token\":\"A\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        assertThat(provider.accessToken(instance(id, "tenant_a", secretConfig()))).isEqualTo("A");
        sender.respond(200, "{\"access_token\":\"B\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        assertThat(provider.accessToken(instance(id, "tenant_b", secretConfig()))).isEqualTo("B");
        sender.respond(200, "{\"access_token\":\"C\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        assertThat(provider.accessToken(instance(UUID.randomUUID(), "tenant_a", secretConfig()))).isEqualTo("C");
    }

    @Test
    void concurrentCallsShareASingleTokenRequest() throws Exception {
        CountDownLatch inFlight = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        OAuth2TokenProvider slow = provider(request -> {
            calls.incrementAndGet();
            try {
                inFlight.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new HttpRestConnector.HttpCallResponse(200, "{\"access_token\":\"one\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        });
        ConnectorInstanceData instance = instance(secretConfig());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> slow.accessToken(instance)));
            }
            Thread.sleep(200);
            inFlight.countDown();
            for (Future<String> f : futures) {
                assertThat(f.get()).isEqualTo("one");
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(calls).hasValue(1);
    }

    @Test
    void theCacheIsBounded() {
        sender.respond(200, "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        for (int i = 0; i < OAuth2TokenProvider.MAX_CACHED_TOKENS + 50; i++) {
            provider.accessToken(instance(UUID.randomUUID(), "tenant_dev", secretConfig()));
        }
        assertThat(provider.cacheSize()).isLessThanOrEqualTo(OAuth2TokenProvider.MAX_CACHED_TOKENS);
    }

    @Test
    void errorsAreNeutralAndNeverLeakTheSecretTheTokenOrTheIdpBody() {
        sender.respond(401, "{\"error\":\"invalid_client\",\"error_description\":\"AADSTS7000215: Invalid client secret s3cr3t-value\"}");

        assertThatThrownBy(() -> provider.accessToken(instance(secretConfig())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 401").hasMessageContaining("invalid_client")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("s3cr3t-value", "AADSTS"));
    }

    @Test
    void anUnexpectedErrorCodeIsNotEchoed() {
        sender.respond(400, "{\"error\":\"<script>alert(1)</script> s3cr3t-value\"}");

        assertThatThrownBy(() -> provider.accessToken(instance(secretConfig())))
                .hasMessageContaining("HTTP 400").satisfies(e -> assertThat(e.getMessage()).doesNotContain("script", "s3cr3t"));
    }

    @Test
    void refusesANonBearerTokenAMissingTokenAndGarbage() {
        for (String body : List.of(
                "{\"access_token\":\"t\",\"token_type\":\"MAC\",\"expires_in\":3600}",
                "{\"token_type\":\"Bearer\",\"expires_in\":3600}",
                "{\"access_token\":\"\",\"token_type\":\"Bearer\"}",
                "{\"access_token\":\"a\\r\\nX-Injected: 1\",\"token_type\":\"Bearer\"}",
                "not json",
                "[1,2]")) {
            sender.respond(200, body);
            assertThatThrownBy(() -> provider.accessToken(instance(secretConfig())))
                    .as(body).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void expiresInIsSanitised() {
        sender.respond(200, "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":\"7200\"}");
        ConnectorInstanceData a = instance(secretConfig());
        provider.accessToken(a);
        clock.advance(Duration.ofSeconds(7000));
        provider.accessToken(a);
        assertThat(sender.requests).hasSize(1);

        // négatif / absurde : jamais plus de 24 h, jamais mis en cache sans borne
        sender.respond(200, "{\"access_token\":\"t2\",\"token_type\":\"Bearer\",\"expires_in\":99999999999}");
        ConnectorInstanceData b = instance(secretConfig());
        provider.accessToken(b);
        clock.advance(Duration.ofHours(25));
        sender.respond(200, "{\"access_token\":\"t3\",\"token_type\":\"Bearer\",\"expires_in\":-5}");
        assertThat(provider.accessToken(b)).isEqualTo("t3");
        assertThat(provider.accessToken(b)).isEqualTo("t3");
    }

    @Test
    void theTokenEndpointGoesThroughTheSsrfGuard() {
        Map<String, Object> config = secretConfig();
        config.put("oauthTokenUrl", "https://127.0.0.1/token");
        OutboundUrlGuard real = new OutboundUrlGuard(false);
        OAuth2TokenProvider guarded = new OAuth2TokenProvider(real, JSON, new ConnectorProperties(null, null, null, null, null, null), sender, clock);

        assertThatThrownBy(() -> guarded.accessToken(instance(config))).isInstanceOf(IllegalArgumentException.class);
        assertThat(sender.requests).isEmpty();
    }

    @Test
    void ioFailuresAreWrappedWithoutLeakingSecrets() {
        sender.error = new IOException("connection reset");
        assertThatThrownBy(() -> provider.accessToken(instance(secretConfig())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("connection reset")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("s3cr3t-value"));
    }

    @Test
    void anOversizedResponseIsRejected() {
        sender.respond(200, "x".repeat(6_000_000));
        assertThatThrownBy(() -> provider.accessToken(instance(secretConfig()))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anInvalidPrivateKeyIsRejectedWithoutEchoingIt() {
        Map<String, Object> config = secretConfig();
        config.remove("oauthClientSecret");
        config.put("oauthClientAuthMethod", "PRIVATE_KEY_JWT");
        config.put("oauthPrivateKey", "-----BEGIN PRIVATE KEY-----\nQUJDREVGRw==\n-----END PRIVATE KEY-----");
        config.put("oauthCertThumbprint", "AB".repeat(20));

        assertThatThrownBy(() -> provider.accessToken(instance(config)))
                .isInstanceOf(IllegalStateException.class).satisfies(e -> assertThat(e.getMessage()).doesNotContain("QUJDREVGRw"));
        assertThat(sender.requests).isEmpty();
    }

    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(value.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    private static final class ScriptedSender implements HttpRestConnector.HttpSender {
        final List<HttpRestConnector.HttpCallRequest> requests = new ArrayList<>();
        int status = 200;
        String body = "{}";
        IOException error;

        void respond(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public synchronized HttpRestConnector.HttpCallResponse send(HttpRestConnector.HttpCallRequest request) throws IOException {
            requests.add(request);
            if (error != null) {
                throw error;
            }
            return new HttpRestConnector.HttpCallResponse(status, body);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
