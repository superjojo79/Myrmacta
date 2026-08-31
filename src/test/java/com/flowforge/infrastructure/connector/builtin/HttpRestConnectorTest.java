package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.ServiceConnector;
import com.flowforge.domain.connector.model.TriggerConnector;
import com.flowforge.domain.connector.model.WebhookAuthType;
import com.flowforge.domain.connector.model.WorkflowTriggerEvent;
import com.flowforge.infrastructure.config.ConnectorProperties;
import com.flowforge.infrastructure.connector.mapping.SimpleJsonPathMapper;
import com.flowforge.infrastructure.notification.OutboundUrlGuard;
import java.io.IOException;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

class HttpRestConnectorTest {

    private static final ConnectorProperties DEFAULT_PROPERTIES = new ConnectorProperties(null, null, null);

    private HttpClient httpClient;
    private HttpRestConnector connector;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        connector = new HttpRestConnector(
                new SimpleJsonPathMapper(),
                new OutboundUrlGuard(false),
                httpClient,
                JsonMapper.builder().build(),
                DEFAULT_PROPERTIES);
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
                .contains("webhookAuthType", "webhookSecret", "processKey", "dossierTypeCode");
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
    void execute_unsafeUrl_neverCallsHttpClient() throws Exception {
        assertThatThrownBy(() -> connector.execute(SERVICE_INSTANCE, Map.of("url", "https://127.0.0.1/internal")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_safeUrl_getIsDefaultMethod() throws Exception {
        stubResponse(200, "{\"ok\":true}");

        Map<String, Object> result = connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().method()).isEqualTo("GET");
        assertThat(result.get("status")).isEqualTo(200);
        assertThat(result.get("body")).isEqualTo("{\"ok\":true}");
    }

    @Test
    void execute_neverEchoesInstanceConfigInResult() throws Exception {
        stubResponse(200, "ok");

        Map<String, Object> result = connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook"));

        assertThat(result.values()).noneMatch(value -> "should-never-be-echoed-back".equals(value));
        assertThat(result).doesNotContainKey("apiSecret");
    }

    @Test
    void execute_missingUrl_throwsWithoutCallingHttpClient() throws Exception {
        assertThatThrownBy(() -> connector.execute(SERVICE_INSTANCE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_httpClientThrowsIOException_wrapsInIllegalStateException() throws Exception {
        when(httpClient.<String>send(any(), any())).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connection refused");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_httpClientInterrupted_restoresInterruptFlagAndWraps() throws Exception {
        when(httpClient.<String>send(any(), any())).thenThrow(new InterruptedException("interrupted"));

        try {
            assertThatThrownBy(() -> connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook")))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_pinsResolvedIpWithoutSecondLookup() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        OutboundUrlGuard.HostResolver resolver = host -> {
            lookups.incrementAndGet();
            return new InetAddress[] {InetAddress.getByName("93.184.216.34")};
        };
        connector = new HttpRestConnector(
                new SimpleJsonPathMapper(),
                new OutboundUrlGuard(false, resolver),
                httpClient,
                JsonMapper.builder().build(),
                DEFAULT_PROPERTIES);
        stubResponse(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of("url", "https://hooks.example.com/hook"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://93.184.216.34/hook");
        assertThat(lookups).hasValue(1);
    }

    // --- SERVICE : méthode / corps (D-HTTP-METHODS) ---

    @Test
    @SuppressWarnings("unchecked")
    void execute_methodPost_sendsPostWithBody() throws Exception {
        stubResponse(201, "created");

        connector.execute(SERVICE_INSTANCE, Map.of(
                "url", "https://example.com/hook", "method", "POST", "body", Map.of("k", "v")));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().method()).isEqualTo("POST");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_methodPut_sendsPutWithBody() throws Exception {
        stubResponse(200, "updated");

        connector.execute(SERVICE_INSTANCE, Map.of(
                "url", "https://example.com/hook", "method", "PUT", "body", Map.of("k", "v")));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().method()).isEqualTo("PUT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_methodIsCaseInsensitive() throws Exception {
        stubResponse(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook", "method", "post"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().method()).isEqualTo("POST");
    }

    @Test
    void execute_unsupportedMethod_throwsWithoutCallingHttpClient() throws Exception {
        assertThatThrownBy(() -> connector.execute(
                        SERVICE_INSTANCE, Map.of("url", "https://example.com/hook", "method", "DELETE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DELETE");

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void execute_bodyOnGet_throwsWithoutCallingHttpClient() throws Exception {
        assertThatThrownBy(() -> connector.execute(
                        SERVICE_INSTANCE, Map.of("url", "https://example.com/hook", "body", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GET");

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_bodyAsMap_serializedToJson() throws Exception {
        stubResponse(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of(
                "url", "https://example.com/hook", "method", "POST", "body", Map.of("k", "v")));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().bodyPublisher()).isPresent();
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_bodyAsString_sentVerbatim() throws Exception {
        stubResponse(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of(
                "url", "https://example.com/hook", "method", "POST", "body", "{\"raw\":true}"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().bodyPublisher()).isPresent();
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_postWithoutBody_sendsEmptyBody() throws Exception {
        stubResponse(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook", "method", "POST"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().bodyPublisher()).isPresent();
        assertThat(captor.getValue().bodyPublisher().orElseThrow().contentLength()).isZero();
    }

    // --- SERVICE : en-têtes / authHeaderValue (D-HTTP-CONFIG, D-HTTP-CONTENT-TYPE) ---

    @Test
    @SuppressWarnings("unchecked")
    void execute_authHeaderValuePresent_sendsAuthorizationHeader() throws Exception {
        stubResponse(200, "ok");
        var instance = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "http-rest-service", "http-rest", true,
                Map.of("authHeaderValue", "Bearer secret-token"));

        connector.execute(instance, Map.of("url", "https://example.com/hook"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().headers().firstValue("Authorization")).contains("Bearer secret-token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_authHeaderValueAbsent_noAuthorizationHeader() throws Exception {
        stubResponse(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().headers().firstValue("Authorization")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_authHeaderValueBlank_noAuthorizationHeader() throws Exception {
        stubResponse(200, "ok");
        var instance = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "http-rest-service", "http-rest", true,
                Map.of("authHeaderValue", "   "));

        connector.execute(instance, Map.of("url", "https://example.com/hook"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().headers().firstValue("Authorization")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_authHeaderValueNeverLeaksInResult() throws Exception {
        stubResponse(200, "ok");
        var instance = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "http-rest-service", "http-rest", true,
                Map.of("authHeaderValue", "Bearer secret-token"));

        Map<String, Object> result = connector.execute(instance, Map.of("url", "https://example.com/hook"));

        assertThat(result.values()).noneMatch(value -> "Bearer secret-token".equals(value));
        assertThat(result).doesNotContainKey("authHeaderValue");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_explicitAuthorizationHeader_overridesConfigValue() throws Exception {
        stubResponse(200, "ok");
        var instance = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "http-rest-service", "http-rest", true,
                Map.of("authHeaderValue", "Bearer config-token"));

        connector.execute(instance, Map.of(
                "url", "https://example.com/hook", "headers", Map.of("Authorization", "Bearer call-token")));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().headers().firstValue("Authorization")).contains("Bearer call-token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_headersApplied() throws Exception {
        stubResponse(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of(
                "url", "https://example.com/hook",
                "headers", Map.of("Content-Type", "application/json", "X-Custom", "value")));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().headers().firstValue("Content-Type")).contains("application/json");
        assertThat(captor.getValue().headers().firstValue("X-Custom")).contains("value");
    }

    @Test
    void execute_headersNotAMap_throwsWithoutCallingHttpClient() throws Exception {
        assertThatThrownBy(() -> connector.execute(
                        SERVICE_INSTANCE, Map.of("url", "https://example.com/hook", "headers", "not-a-map")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headers");

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_noHeadersKey_noContentTypeAddedByDefault() throws Exception {
        stubResponse(200, "ok");

        connector.execute(SERVICE_INSTANCE, Map.of("url", "https://example.com/hook", "method", "POST", "body", "x"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().headers().firstValue("Content-Type")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int status, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(httpClient.<String>send(any(), any())).thenReturn(response);
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "http", "http-rest", true, new HashMap<>(config));
    }
}
