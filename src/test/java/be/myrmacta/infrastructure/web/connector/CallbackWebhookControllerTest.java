package be.myrmacta.infrastructure.web.connector;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import be.myrmacta.domain.connector.model.CallbackAmbiguousCorrelationException;
import be.myrmacta.domain.connector.model.CallbackNoMatchingInstanceException;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.CallbackConnectorRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.connector.port.out.WebhookIdempotencyStore;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class CallbackWebhookControllerTest {

    private static final UUID INSTANCE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    // Les limiteurs comptent par minute calendaire : une horloge figée évite qu'un test de quota
    // chevauchant une frontière de minute voie son compteur remis à zéro (pas de 429).
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:30Z"), ZoneOffset.UTC);

    private ConnectorInstanceRepository instances;
    private WebhookIdempotencyStore idempotencyStore;
    private CallbackConnectorRuntime runtime;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        instances = mock(ConnectorInstanceRepository.class);
        idempotencyStore = mock(WebhookIdempotencyStore.class);
        runtime = mock(CallbackConnectorRuntime.class);
        var controller = new CallbackWebhookController(
                new TenantProperties("tenant_dev"),
                instances,
                idempotencyStore,
                runtime,
                new ConnectorProperties(
                        new ConnectorProperties.Crypto("unused"),
                        new ConnectorProperties.Webhook(64, 2, Duration.ofHours(24), 3),
                        null,
                        null,
                        null,
                        null),
                JsonMapper.builder().build(),
                new WebhookRequestRateLimiter(FIXED_CLOCK),
                new WebhookPreAuthRateLimiter(FIXED_CLOCK),
                List.of(new SharedSecretWebhookAuthenticator(), new HmacWebhookAuthenticator(),
                        new YousignWebhookAuthenticator(), new DocusignWebhookAuthenticator()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ConnectorExceptionHandler())
                .build();
    }

    @Test
    void receive_validSharedSecretDispatchesAndStoresAccepted() throws Exception {
        ConnectorInstanceData instance = instance(true);
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, "idem-1")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"ORD-1\"}"))
                .andExpect(status().isAccepted());

        verify(runtime).onIncomingCallback(eq(instance), any());
        verify(idempotencyStore).complete(INSTANCE_ID, "idem-1", 202);
    }

    @Test
    void receive_idempotencyHitReturnsStoredStatusWithoutDispatch() throws Exception {
        ConnectorInstanceData instance = instance(true);
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, "idem-1")).thenReturn(Optional.of(202));

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted());

        verify(runtime, never()).onIncomingCallback(any(), any());
        verify(idempotencyStore, never()).complete(any(), any(), anyInt());
    }

    @Test
    void receive_missingIdempotencyKeyReturns400() throws Exception {
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receive_missingIdempotencyKeyOnKnownConnectorReturns400() throws Exception {
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance(true)));

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(runtime, never()).onIncomingCallback(any(), any());
    }

    @Test
    void receive_bodyOverLimitReturns413() throws Exception {
        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"" + "x".repeat(80) + "\"}"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void receive_overPerInstanceRateLimitReturns429() throws Exception {
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance(true)));
        when(idempotencyStore.beginOrGetCompleted(eq(INSTANCE_ID), any())).thenReturn(Optional.empty());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                            .header("Idempotency-Key", "idem-" + i)
                            .header("X-Webhook-Token", "secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"n\":" + i + "}"))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-3")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"n\":3}"))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * TODO §57 revue adversariale PR #113 : après le passage du rate-limit strict (2/min) après
     * authenticate() (Q10, pour ne plus permettre à du trafic non signé de verrouiller
     * l'intégrateur légitime), le trafic non authentifié n'était plus du tout borné -- un
     * attaquant pouvait envoyer un volume illimité de requêtes à secret invalide (coût
     * HMAC/parsing, bande passante) sans jamais être throttlé. Le plafond pré-auth (3/min ici,
     * séparé du plafond post-auth 2/min) doit intervenir avant authenticate().
     */
    @Test
    void receive_invalidSecretFloodTripsPreAuthLimitBeforeAuthentication() throws Exception {
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance(true)));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                            .header("Idempotency-Key", "idem-bad-" + i)
                            .header("X-Webhook-Token", "wrong-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"n\":" + i + "}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-bad-3")
                        .header("X-Webhook-Token", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"n\":3}"))
                .andExpect(status().isTooManyRequests());

        verify(runtime, never()).onIncomingCallback(any(), any());
    }

    @Test
    void receive_invalidHmacReturns401WithoutDispatch() throws Exception {
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev"))
                .thenReturn(Optional.of(hmacInstance(true)));

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-bad")
                        .header("X-Signature-256", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"ORD-1\"}"))
                .andExpect(status().isUnauthorized());

        verify(runtime, never()).onIncomingCallback(any(), any());
        verify(idempotencyStore, never()).complete(any(), any(), anyInt());
    }

    @Test
    void receive_invalidHmac_logsSecurityAuditEvent() throws Exception {
        // D-CALLBACK-BRUTEFORCE (found in adversarial security review) : a failed authentication
        // attempt on this anonymous, internet-facing endpoint was previously entirely silent --
        // a sustained brute-force against webhookSecret would leave no trace. Never logs the
        // submitted signature/secret, only the connector instance.
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev"))
                .thenReturn(Optional.of(hmacInstance(true)));

        ListAppender<ILoggingEvent> appender = attachSecurityAuditAppender();
        try {
            mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                            .header("Idempotency-Key", "idem-bad-audit")
                            .header("X-Signature-256", "sha256=deadbeef")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":\"ORD-1\"}"))
                    .andExpect(status().isUnauthorized());
        } finally {
            detachSecurityAuditAppender(appender);
        }

        List<String> matches = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(msg -> msg.contains("event=webhook_authentication_failed"))
                .toList();
        org.assertj.core.api.Assertions.assertThat(matches).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(matches.get(0))
                .contains("instanceId=" + INSTANCE_ID)
                .doesNotContain("deadbeef");
    }

    @Test
    void receive_disabledInstanceReturns404() throws Exception {
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev"))
                .thenReturn(Optional.of(instance(false)));

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-dis")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        verify(runtime, never()).onIncomingCallback(any(), any());
    }

    @Test
    void receive_noMatchingProcessInstanceReturns404() throws Exception {
        // D-CALLBACK-RACE relies on the external caller retrying: the failed attempt must be
        // recorded as complete(404), never left stuck at IN_FLIGHT (found in adversarial security
        // review post-merge -- a stuck claim blocks every retry with the same Idempotency-Key for
        // the full TTL via WebhookIdempotencyInFlightException/409, defeating the retry contract).
        ConnectorInstanceData instance = instance(true);
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, "idem-race")).thenReturn(Optional.empty());
        doThrow(new CallbackNoMatchingInstanceException("m", "k", "v"))
                .when(runtime)
                .onIncomingCallback(eq(instance), any());

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-race")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"ORD-1\"}"))
                .andExpect(status().isNotFound());

        verify(idempotencyStore).complete(INSTANCE_ID, "idem-race", 404);
    }

    @Test
    void receive_ambiguousCorrelationReturns409() throws Exception {
        ConnectorInstanceData instance = instance(true);
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, "idem-ambiguous")).thenReturn(Optional.empty());
        doThrow(new CallbackAmbiguousCorrelationException("m", "k", "v", 2))
                .when(runtime)
                .onIncomingCallback(eq(instance), any());

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-ambiguous")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"ORD-1\"}"))
                .andExpect(status().isConflict());

        verify(idempotencyStore).complete(INSTANCE_ID, "idem-ambiguous", 409);
    }

    @Test
    void receive_ambiguousCorrelation_logsSecurityAuditEvent_withoutLoggingCorrelationValue() throws Exception {
        // Found in adversarial security review: an ambiguous correlation is always a real BPMN
        // modeling defect and was previously entirely silent server-side. correlationValue ("v")
        // is caller-supplied business data and must never appear in the log line.
        ConnectorInstanceData instance = instance(true);
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, "idem-audit")).thenReturn(Optional.empty());
        doThrow(new CallbackAmbiguousCorrelationException(
                        "callbackReceived", "correlationKey", "super-secret-order-id-999", 2))
                .when(runtime)
                .onIncomingCallback(eq(instance), any());

        ListAppender<ILoggingEvent> appender = attachSecurityAuditAppender();
        try {
            mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                            .header("Idempotency-Key", "idem-audit")
                            .header("X-Webhook-Token", "secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":\"ORD-1\"}"))
                    .andExpect(status().isConflict());
        } finally {
            detachSecurityAuditAppender(appender);
        }

        List<String> matches = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(msg -> msg.contains("event=callback_ambiguous_correlation"))
                .toList();
        org.assertj.core.api.Assertions.assertThat(matches).hasSize(1);
        String message = matches.get(0);
        org.assertj.core.api.Assertions.assertThat(message)
                .contains("messageName=callbackReceived")
                .contains("correlationKey=correlationKey")
                .contains("matchCount=2")
                .doesNotContain("super-secret-order-id-999");
    }

    private static ListAppender<ILoggingEvent> attachSecurityAuditAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachSecurityAuditAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void receive_unmappedRuntimeException_leavesClaimInFlightForFreshRetry() {
        // An exception this endpoint doesn't recognize is not assumed deterministic on the exact
        // same input -- conservatively left IN_FLIGHT rather than caching a guessed status, unlike
        // the two domain exceptions above whose outcome is stable for the same request content.
        // Unmapped by ConnectorExceptionHandler too, so it propagates raw here (no controller
        // advice covers it) -- the point of this test is only the idempotency store interaction.
        ConnectorInstanceData instance = instance(true);
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, "idem-unexpected")).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("boom"))
                .when(runtime)
                .onIncomingCallback(eq(instance), any());

        assertThatThrownBy(() -> mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-unexpected")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"ORD-1\"}")))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        verify(idempotencyStore, never()).complete(any(), any(), anyInt());
    }

    @Test
    void receive_yousignUsesSignedEventIdWithoutIdempotencyHeader() throws Exception {
        String eventId = "b6c63685-c556-4a30-8fe9-b6f2b187d936";
        String body = "{\"event_id\":\"" + eventId + "\"}";
        ConnectorInstanceData instance = yousignInstance();
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, eventId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("X-Yousign-Signature-256", "sha256=" + bodyHmac("secret", body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(runtime).onIncomingCallback(eq(instance), any());
        verify(idempotencyStore).complete(INSTANCE_ID, eventId, 202);
    }

    @Test
    void receive_yousignRejectsIdempotencyHeaderThatContradictsEventId() throws Exception {
        String eventId = "b6c63685-c556-4a30-8fe9-b6f2b187d936";
        String body = "{\"event_id\":\"" + eventId + "\"}";
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(yousignInstance()));

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("X-Yousign-Signature-256", "sha256=" + bodyHmac("secret", body))
                        .header("Idempotency-Key", "forged-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(idempotencyStore, never()).beginOrGetCompleted(any(), any());
    }

    @Test
    void receive_docusignUsesEnvelopeAndEventWithoutIdempotencyHeader() throws Exception {
        String envelope = "0ef334d2-7f36-4643-8a2a-4283b4a3e4a4";
        String body = "{\"event\":\"envelope-completed\",\"retryCount\":1,\"data\":{\"envelopeId\":\"" + envelope + "\"}}";
        String key = envelope + ":envelope-completed";
        ConnectorInstanceData instance = docusignInstance();
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, key)).thenReturn(Optional.empty());

        docusignMvc().perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("X-DocuSign-Signature-1", "not-this-key")
                        .header("X-DocuSign-Signature-2", bodyHmacBase64("secret", body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(runtime).onIncomingCallback(eq(instance), any());
        verify(idempotencyStore).complete(INSTANCE_ID, key, 202);
    }

    @Test
    void receive_docusignRejectsABadSignatureAndAContradictoryIdempotencyKey() throws Exception {
        String envelope = "0ef334d2-7f36-4643-8a2a-4283b4a3e4a4";
        String body = "{\"event\":\"envelope-completed\",\"data\":{\"envelopeId\":\"" + envelope + "\"}}";
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(docusignInstance()));

        docusignMvc().perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("X-DocuSign-Signature-1", bodyHmacBase64("other-secret", body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        docusignMvc().perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("X-DocuSign-Signature-1", bodyHmacBase64("secret", body))
                        .header("Idempotency-Key", "forged-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(idempotencyStore, never()).beginOrGetCompleted(any(), any());
    }

    private MockMvc docusignMvc() {
        var controller = new CallbackWebhookController(
                new TenantProperties("tenant_dev"),
                instances,
                idempotencyStore,
                runtime,
                new ConnectorProperties(
                        new ConnectorProperties.Crypto("unused"),
                        new ConnectorProperties.Webhook(512, 2, Duration.ofHours(24), 3),
                        null,
                        null,
                        null,
                        null),
                JsonMapper.builder().build(),
                new WebhookRequestRateLimiter(FIXED_CLOCK),
                new WebhookPreAuthRateLimiter(FIXED_CLOCK),
                List.of(new DocusignWebhookAuthenticator()));
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ConnectorExceptionHandler())
                .build();
    }

    private static String bodyHmacBase64(String secret, String body) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.Base64.getEncoder().encodeToString(
                    mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ConnectorInstanceData docusignInstance() {
        return new ConnectorInstanceData(
                INSTANCE_ID,
                "tenant_dev",
                "Signature",
                "signature",
                true,
                Map.of("webhookAuthType", "DOCUSIGN_HMAC", "webhookSecret", "secret"));
    }

    private static String bodyHmac(String secret, String body) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ConnectorInstanceData yousignInstance() {
        return new ConnectorInstanceData(
                INSTANCE_ID,
                "tenant_dev",
                "Signature",
                "signature",
                true,
                Map.of("webhookAuthType", "YOUSIGN_HMAC", "webhookSecret", "secret"));
    }

    private static ConnectorInstanceData instance(boolean enabled) {
        return new ConnectorInstanceData(
                INSTANCE_ID,
                "tenant_dev",
                "Callback",
                "flowable-callback",
                enabled,
                Map.of(
                        "webhookAuthType", "SHARED_SECRET_TOKEN",
                        "webhookSecret", "secret"));
    }

    private static ConnectorInstanceData hmacInstance(boolean enabled) {
        return new ConnectorInstanceData(
                INSTANCE_ID,
                "tenant_dev",
                "Callback HMAC",
                "flowable-callback",
                enabled,
                Map.of(
                        "webhookAuthType", "HMAC_SHA256",
                        "webhookSecret", "hmac-secret"));
    }
}
