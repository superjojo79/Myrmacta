package com.flowforge.infrastructure.web.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
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
                        new ConnectorProperties.Webhook(64, 2, Duration.ofHours(24))),
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
        when(idempotencyStore.findResponseStatus(INSTANCE_ID, "idem-1")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"created\"}"))
                .andExpect(status().isAccepted());

        verify(runtime).onIncomingEvent(eq(instance), any());
        verify(idempotencyStore).store(INSTANCE_ID, "idem-1", 202);
    }

    @Test
    void receive_idempotencyHitReturnsStoredStatusWithoutDispatch() throws Exception {
        ConnectorInstanceData instance = instance(true);
        when(instances.findByIdAndTenantId(INSTANCE_ID, "tenant_dev")).thenReturn(Optional.of(instance));
        when(idempotencyStore.findResponseStatus(INSTANCE_ID, "idem-1")).thenReturn(Optional.of(202));

        mockMvc.perform(post("/api/webhooks/{instanceId}", INSTANCE_ID)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Webhook-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"created\"}"))
                .andExpect(status().isAccepted());

        verify(runtime, never()).onIncomingEvent(any(), any());
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
        when(idempotencyStore.findResponseStatus(eq(INSTANCE_ID), any())).thenReturn(Optional.empty());

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
}
