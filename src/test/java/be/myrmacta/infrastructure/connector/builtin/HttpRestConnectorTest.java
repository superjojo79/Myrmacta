package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.ServiceConnector;
import be.myrmacta.domain.connector.model.TriggerConnector;
import be.myrmacta.domain.connector.model.WebhookAuthType;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

class HttpRestConnectorTest {

    private static final ConnectorProperties DEFAULT_PROPERTIES =
            new ConnectorProperties(null, null, null, null, null, null);

    private RecordingHttpSender httpSender;
    private HttpRestConnector connector;

    @BeforeEach
    void setUp() {
        httpSender = new RecordingHttpSender();
        // Résolution figée : le test ne doit pas dépendre du DNS du poste (example.com filtré
        // derrière certains résolveurs → « host cannot be resolved »).
        connector = new HttpRestConnector(
                new SimpleJsonPathMapper(),
                new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")}),
                JsonMapper.builder().build(),
                DEFAULT_PROPERTIES,
                httpSender);
    }

    // --- TRIGGER (régression — comportement inchangé) ---

    @Test
    void mapsObjectPayload_toSingleEvent() {
        var instance = instance(Map.of(
                "processKey", "smokeProcess",
                "dossierTypeCode", "SINISTRE",
                "webhookAuthType", WebhookAuthType.HMAC_SHA256.name(),
                "webhookSecret", "s",
                "mapping", Map.of(
                        "fields", Map.of("claimNumber", "$.claim.id"),
                        "variables", Map.of("source", "$.meta.source"))));

        List<WorkflowTriggerEvent> events = connector.onEvent(
                instance,
                Map.of(
                        "claim", Map.of("id", "C-1"),
                        "meta", Map.of("source", "partner")));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().processKey()).isEqualTo("smokeProcess");
        assertThat(events.getFirst().dossierTypeCode()).isEqualTo("SINISTRE");
        assertThat(events.getFirst().dossierFields()).containsEntry("claimNumber", "C-1");
        assertThat(events.getFirst().variables()).containsEntry("source", "partner");
    }

    @Test
    void mapsArrayPayload_toMultipleEvents() {
        var instance = instance(Map.of(
                "processKey", "smokeProcess",
                "dossierTypeCode", "SINISTRE",
                "webhookAuthType", "HMAC_SHA256",
                "webhookSecret", "s",
                "mapping", Map.of(
                        "itemsPath", "$",
                        "fields", Map.of("claimNumber", "$.id"))));

        List<WorkflowTriggerEvent> events = connector.onEvent(
                instance, List.of(Map.of("id", "1"), Map.of("id", "2")));

        assertThat(events).hasSize(2);
        assertThat(events.get(0).dossierFields()).containsEntry("claimNumber", "1");
        assertThat(events.get(1).dossierFields()).containsEntry("claimNumber", "2");
    }

    @Test
    void existingDossierReferencePath_setsReference() {
        var instance = instance(Map.of(
                "processKey", "smokeProcess",
                "dossierTypeCode", "SINISTRE",
                "webhookAuthType", "HMAC_SHA256",
                "webhookSecret", "s",
                "mapping", Map.of("existingDossierReferencePath", "$.dossierRef")));

        var events = connector.onEvent(instance, Map.of("dossierRef", "DOS-1"));
        assertThat(events.getFirst().existingDossierReference()).isEqualTo("DOS-1");
    }

    @Test
    void processKeyPath_overridesDefault() {
        var instance = instance(Map.of(
                "processKey", "defaultProcess",
                "dossierTypeCode", "SINISTRE",
                "webhookAuthType", "HMAC_SHA256",
                "webhookSecret", "s",
                "mapping", Map.of("processKeyPath", "$.processKey")));

        var events = connector.onEvent(instance, Map.of("processKey", "otherProcess"));
        assertThat(events.getFirst().processKey()).isEqualTo("otherProcess");
    }

    @Test
    void validate_rejectsIpAllowlist() {
        assertThatThrownBy(() -> connector.validate(Map.of(
                        "webhookAuthType", "IP_ALLOWLIST",
                        "webhookSecret", "s",
                        "processKey", "p",
                        "dossierTypeCode", "T")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("IP_ALLOWLIST");
    }

    // --- Capacité / enregistrement (D-CAPABILITY-SCOPE, D-HTTP-NAMING, D-HTTP-TESTS) ---

    @Test
    void implementsConnectorDefinitionAndServiceConnectorAndTriggerConnector() {
        assertThat(ConnectorDefinition.class.isAssignableFrom(HttpRestConnector.class)).isTrue();
        assertThat(ServiceConnector.class.isAssignableFrom(HttpRestConnector.class)).isTrue();
        assertThat(TriggerConnector.class.isAssignableFrom(HttpRestConnector.class)).isTrue();
    }

    @Test
    void capability_isBoth() {
        assertThat(connector.getCapability()).isEqualTo(ConnectorCapability.BOTH);
    }

    @Test
    void isASpringComponent() {
        assertThat(HttpRestConnector.class.isAnnotationPresent(Component.class)).isTrue();
    }

    @Test
    void getType_stillReturnsHttpRest() {
        assertThat(connector.getType()).isEqualTo("http-rest");
    }

    @Test
    void configSchema_stillHasTriggerFields() {
        assertThat(connector.getConfigSchema())
                .extracting(ConfigField::name)
                .contains("webhookAuthType", "webhookSecret", "processKey", "dossierTypeCode", "mapping");
    }

    @Test
    void configSchema_hasOptionalAuthHeaderValue() {
        ConfigField field = connector.getConfigSchema().stream()
                .filter(f -> f.name().equals("authHeaderValue"))
                .findFirst()
                .orElseThrow();

        assertThat(field.type()).isEqualTo(ConfigFieldType.PASSWORD);
        assertThat(field.required()).isFalse();
    }

    // --- SERVICE : SSRF / anti-fuite (régression du skeleton) ---

    private static final ConnectorInstanceData SERVICE_INSTANCE = new ConnectorInstanceData(
            UUID.randomUUID(), "tenant_dev", "http-rest-service", "http-rest", true,
            Map.of("apiSecret", "should-never-be-echoed-back"));

    @Test
    void execute_unsafeUrl_neverCallsHttpClient() {
        assertThatThrownBy(() -> connector.execute(SERVICE_INSTANCE, Map.of("url", "https://127.0.0.1/internal")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(httpSender.last).isNull();
    }

    @Test
    void execute_safeUrl_getIsDefaultMethod() throws Exception {
        httpSender.stub(200, "{\"ok\":true}");

        Map<String, Object> result = connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook"));

        assertThat(httpSender.last.method()).isEqualTo("GET");
        assertThat(result.get("status")).isEqualTo(200);
        assertThat(result.get("body")).isEqualTo("{\"ok\":true}");
    }

    @Test
    void execute_neverEchoesInstanceConfigInResult() throws Exception {
        httpSender.stub(200, "ok");

        Map<String, Object> result = connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook"));

        assertThat(result.values()).noneMatch(value -> "should-never-be-echoed-back".equals(value));
        assertThat(result).doesNotContainKey("apiSecret");
    }

    @Test
    void execute_missingUrl_throwsWithoutCallingHttpClient() {
        assertThatThrownBy(() -> connector.execute(SERVICE_INSTANCE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");

        assertThat(httpSender.last).isNull();
    }

    @Test
    void execute_httpClientThrowsIOException_wrapsInIllegalStateException() {
        httpSender.error = new IOException("connection refused");

        assertThatThrownBy(() -> connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connection refused");
    }

    @Test
    void execute_pinsResolvedIpWithoutSecondLookup() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        OutboundUrlGuard.HostResolver resolver = host -> {
            lookups.incrementAndGet();
            return new InetAddress[] {InetAddress.getByName("93.184.216.34")};
        };
        connector = new HttpRestConnector(
                new SimpleJsonPathMapper(),
                new OutboundUrlGuard(false, resolver),
                JsonMapper.builder().build(),
                DEFAULT_PROPERTIES,
                httpSender);
        httpSender.stub(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of("url", "https://hooks.example.com/hook"));

        assertThat(httpSender.last.url()).isEqualTo("https://93.184.216.34/hook");
        assertThat(httpSender.last.headers()).containsEntry("Host", "hooks.example.com");
        assertThat(lookups).hasValue(1);
    }

    @Test
    void execute_callerHostHeaderIsOverwrittenWithOriginalHostname() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        OutboundUrlGuard.HostResolver resolver = host -> {
            lookups.incrementAndGet();
            return new InetAddress[] {InetAddress.getByName("93.184.216.34")};
        };
        connector = new HttpRestConnector(
                new SimpleJsonPathMapper(),
                new OutboundUrlGuard(false, resolver),
                JsonMapper.builder().build(),
                DEFAULT_PROPERTIES,
                httpSender);
        httpSender.stub(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of(
                "url", "https://hooks.example.com/hook",
                "headers", Map.of("Host", "evil.internal")));

        assertThat(httpSender.last.headers()).containsEntry("Host", "hooks.example.com");
        assertThat(httpSender.last.headers().values()).noneMatch("evil.internal"::equals);
    }

    @Test
    void execute_methodPost_sendsPostWithBody() throws Exception {
        httpSender.stub(201, "created");

        connector.execute(SERVICE_INSTANCE, Map.of(
                "url", "https://example.com/hook", "method", "POST", "body", Map.of("k", "v")));

        assertThat(httpSender.last.method()).isEqualTo("POST");
        assertThat(new String(httpSender.last.body(), StandardCharsets.UTF_8)).contains("\"k\"");
    }

    @Test
    void execute_methodPut_sendsPutWithBody() throws Exception {
        httpSender.stub(200, "updated");

        connector.execute(SERVICE_INSTANCE, Map.of(
                "url", "https://example.com/hook", "method", "PUT", "body", Map.of("k", "v")));

        assertThat(httpSender.last.method()).isEqualTo("PUT");
    }

    @Test
    void execute_methodIsCaseInsensitive() throws Exception {
        httpSender.stub(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook", "method", "post"));

        assertThat(httpSender.last.method()).isEqualTo("POST");
    }

    @Test
    void execute_unsupportedMethod_throwsWithoutCallingHttpClient() {
        assertThatThrownBy(() -> connector.execute(
                        SERVICE_INSTANCE, Map.of("url", "https://example.com/hook", "method", "DELETE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DELETE");

        assertThat(httpSender.last).isNull();
    }

    @Test
    void execute_bodyOnGet_throwsWithoutCallingHttpClient() {
        assertThatThrownBy(() -> connector.execute(
                        SERVICE_INSTANCE, Map.of("url", "https://example.com/hook", "body", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GET");

        assertThat(httpSender.last).isNull();
    }

    @Test
    void execute_bodyAsMap_serializedToJson() throws Exception {
        httpSender.stub(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of(
                "url", "https://example.com/hook", "method", "POST", "body", Map.of("k", "v")));

        assertThat(httpSender.last.body().length).isGreaterThan(0);
    }

    @Test
    void execute_bodyAsString_sentVerbatim() throws Exception {
        httpSender.stub(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of(
                "url", "https://example.com/hook", "method", "POST", "body", "{\"raw\":true}"));

        assertThat(new String(httpSender.last.body(), StandardCharsets.UTF_8)).isEqualTo("{\"raw\":true}");
    }

    @Test
    void execute_postWithoutBody_sendsEmptyBody() throws Exception {
        httpSender.stub(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook", "method", "POST"));

        assertThat(httpSender.last.body()).isEmpty();
    }

    @Test
    void execute_authHeaderValuePresent_sendsAuthorizationHeader() throws Exception {
        httpSender.stub(200, "ok");
        var instance = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "http-rest-service", "http-rest", true,
                Map.of("authHeaderValue", "Bearer secret-token"));

        connector.execute(instance, Map.of("url", "https://example.com/hook"));

        assertThat(httpSender.last.headers()).containsEntry("Authorization", "Bearer secret-token");
    }

    @Test
    void execute_authHeaderValueAbsent_noAuthorizationHeader() throws Exception {
        httpSender.stub(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook"));

        assertThat(httpSender.last.headers()).doesNotContainKey("Authorization");
    }

    @Test
    void execute_authHeaderValueBlank_noAuthorizationHeader() throws Exception {
        httpSender.stub(200, "ok");
        var instance = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "http-rest-service", "http-rest", true,
                Map.of("authHeaderValue", "   "));

        connector.execute(instance, Map.of("url", "https://example.com/hook"));

        assertThat(httpSender.last.headers()).doesNotContainKey("Authorization");
    }

    @Test
    void execute_authHeaderValueNeverLeaksInResult() throws Exception {
        httpSender.stub(200, "ok");
        var instance = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "http-rest-service", "http-rest", true,
                Map.of("authHeaderValue", "Bearer secret-token"));

        Map<String, Object> result = connector.execute(instance, Map.of("url", "https://example.com/hook"));

        assertThat(result.values()).noneMatch(value -> "Bearer secret-token".equals(value));
        assertThat(result).doesNotContainKey("authHeaderValue");
    }

    @Test
    void execute_explicitAuthorizationHeader_overridesConfigValue() throws Exception {
        httpSender.stub(200, "ok");
        var instance = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "http-rest-service", "http-rest", true,
                Map.of("authHeaderValue", "Bearer config-token"));

        connector.execute(instance, Map.of(
                "url", "https://example.com/hook", "headers", Map.of("Authorization", "Bearer call-token")));

        assertThat(httpSender.last.headers()).containsEntry("Authorization", "Bearer call-token");
    }

    @Test
    void execute_headersApplied() throws Exception {
        httpSender.stub(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of(
                "url", "https://example.com/hook",
                "headers", Map.of("Content-Type", "application/json", "X-Custom", "value")));

        assertThat(httpSender.last.headers()).containsEntry("Content-Type", "application/json");
        assertThat(httpSender.last.headers()).containsEntry("X-Custom", "value");
    }

    @Test
    void execute_headersNotAMap_throwsWithoutCallingHttpClient() {
        assertThatThrownBy(() -> connector.execute(
                        SERVICE_INSTANCE, Map.of("url", "https://example.com/hook", "headers", "not-a-map")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headers");

        assertThat(httpSender.last).isNull();
    }

    @Test
    void execute_noHeadersKey_noContentTypeAddedByDefault() throws Exception {
        httpSender.stub(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook", "method", "POST", "body", "x"));

        assertThat(httpSender.last.headers()).doesNotContainKey("Content-Type");
    }

    @Test
    void execute_oversizedResponse_isRejected() {
        httpSender.stub(200, "x".repeat(6_000_000));

        assertThatThrownBy(() -> connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maxResponseBytes");
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "http", "http-rest", true, new HashMap<>(config));
    }

    private static final class RecordingHttpSender implements HttpRestConnector.HttpSender {
        HttpRestConnector.HttpCallRequest last;
        int status = 200;
        String responseBody = "ok";
        IOException error;

        void stub(int status, String body) {
            this.status = status;
            this.responseBody = body;
        }

        @Override
        public HttpRestConnector.HttpCallResponse send(HttpRestConnector.HttpCallRequest request) throws IOException {
            this.last = request;
            if (error != null) {
                throw error;
            }
            return new HttpRestConnector.HttpCallResponse(status, responseBody);
        }
    }
}
