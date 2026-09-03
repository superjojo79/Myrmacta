package com.flowforge.infrastructure.web.connector;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.port.in.ConnectorTriggerRuntime;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.connector.port.out.WebhookIdempotencyStore;
import com.flowforge.infrastructure.config.ConnectorProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class WebhookControllerTest {

    private static final UUID INSTANCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private ConnectorInstanceRepository instances;
    private WebhookIdempotencyStore idempotencyStore;
    private ConnectorTriggerRuntime runtime;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        instances = mock(ConnectorInstanceRepository.class);
        idempotencyStore = mock(WebhookIdempotencyStore.class);
        runtime = mock(ConnectorTriggerRuntime.class);
        var controller = new WebhookController(
                new TenantProperties("tenant_dev"),
                instances,
                idempotencyStore,
                runtime,
                new ConnectorProperties(
                        new ConnectorProperties.Crypto("unused"),
                        new ConnectorProperties.Webhook(64, 2, Duration.ofHours(24)),
                        null,
                        null),
                JsonMapper.builder().build(),
                new WebhookRequestRateLimiter(),
                List.of(new SharedSecretWebhookAuthenticator(), new HmacWebhookAuthenticator()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ConnectorExceptionHandler())
                .build();
    }

    @Test
    void receive_validSharedSecretDispatchesAndStoresAccepted() throws Exception {
        ConnectorInstanceData instance = instance(true);
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, "idem-1")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"created\"}"))
                .andExpect(status().isAccepted());

        verify(runtime).onIncomingEvent(eq(instance), any());
        verify(idempotencyStore).complete(INSTANCE_ID, "idem-1", 202);
    }

    @Test
    void receive_idempotencyHitReturnsStoredStatusWithoutDispatch() throws Exception {
        ConnectorInstanceData instance = instance(true);
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, "idem-1")).thenReturn(Optional.of(202));

        mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"created\"}"))
                .andExpect(status().isAccepted());

        verify(runtime, never()).onIncomingEvent(any(), any());
        verify(idempotencyStore, never()).complete(any(), any(), anyInt());
    }

    @Test
    void receive_missingIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receive_bodyOverLimitReturns413() throws Exception {
        mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
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
            mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                            .header("Idempotency-Key", "idem-" + i)
                            .header("X-Webhook-Token", "secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-3")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void receive_validHmacDispatchesAndStoresAccepted() throws Exception {
        ConnectorInstanceData instance = hmacInstance(true);
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, "idem-hmac")).thenReturn(Optional.empty());
        byte[] body = "{\"event\":\"created\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-hmac")
                        .header("X-Signature-256", "sha256=" + hmacHex("hmac-secret", body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(runtime).onIncomingEvent(eq(instance), any());
        verify(idempotencyStore).complete(INSTANCE_ID, "idem-hmac", 202);
    }

    @Test
    void receive_invalidHmacReturns401WithoutDispatch() throws Exception {
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev"))
                .thenReturn(Optional.of(hmacInstance(true)));

        mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-bad")
                        .header("X-Signature-256", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"created\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(runtime, never()).onIncomingEvent(any(), any());
        verify(idempotencyStore, never()).complete(any(), any(), anyInt());
    }

    @Test
    void receive_disabledInstanceReturns404() throws Exception {
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev"))
                .thenReturn(Optional.of(instance(false)));

        mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-dis")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        verify(runtime, never()).onIncomingEvent(any(), any());
    }

    @Test
    void receive_unknownInstanceReturns404() throws Exception {
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-miss")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void receive_deterministicValidationFailureReturns400_andCompletesClaim() throws Exception {
        // TODO §53 (found in adversarial security review on connecteur-callback, fixed here with
        // the same pattern): a failed attempt whose outcome is stable for the same request content
        // must be recorded as complete(400), never left stuck at IN_FLIGHT -- a stuck claim blocks
        // every retry with the same Idempotency-Key for the full TTL via
        // WebhookIdempotencyInFlightException/409, defeating the retry contract.
        ConnectorInstanceData instance = instance(true);
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, "idem-invalid")).thenReturn(Optional.empty());
        doThrow(new ConnectorValidationException("dossierTypeCode is required when existingDossierReference is absent"))
                .when(runtime)
                .onIncomingEvent(eq(instance), any());

        mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-invalid")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(idempotencyStore).complete(INSTANCE_ID, "idem-invalid", 400);
    }

    @Test
    void receive_unmappedRuntimeException_leavesClaimInFlightForFreshRetry() {
        // An exception this endpoint doesn't recognize is not assumed deterministic on the exact
        // same input -- conservatively left IN_FLIGHT rather than caching a guessed status, unlike
        // the deterministic validation failure above whose outcome is stable for the same request
        // content. Unmapped by ConnectorExceptionHandler too, so it propagates raw here (no
        // controller advice covers it) -- the point of this test is only the idempotency store
        // interaction.
        ConnectorInstanceData instance = instance(true);
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.beginOrGetCompleted(INSTANCE_ID, "idem-unexpected")).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("boom"))
                .when(runtime)
                .onIncomingEvent(eq(instance), any());

        assertThatThrownBy(() -> mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-unexpected")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        verify(idempotencyStore, never()).complete(any(), any(), anyInt());
    }

    private static ConnectorInstanceData instance(boolean enabled) {
        return new ConnectorInstanceData(
                INSTANCE_ID,
                "tenant_dev",
                "Claims",
                "http-rest",
                enabled,
                Map.of(
                        "webhookAuthType", "SHARED_SECRET_TOKEN",
                        "webhookSecret", "secret"));
    }

    private static ConnectorInstanceData hmacInstance(boolean enabled) {
        return new ConnectorInstanceData(
                INSTANCE_ID,
                "tenant_dev",
                "Claims HMAC",
                "http-rest",
                enabled,
                Map.of(
                        "webhookAuthType", "HMAC_SHA256",
                        "webhookSecret", "hmac-secret"));
    }

    private static String hmacHex(String secret, byte[] body) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
