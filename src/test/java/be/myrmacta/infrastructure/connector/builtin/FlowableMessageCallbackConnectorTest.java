package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.CallbackResolution;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.WebhookAuthType;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableMessageCallbackConnectorTest {

    private static final String VALID_WEBHOOK_SECRET = "test-webhook-secret-0123456789";

    private FlowableMessageCallbackConnector connector;

    @BeforeEach
    void setUp() {
        connector = new FlowableMessageCallbackConnector(new SimpleJsonPathMapper());
    }

    @Test
    void metadata() {
        assertThat(connector.getType()).isEqualTo("flowable-callback");
        assertThat(connector.getCapability()).isEqualTo(ConnectorCapability.CALLBACK);
    }

    @Test
    void resolve_extractsCorrelationValueAndVariables() {
        var instance = instance(Map.of(
                "webhookAuthType", WebhookAuthType.HMAC_SHA256.name(),
                "webhookSecret", "s",
                "messageName", "callbackReceived",
                "correlationVariableName", "correlationKey",
                "correlationValuePath", "$.orderId",
                "mapping", Map.of("variables", Map.of("result", "$.status", "amount", "$.total"))));

        CallbackResolution resolution = connector.resolve(
                instance,
                Map.of("orderId", "ORD-42", "status", "approved", "total", 99));

        assertThat(resolution.messageName()).isEqualTo("callbackReceived");
        assertThat(resolution.correlationVariableName()).isEqualTo("correlationKey");
        assertThat(resolution.correlationValue()).isEqualTo("ORD-42");
        assertThat(resolution.variables())
                .containsEntry("result", "approved")
                .containsEntry("amount", 99);
    }

    @Test
    void resolve_messageNameAndCorrelationVariableName_alwaysFromConfig_neverFromPayload() {
        var instance = instance(Map.of(
                "webhookAuthType", WebhookAuthType.HMAC_SHA256.name(),
                "webhookSecret", "s",
                "messageName", "callbackReceived",
                "correlationVariableName", "correlationKey",
                "correlationValuePath", "$.orderId"));

        CallbackResolution resolution = connector.resolve(
                instance,
                Map.of("orderId", "ORD-1", "messageName", "attacker-supplied", "correlationVariableName", "x"));

        assertThat(resolution.messageName()).isEqualTo("callbackReceived");
        assertThat(resolution.correlationVariableName()).isEqualTo("correlationKey");
    }

    @Test
    void resolve_numericCorrelationValueInPayload_coercedToString() {
        // D-CALLBACK-TYPE (Phase 7) : Flowable process-variable equality is type-sensitive --
        // a JSON payload carrying the correlation id as a number must still match a String
        // process variable, so the connector normalizes to String at this boundary.
        var instance = instance(Map.of(
                "webhookAuthType", WebhookAuthType.HMAC_SHA256.name(),
                "webhookSecret", "s",
                "messageName", "callbackReceived",
                "correlationVariableName", "correlationKey",
                "correlationValuePath", "$.orderId"));

        CallbackResolution resolution = connector.resolve(instance, Map.of("orderId", 12345));

        assertThat(resolution.correlationValue()).isEqualTo("12345").isInstanceOf(String.class);
    }

    @Test
    void resolve_missingOptionalMappedVariable_omittedNotCrashed() {
        var instance = instance(Map.of(
                "webhookAuthType", WebhookAuthType.HMAC_SHA256.name(),
                "webhookSecret", "s",
                "messageName", "callbackReceived",
                "correlationVariableName", "correlationKey",
                "correlationValuePath", "$.orderId",
                "mapping", Map.of("variables", Map.of("result", "$.status"))));

        CallbackResolution resolution = connector.resolve(instance, Map.of("orderId", "ORD-1"));

        assertThat(resolution.correlationValue()).isEqualTo("ORD-1");
        assertThat(resolution.variables()).doesNotContainKey("result");
    }

    @Test
    void resolve_missingCorrelationValueInPayload_rejected() {
        var instance = instance(Map.of(
                "webhookAuthType", WebhookAuthType.HMAC_SHA256.name(),
                "webhookSecret", "s",
                "messageName", "callbackReceived",
                "correlationVariableName", "correlationKey",
                "correlationValuePath", "$.orderId"));

        assertThatThrownBy(() -> connector.resolve(instance, Map.of("other", "value")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("correlationValuePath");
    }

    @Test
    void resolve_configMissingRequiredField_rejectedByResolveItself() {
        // Defense in depth (miroir HttpRestConnector) : resolve() ne doit pas dépendre uniquement
        // de validate() pour rejeter un champ requis absent -- couvre stringValue(), pas seulement
        // requireString() côté validate().
        var instance = instance(Map.of(
                "webhookAuthType", WebhookAuthType.HMAC_SHA256.name(),
                "webhookSecret", "s",
                "correlationVariableName", "correlationKey",
                "correlationValuePath", "$.orderId"));

        assertThatThrownBy(() -> connector.resolve(instance, Map.of("orderId", "ORD-1")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("messageName");
    }

    @Test
    void resolve_blankRequiredField_rejected() {
        var instance = instance(Map.of(
                "webhookAuthType", WebhookAuthType.HMAC_SHA256.name(),
                "webhookSecret", "s",
                "messageName", "  ",
                "correlationVariableName", "correlationKey",
                "correlationValuePath", "$.orderId"));

        assertThatThrownBy(() -> connector.resolve(instance, Map.of("orderId", "ORD-1")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("messageName");
    }

    @Test
    void validate_blankRequiredField_rejected() {
        assertThatThrownBy(() -> connector.validate(Map.of(
                        "webhookAuthType", "HMAC_SHA256",
                        "webhookSecret", "  ",
                        "messageName", "m",
                        "correlationVariableName", "k",
                        "correlationValuePath", "$.x")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("webhookSecret");
    }

    @Test
    void validate_requiresAllFields() {
        assertThatThrownBy(() -> connector.validate(Map.of())).isInstanceOf(ConnectorValidationException.class);
        assertThatThrownBy(() -> connector.validate(Map.of(
                        "webhookAuthType", "HMAC_SHA256",
                        "webhookSecret", VALID_WEBHOOK_SECRET,
                        "messageName", "m")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("correlationVariableName");
        assertThatThrownBy(() -> connector.validate(Map.of(
                        "webhookAuthType", "HMAC_SHA256",
                        "webhookSecret", VALID_WEBHOOK_SECRET,
                        "messageName", "m",
                        "correlationVariableName", "k")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("correlationValuePath");
    }

    @Test
    void validate_rejectsShortWebhookSecret() {
        // D-CALLBACK-BRUTEFORCE (found in adversarial security review) : the anonymous,
        // internet-facing callback endpoint has no other layer of defense (no IP allowlist in
        // V1) -- a short secret is brute-forceable within the per-instance rate limit.
        assertThatThrownBy(() -> connector.validate(Map.of(
                        "webhookAuthType", "HMAC_SHA256",
                        "webhookSecret", "too-short",
                        "messageName", "m",
                        "correlationVariableName", "k",
                        "correlationValuePath", "$.x")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("webhookSecret")
                .hasMessageContaining("20");
    }

    @Test
    void validate_nullConfig_rejected() {
        assertThatThrownBy(() -> connector.validate(null)).isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void validate_rejectsUnsupportedAuthType() {
        assertThatThrownBy(() -> connector.validate(Map.of(
                        "webhookAuthType", "BOGUS",
                        "webhookSecret", "s",
                        "messageName", "m",
                        "correlationVariableName", "k",
                        "correlationValuePath", "$.x")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Unsupported webhookAuthType");
    }

    @Test
    void resolve_nullPayload_treatedAsEmptyObject() {
        var instance = instance(Map.of(
                "webhookAuthType", WebhookAuthType.HMAC_SHA256.name(),
                "webhookSecret", "s",
                "messageName", "callbackReceived",
                "correlationVariableName", "correlationKey",
                "correlationValuePath", "$.orderId"));

        assertThatThrownBy(() -> connector.resolve(instance, null))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("correlationValuePath");
    }

    @Test
    void validate_rejectsIpAllowlist() {
        assertThatThrownBy(() -> connector.validate(Map.of(
                        "webhookAuthType", "IP_ALLOWLIST",
                        "webhookSecret", "s",
                        "messageName", "m",
                        "correlationVariableName", "k",
                        "correlationValuePath", "$.x")))
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void validate_acceptsCompleteConfig() {
        connector.validate(Map.of(
                "webhookAuthType", "HMAC_SHA256",
                "webhookSecret", VALID_WEBHOOK_SECRET,
                "messageName", "m",
                "correlationVariableName", "k",
                "correlationValuePath", "$.x"));
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "n", "flowable-callback", true, config);
    }
}
