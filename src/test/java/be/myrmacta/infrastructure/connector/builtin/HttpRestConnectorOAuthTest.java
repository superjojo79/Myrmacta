package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** D3a (TODO §77) — `http-rest` en mode OAuth2 client credentials : en-têtes, rejeu 401, hôtes autorisés, validation. */
class HttpRestConnectorOAuthTest {

    private static final String API = "https://erp.example.com/data/Vendors";
    private final List<HttpRestConnector.HttpCallRequest> calls = new ArrayList<>();
    private final List<Integer> apiStatuses = new ArrayList<>();
    private int tokenCounter;
    private boolean ageTokenOn401 = true;
    private HttpRestConnector connector;
    private final java.util.concurrent.atomic.AtomicReference<java.time.Instant> now =
            new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.parse("2026-09-30T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return now.get();
        }
    };

    @BeforeEach
    void setUp() {
        HttpRestConnector.HttpSender sender = request -> {
            calls.add(request);
            if ("login.example.com".equals(request.tlsHostname())) {
                tokenCounter++;
                return new HttpRestConnector.HttpCallResponse(
                        200, "{\"access_token\":\"tok-" + tokenCounter + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
            }
            int status = apiStatuses.isEmpty() ? 200 : apiStatuses.remove(0);
            if (status == 401 && ageTokenOn401) {
                // Le jeton rejeté n'est pas « tout neuf » : seul un jeton plus ancien est renouvelé de force.
                now.updateAndGet(t -> t.plusSeconds(31));
            }
            return new HttpRestConnector.HttpCallResponse(status, "{\"ok\":true}");
        };
        OutboundUrlGuard guard = new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        connector = new HttpRestConnector(new SimpleJsonPathMapper(), guard, JsonMapper.builder().build(),
                new ConnectorProperties(null, null, null, null, null, null), sender, clock);
    }

    private static Map<String, Object> oauthConfig() {
        Map<String, Object> c = new HashMap<>();
        c.put("webhookAuthType", "SHARED_SECRET_TOKEN");
        c.put("webhookSecret", "a-long-enough-shared-secret-value");
        c.put("processKey", "p");
        c.put("dossierTypeCode", "T");
        c.put("authMode", "OAUTH2_CLIENT_CREDENTIALS");
        c.put("oauthTokenUrl", "https://login.example.com/t/oauth2/v2.0/token");
        c.put("oauthClientId", "app");
        c.put("oauthClientSecret", "sekret");
        c.put("oauthAllowedHosts", "erp.example.com, *.crm.example.com");
        c.put("oauthScope", "https://erp.example.com/.default");
        return c;
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return new ConnectorInstanceData(UUID.randomUUID(), "tenant_dev", "erp", "http-rest", true, config);
    }

    private List<HttpRestConnector.HttpCallRequest> apiCalls() {
        return calls.stream().filter(c -> "erp.example.com".equals(c.tlsHostname())).toList();
    }

    @Test
    void sendsABearerTokenAndReusesItAcrossCalls() {
        ConnectorInstanceData instance = instance(oauthConfig());

        Map<String, Object> result = connector.execute(instance, Map.of("url", API));
        connector.execute(instance, Map.of("url", API));

        assertThat(result).containsEntry("status", 200);
        assertThat(tokenCounter).isEqualTo(1);
        assertThat(apiCalls()).hasSize(2).allSatisfy(c -> assertThat(c.headers()).containsEntry("Authorization", "Bearer tok-1"));
        assertThat(result.toString()).doesNotContain("tok-1", "sekret");
    }

    @Test
    void aCallerSuppliedAuthorizationHeaderIsIgnoredInOAuthMode() {
        connector.execute(instance(oauthConfig()),
                Map.of("url", API, "headers", Map.of("authorization", "Bearer attacker", "X-Other", "1")));

        Map<String, String> headers = apiCalls().get(0).headers();
        assertThat(headers).containsEntry("Authorization", "Bearer tok-1").containsEntry("X-Other", "1");
        assertThat(headers.keySet().stream().filter("authorization"::equalsIgnoreCase)).hasSize(1);
    }

    @Test
    void a401RefreshesTheTokenAndRetriesExactlyOnce() {
        apiStatuses.add(401);
        apiStatuses.add(200);

        Map<String, Object> result = connector.execute(instance(oauthConfig()), Map.of("url", API, "method", "POST", "body", Map.of("a", 1)));

        assertThat(result).containsEntry("status", 200);
        assertThat(tokenCounter).isEqualTo(2);
        assertThat(apiCalls()).hasSize(2);
        assertThat(apiCalls().get(0).headers()).containsEntry("Authorization", "Bearer tok-1");
        assertThat(apiCalls().get(1).headers()).containsEntry("Authorization", "Bearer tok-2");
        assertThat(new String(apiCalls().get(1).body())).isEqualTo("{\"a\":1}");
    }

    @Test
    void aSecond401IsReturnedAsIsWithoutALoop() {
        apiStatuses.add(401);
        apiStatuses.add(401);
        apiStatuses.add(200);

        assertThat(connector.execute(instance(oauthConfig()), Map.of("url", API))).containsEntry("status", 401);
        assertThat(apiCalls()).hasSize(2);
        assertThat(tokenCounter).isEqualTo(2);
    }

    @Test
    void a401ThatPersistsOnAFreshTokenIsNotRetriedAndDoesNotHammerTheIdp() {
        ageTokenOn401 = false;
        ConnectorInstanceData instance = instance(oauthConfig());
        connector.execute(instance, Map.of("url", API)); // émet le jeton 1 à l'instant T
        for (int i = 0; i < 10; i++) {
            apiStatuses.add(401);
        }

        for (int i = 0; i < 10; i++) {
            assertThat(connector.execute(instance, Map.of("url", "https://erp.example.com/data/Vendors", "method", "POST", "body", "x")))
                    .containsEntry("status", 401);
        }

        assertThat(tokenCounter).as("aucun renouvellement forcé sur un jeton de moins de 30 s").isEqualTo(1);
        assertThat(apiCalls()).as("1 appel initial + 10 appels sans rejeu").hasSize(11);
    }

    @Test
    void anIdpFailureIsRememberedBrieflyInsteadOfBeingHammered() {
        int[] idpCalls = {0};
        OutboundUrlGuard guard = new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        HttpRestConnector failing = new HttpRestConnector(new SimpleJsonPathMapper(), guard, JsonMapper.builder().build(),
                new ConnectorProperties(null, null, null, null, null, null), request -> {
                    idpCalls[0]++;
                    return new HttpRestConnector.HttpCallResponse(429, "{\"error\":\"temporarily_unavailable\"}");
                }, clock);
        ConnectorInstanceData instance = instance(oauthConfig());
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> failing.execute(instance, Map.of("url", API))).hasMessageContaining("429");
        }
        assertThat(idpCalls[0]).isEqualTo(1);
        now.updateAndGet(t -> t.plusSeconds(6));
        assertThatThrownBy(() -> failing.execute(instance, Map.of("url", API))).hasMessageContaining("429");
        assertThat(idpCalls[0]).isEqualTo(2);
    }

    @Test
    void anAuthorizationHeaderWithTrailingSpaceIsAlsoIgnored() {
        connector.execute(instance(oauthConfig()), Map.of("url", API, "headers", Map.of("Authorization ", "Bearer attacker")));
        assertThat(apiCalls().get(0).headers().keySet().stream().filter(k -> k.trim().equalsIgnoreCase("authorization"))).hasSize(1);
    }

    @Test
    void theTokenIsNeverSentToAnUndeclaredHostAndNoTokenIsRequested() {
        assertThatThrownBy(() -> connector.execute(instance(oauthConfig()), Map.of("url", "https://evil.example.net/steal")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not allowed");
        assertThatThrownBy(() -> connector.execute(instance(oauthConfig()), Map.of("url", "https://erp.example.com.evil.net/x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.execute(instance(oauthConfig()), Map.of("url", "https://erp.example.com@evil.net/x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).isEmpty();
    }

    @Test
    void wildcardHostsMatchSubdomainsOnly() {
        connector.execute(instance(oauthConfig()), Map.of("url", "https://org.crm.example.com/api"));
        assertThat(tokenCounter).isEqualTo(1);
        assertThatThrownBy(() -> connector.execute(instance(oauthConfig()), Map.of("url", "https://crm.example.com/api")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.execute(instance(oauthConfig()), Map.of("url", "https://xcrm.example.com/api")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.execute(instance(oauthConfig()), Map.of("url", "https://a.org.crm.example.com/api")))
                .as("a wildcard covers a single label").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staticHeaderModeIsUnchangedAndNeverRequestsAToken() {
        Map<String, Object> config = new HashMap<>();
        config.put("authHeaderValue", "Basic abc");
        connector.execute(instance(config), Map.of("url", API));

        assertThat(apiCalls().get(0).headers()).containsEntry("Authorization", "Basic abc");
        assertThat(tokenCounter).isZero();
    }

    @Test
    void theSchemaDeclaresTheSecretsAsEncryptedFieldTypes() {
        Map<String, ConfigFieldType> types = new HashMap<>();
        for (ConfigField f : connector.getConfigSchema()) {
            types.put(f.name(), f.type());
            if (f.name().startsWith("oauth") || f.name().equals("authMode")) {
                assertThat(f.required()).as(f.name()).isFalse();
            }
        }
        assertThat(types).containsEntry("oauthClientSecret", ConfigFieldType.PASSWORD)
                .containsEntry("oauthPrivateKey", ConfigFieldType.SECRET_TEXTAREA)
                .containsEntry("authMode", ConfigFieldType.SELECT);
    }

    @Test
    void validateAcceptsACompleteSecretConfigurationAndAnExistingOneWithoutAuthMode() {
        connector.validate(oauthConfig());
        Map<String, Object> legacy = oauthConfig();
        legacy.keySet().removeIf(k -> k.startsWith("oauth") || k.equals("authMode"));
        legacy.put("authHeaderValue", "Bearer x");
        connector.validate(legacy);
    }

    @Test
    void validateRefusesIncompleteOrAmbiguousOAuthConfigurations() {
        for (String missing : List.of("oauthTokenUrl", "oauthClientId", "oauthClientSecret", "oauthAllowedHosts")) {
            Map<String, Object> c = oauthConfig();
            c.remove(missing);
            assertThatThrownBy(() -> connector.validate(c)).as(missing).isInstanceOf(ConnectorValidationException.class);
        }
        // Un authHeaderValue hérité du mode statique est ignoré (jamais envoyé) : on peut migrer sans recréer l'instance.
        Map<String, Object> legacyHeader = oauthConfig();
        legacyHeader.put("authHeaderValue", "Bearer x");
        connector.validate(legacyHeader);
        connector.execute(instance(legacyHeader), Map.of("url", API));
        assertThat(apiCalls().get(0).headers()).containsEntry("Authorization", "Bearer tok-1");

        Map<String, Object> unknownMode = oauthConfig();
        unknownMode.put("authMode", "NTLM");
        assertThatThrownBy(() -> connector.validate(unknownMode)).hasMessageContaining("authMode");

        Map<String, Object> unknownMethod = oauthConfig();
        unknownMethod.put("oauthClientAuthMethod", "NONE");
        assertThatThrownBy(() -> connector.validate(unknownMethod)).hasMessageContaining("oauthClientAuthMethod");

        for (String bad : List.of("*.com", "com", "localhost", "1.2.3.4", "*.azurewebsites.net", "*.x.herokuapp.com", "*.github.io", "*")) {
            Map<String, Object> c = oauthConfig();
            c.put("oauthAllowedHosts", bad);
            assertThatThrownBy(() -> connector.validate(c)).as(bad).isInstanceOf(ConnectorValidationException.class);
        }
        Map<String, Object> badHost = oauthConfig();
        badHost.put("oauthAllowedHosts", "erp.example.com/path");
        assertThatThrownBy(() -> connector.validate(badHost)).hasMessageContaining("oauthAllowedHosts");

        Map<String, Object> emptyHosts = oauthConfig();
        emptyHosts.put("oauthAllowedHosts", " , ");
        assertThatThrownBy(() -> connector.validate(emptyHosts)).isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void validateRefusesAnUnsafeTokenUrl() {
        for (String url : List.of("http://login.example.com/token", "https://127.0.0.1/token", "file:///etc/passwd")) {
            Map<String, Object> c = oauthConfig();
            c.put("oauthTokenUrl", url);
            assertThatThrownBy(() -> connector.validate(c)).as(url).isInstanceOf(ConnectorValidationException.class);
        }
    }

    @Test
    void validatePrivateKeyJwtNeedsAKeyAndAWellFormedThumbprint() {
        Map<String, Object> c = oauthConfig();
        c.remove("oauthClientSecret");
        c.put("oauthClientAuthMethod", "PRIVATE_KEY_JWT");
        assertThatThrownBy(() -> connector.validate(c)).isInstanceOf(ConnectorValidationException.class);
        c.put("oauthPrivateKey", "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----");
        c.put("oauthCertThumbprint", "not-hex");
        assertThatThrownBy(() -> connector.validate(c)).hasMessageContaining("thumbprint");
        c.put("oauthCertThumbprint", "AB:CD".repeat(1) + "ab".repeat(18));
        connector.validate(c);
    }

    @Test
    void anIdpFailureSurfacesAsANeutralError() {
        HttpRestConnector failing = new HttpRestConnector(new SimpleJsonPathMapper(),
                new OutboundUrlGuard(false, h -> new InetAddress[] {InetAddress.getByName("93.184.216.34")}),
                JsonMapper.builder().build(), new ConnectorProperties(null, null, null, null, null, null),
                request -> {
                    throw new IOException("connect timed out");
                });

        assertThatThrownBy(() -> failing.execute(instance(oauthConfig()), Map.of("url", API)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("connect timed out")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("sekret"));
    }
}
