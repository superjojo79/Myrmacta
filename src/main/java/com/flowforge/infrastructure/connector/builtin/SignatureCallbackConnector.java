package com.flowforge.infrastructure.connector.builtin;

import com.flowforge.domain.connector.model.CallbackHandlingConnector;
import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.WebhookAuthType;
import com.flowforge.domain.connector.model.WebhookSecretPolicy;
import com.flowforge.domain.signature.port.in.HandleSignatureCallbackUseCase;
import com.flowforge.domain.signature.port.out.SignaturePort;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Entrée de catalogue de la signature (TODO §72, D-SIGNATURE-ABSTRACTION) : porte la
 * <strong>configuration et les secrets</strong> du retour du fournisseur actif (HMAC ou jeton partagé,
 * chiffrés au repos comme tout champ {@code PASSWORD}) et reçoit ce retour sur la route générique
 * {@code /api/callbacks/{instanceId}} (mêmes garde-fous que {@code flowable-callback} : HMAC horodaté,
 * idempotence, corps borné, plafonds). Le delegate BPMN, lui, appelle directement
 * {@link SignaturePort} : ce connecteur ne sert ni à déclencher ni à exécuter (D-SIGNATURE-TRIGGER-VS-CONNECTOR).
 */
public final class SignatureCallbackConnector implements ConnectorDefinition, CallbackHandlingConnector {

    public static final String TYPE = "signature";

    private final SignaturePort port;
    private final HandleSignatureCallbackUseCase useCase;

    public SignatureCallbackConnector(SignaturePort port, HandleSignatureCallbackUseCase useCase) {
        this.port = Objects.requireNonNull(port, "port");
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getLabel() {
        return "Signature électronique (retour du fournisseur)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.CALLBACK;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField(
                        "webhookAuthType",
                        "Webhook auth",
                        ConfigFieldType.SELECT,
                        true,
                        WebhookAuthType.HMAC_SHA256.name(),
                        List.of(WebhookAuthType.HMAC_SHA256.name(), WebhookAuthType.SHARED_SECRET_TOKEN.name()),
                        "HMAC_SHA256 exige un en-tête X-Webhook-Timestamp (secondes epoch) et couvre timestamp + \".\" +"
                                + " Idempotency-Key + \".\" + corps (X-Signature-256), fenêtre de 5 minutes"),
                new ConfigField(
                        "webhookSecret",
                        "Webhook secret",
                        ConfigFieldType.PASSWORD,
                        true,
                        null,
                        List.of(),
                        "Secret HMAC ou jeton partagé — au moins " + WebhookSecretPolicy.MIN_LENGTH + " caractères"));
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        Object authType = config.get("webhookAuthType");
        if (authType == null) {
            throw new ConnectorValidationException("webhookAuthType is required");
        }
        String auth = String.valueOf(authType);
        if ("IP_ALLOWLIST".equals(auth)) {
            throw new ConnectorValidationException("IP_ALLOWLIST is not supported in V1");
        }
        try {
            WebhookAuthType.valueOf(auth);
        } catch (IllegalArgumentException ex) {
            throw new ConnectorValidationException("Unsupported webhookAuthType: " + auth);
        }
        Object secret = config.get("webhookSecret");
        if (!(secret instanceof String text) || text.isBlank()) {
            throw new ConnectorValidationException("webhookSecret is required");
        }
        WebhookSecretPolicy.validate(text);
    }

    @Override
    public void handleCallback(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        SignaturePort.SignatureCallbackEvent event = port.parseCallback(rawPayload);
        useCase.apply(event);
    }
}
