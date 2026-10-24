package com.flowforge.infrastructure.web.connector;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.WebhookAuthType;
import com.flowforge.domain.connector.port.in.ConnectorTriggerRuntime;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.connector.port.out.WebhookIdempotencyStore;
import com.flowforge.domain.dossier.model.DossierFieldValidationException;
import com.flowforge.infrastructure.config.ConnectorProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/webhooks")
class WebhookController {

    private static final int ACCEPTED = 202;

    private final TenantProperties tenantProperties;
    private final ConnectorInstanceRepository instances;
    private final WebhookIdempotencyStore idempotencyStore;
    private final ConnectorTriggerRuntime triggerRuntime;
    private final ConnectorProperties connectorProperties;
    private final ObjectMapper objectMapper;
    private final WebhookRequestRateLimiter rateLimiter;
    private final WebhookPreAuthRateLimiter preAuthRateLimiter;
    private final List<WebhookAuthenticator> authenticators;

    WebhookController(
            TenantProperties tenantProperties,
            ConnectorInstanceRepository instances,
            WebhookIdempotencyStore idempotencyStore,
            ConnectorTriggerRuntime triggerRuntime,
            ConnectorProperties connectorProperties,
            ObjectMapper objectMapper,
            WebhookRequestRateLimiter rateLimiter,
            WebhookPreAuthRateLimiter preAuthRateLimiter,
            List<WebhookAuthenticator> authenticators) {
        this.tenantProperties = tenantProperties;
        this.instances = instances;
        this.idempotencyStore = idempotencyStore;
        this.triggerRuntime = triggerRuntime;
        this.connectorProperties = connectorProperties;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.preAuthRateLimiter = preAuthRateLimiter;
        this.authenticators = List.copyOf(authenticators);
    }

    @PostMapping("/{instanceId}")
    ResponseEntity<Void> receive(
            @PathVariable UUID instanceId,
            @Parameter(hidden = true) @RequestHeader HttpHeaders headers,
            @RequestBody byte[] rawBody) {
        byte[] body = rawBody == null ? new byte[0] : rawBody;
        if (body.length > connectorProperties.webhook().maxBodyBytes()) {
            throw new WebhookPayloadTooLargeException(connectorProperties.webhook().maxBodyBytes());
        }

        String idempotencyKey = headers.getFirst("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        // TODO §57 revue adversariale PR #113 : le plafond strict (rateLimitPerMinute) ne
        // s'applique plus qu'après authenticate() depuis PR #113 (Q10), pour qu'un flot non signé
        // ne puisse plus épuiser le budget d'un intégrateur légitime. Ce plafond pré-auth, plus
        // large et distinct, borne malgré tout le coût (parsing/HMAC, bande passante) d'un flot
        // de requêtes non authentifiées ou visant un instanceId inexistant.
        if (!preAuthRateLimiter.tryConsume(instanceId, connectorProperties.webhook().preAuthRateLimitPerMinute())) {
            throw new WebhookRateLimitExceededException(instanceId);
        }

        ConnectorInstanceData instance = instances
                .findByIdAndTenantId(instanceId, tenantProperties.id())
                .filter(ConnectorInstanceData::enabled)
                .orElseThrow(() -> new WebhookEndpointNotFoundException(instanceId));

        WebhookAuthType authType = authType(instance.config());
        String secret = stringValue(instance.config().get("webhookSecret"), "webhookSecret");
        authenticator(authType).authenticate(instance.id(), secret, body, headers);

        if (!rateLimiter.tryConsume(instanceId, connectorProperties.webhook().rateLimitPerMinute())) {
            throw new WebhookRateLimitExceededException(instanceId);
        }

        return idempotencyStore.beginOrGetCompleted(instance.id(), idempotencyKey)
                .map(status -> ResponseEntity.status(status).<Void>build())
                .orElseGet(() -> {
                    Object payload;
                    try {
                        payload = readPayload(body);
                        triggerRuntime.onIncomingEvent(instance, payload);
                    } catch (RuntimeException ex) {
                        Integer failureStatus = deterministicFailureStatus(ex);
                        if (failureStatus != null) {
                            idempotencyStore.complete(instance.id(), idempotencyKey, failureStatus);
                        }
                        throw ex;
                    }
                    idempotencyStore.complete(instance.id(), idempotencyKey, ACCEPTED);
                    return ResponseEntity.accepted().build();
                });
    }

    private static Integer deterministicFailureStatus(RuntimeException ex) {
        // TODO §102 : un dossier refusé à la validation de ses champs (dont un doublon) l'est pour ce même contenu.
        if (ex instanceof ConnectorValidationException || ex instanceof IllegalArgumentException
                || ex instanceof DossierFieldValidationException) {
            return 400;
        }
        return null;
    }

    private WebhookAuthenticator authenticator(WebhookAuthType authType) {
        return authenticators.stream()
                .filter(authenticator -> authenticator.supports(authType))
                .findFirst()
                .orElseThrow(() -> new ConnectorValidationException(
                        "Unsupported webhookAuthType: " + authType));
    }

    private Object readPayload(byte[] body) {
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Webhook body must be valid JSON", ex);
        }
    }

    private static WebhookAuthType authType(Map<String, Object> config) {
        Object value = config.get("webhookAuthType");
        if (value == null) {
            throw new ConnectorValidationException("webhookAuthType is required");
        }
        try {
            return WebhookAuthType.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            throw new ConnectorValidationException("Unsupported webhookAuthType: " + value);
        }
    }

    private static String stringValue(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConnectorValidationException(field + " is required");
        }
        return text;
    }
}
