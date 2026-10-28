package be.myrmacta.infrastructure.web.connector;

import be.myrmacta.domain.connector.model.CallbackAmbiguousCorrelationException;
import be.myrmacta.domain.connector.model.CallbackNoMatchingInstanceException;
import be.myrmacta.domain.connector.model.CallbackRetryLaterException;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.WebhookAuthType;
import be.myrmacta.domain.connector.port.in.CallbackConnectorRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.connector.port.out.WebhookIdempotencyStore;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Parameter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Entry point for the generic callback connector (D-CALLBACK, TODO §51) : separate from
 * {@link WebhookController} because the semantics differ -- resumes an existing waiting process
 * execution, never creates or attaches a dossier. Authentication, rate limiting and idempotence
 * are the same shared components, reused by injection.
 */
@RestController
@RequestMapping("/api/callbacks")
class CallbackWebhookController {

    private static final int ACCEPTED = 202;

    private final TenantProperties tenantProperties;
    private final ConnectorInstanceRepository instances;
    private final WebhookIdempotencyStore idempotencyStore;
    private final CallbackConnectorRuntime callbackRuntime;
    private final ConnectorProperties connectorProperties;
    private final ObjectMapper objectMapper;
    private final WebhookRequestRateLimiter rateLimiter;
    private final WebhookPreAuthRateLimiter preAuthRateLimiter;
    private final List<WebhookAuthenticator> authenticators;

    CallbackWebhookController(
            TenantProperties tenantProperties,
            ConnectorInstanceRepository instances,
            WebhookIdempotencyStore idempotencyStore,
            CallbackConnectorRuntime callbackRuntime,
            ConnectorProperties connectorProperties,
            ObjectMapper objectMapper,
            WebhookRequestRateLimiter rateLimiter,
            WebhookPreAuthRateLimiter preAuthRateLimiter,
            List<WebhookAuthenticator> authenticators) {
        this.tenantProperties = tenantProperties;
        this.instances = instances;
        this.idempotencyStore = idempotencyStore;
        this.callbackRuntime = callbackRuntime;
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

        // TODO §57 revue adversariale PR #113 : voir WebhookController -- même plafond pré-auth,
        // distinct du plafond strict post-auth (Q10).
        if (!preAuthRateLimiter.tryConsume(instanceId, connectorProperties.webhook().preAuthRateLimitPerMinute())) {
            throw new WebhookRateLimitExceededException(instanceId);
        }

        Optional<ConnectorInstanceData> found = instances
                .findByIdAndTenantId(instanceId, tenantProperties.id())
                .filter(ConnectorInstanceData::enabled);
        String headerKey = headers.getFirst("Idempotency-Key");
        boolean headerMissing = headerKey == null || headerKey.isBlank();
        // Un appel sans Idempotency-Key reste un 400, y compris si l'instance est inconnue :
        // répondre 404 révélerait son existence. Yousign et DocuSign n'envoient pas cet en-tête
        // (TODO §109) ; seuls ces deux modes, sur un connecteur connu et activé, continuent sans lui.
        if (found.isEmpty()) {
            if (headerMissing) {
                throw new IllegalArgumentException("Idempotency-Key header is required");
            }
            throw new WebhookEndpointNotFoundException(instanceId);
        }
        ConnectorInstanceData instance = found.get();
        WebhookAuthType authType = authType(instance.config());
        if (headerMissing && !idempotencyFromBody(authType)) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        String secret = stringValue(instance.config().get("webhookSecret"), "webhookSecret");
        WebhookAuthenticator authenticator = authenticator(authType);
        authenticator.authenticate(instance.id(), secret, body, headers);
        String idempotencyKey = idempotencyKey(authType, authenticator, body, headerKey);

        if (!rateLimiter.tryConsume(instanceId, connectorProperties.webhook().rateLimitPerMinute())) {
            throw new WebhookRateLimitExceededException(instanceId);
        }

        return idempotencyStore.beginOrGetCompleted(instance.id(), idempotencyKey)
                .map(status -> ResponseEntity.status(status).<Void>build())
                .orElseGet(() -> {
                    Object payload;
                    try {
                        payload = readPayload(body);
                        callbackRuntime.onIncomingCallback(instance, payload);
                    } catch (RuntimeException ex) {
                        // D-CALLBACK-RACE (found in adversarial security review, post-merge) : a
                        // failed attempt must still be recorded as complete(status), never left
                        // IN_FLIGHT, or every retry with the same Idempotency-Key gets stuck on
                        // WebhookIdempotencyInFlightException/409 for the full TTL -- defeating
                        // the retry contract this endpoint's whole design relies on. Only for
                        // exceptions whose outcome is deterministic on this request's own content
                        // (so replaying the cached status on an exact-key retry is correct);
                        // anything unmapped is left IN_FLIGHT rather than guessing a status.
                        if (ex instanceof CallbackRetryLaterException) {
                            // transitoire : la clé est libérée, le rejeu avec la même clé sera traité
                            idempotencyStore.release(instance.id(), idempotencyKey);
                            throw ex;
                        }
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
        if (ex instanceof CallbackNoMatchingInstanceException) {
            return 404;
        }
        if (ex instanceof CallbackAmbiguousCorrelationException) {
            return 409;
        }
        if (ex instanceof ConnectorValidationException || ex instanceof IllegalArgumentException) {
            return 400;
        }
        return null;
    }

    /**
     * TODO §109 — Yousign et DocuSign n'envoient pas {@code Idempotency-Key}. La clé est lue dans le
     * corps déjà authentifié. Un en-tête contradictoire est refusé : il n'est pas signé.
     */
    private static String idempotencyKey(
            WebhookAuthType authType, WebhookAuthenticator authenticator, byte[] body, String headerKey) {
        if (!idempotencyFromBody(authType) || !(authenticator instanceof BodyIdempotency bodyKey)) {
            return headerKey;
        }
        String eventId = bodyKey.idempotencyKey(body);
        if (headerKey != null && !headerKey.isBlank() && !headerKey.equals(eventId)) {
            throw new IllegalArgumentException("Idempotency-Key contradicts the signed event id");
        }
        return eventId;
    }

    private static boolean idempotencyFromBody(WebhookAuthType authType) {
        return authType == WebhookAuthType.YOUSIGN_HMAC || authType == WebhookAuthType.DOCUSIGN_HMAC;
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
            throw new IllegalArgumentException("Callback body must be valid JSON", ex);
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
