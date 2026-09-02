package com.flowforge.infrastructure.web.connector;

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

import com.flowforge.domain.connector.model.CallbackAmbiguousCorrelationException;
import com.flowforge.domain.connector.model.CallbackNoMatchingInstanceException;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.in.CallbackConnectorRuntime;
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

class CallbackWebhookControllerTest {

    private static final UUID INSTANCE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

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
        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
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
                            .content("{}"))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/callbacks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-3")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests());
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

        verify(idempotencyStore, never()).complete(any(), any(), anyInt());
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

        verify(idempotencyStore, never()).complete(any(), any(), anyInt());
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
