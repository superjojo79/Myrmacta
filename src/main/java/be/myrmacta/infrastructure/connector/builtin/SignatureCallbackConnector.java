package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.CallbackHandlingConnector;
import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.WebhookAuthType;
import be.myrmacta.domain.connector.model.WebhookSecretPolicy;
import be.myrmacta.domain.signature.port.in.HandleSignatureCallbackUseCase;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.infrastructure.signature.DocusignCallbackParser;
import be.myrmacta.infrastructure.signature.DocusignSignatureAdapter;
import be.myrmacta.infrastructure.signature.YousignCallbackParser;
import be.myrmacta.infrastructure.signature.YousignSignatureAdapter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
                        List.of(
                                WebhookAuthType.HMAC_SHA256.name(),
                                WebhookAuthType.SHARED_SECRET_TOKEN.name(),
                                WebhookAuthType.YOUSIGN_HMAC.name(),
                                WebhookAuthType.DOCUSIGN_HMAC.name()),
                        "HMAC_SHA256 : X-Webhook-Timestamp + Idempotency-Key + corps (X-Signature-256). "
                                + "YOUSIGN_HMAC : corps brut (X-Yousign-Signature-256), idempotence = event_id. "
                                + "DOCUSIGN_HMAC : corps brut en Base64 (X-DocuSign-Signature-N), "
                                + "idempotence = enveloppe + événement"),
                new ConfigField(
                        "webhookSecret",
                        "Webhook secret",
                        ConfigFieldType.PASSWORD,
                        true,
                        null,
                        List.of(),
                        "Secret HMAC ou jeton partagé — au moins " + WebhookSecretPolicy.MIN_LENGTH + " caractères"),
                new ConfigField(
                        "yousignEnvironment",
                        "Yousign environment",
                        ConfigFieldType.SELECT,
                        false,
                        "sandbox",
                        List.of("sandbox", "production"),
                        "Hôte d'appel fixé : bac à sable ou production. Jamais une URL libre."),
                new ConfigField(
                        "yousignApiKey",
                        "Yousign API key",
                        ConfigFieldType.PASSWORD,
                        false,
                        null,
                        List.of(),
                        "Clé d'API du même environnement. Requis quand le fournisseur actif est Yousign."),
                new ConfigField(
                        "docusignEnvironment",
                        "DocuSign environment",
                        ConfigFieldType.SELECT,
                        false,
                        "sandbox",
                        List.of("sandbox", "production"),
                        "Hôte OAuth fixé : bac à sable ou production. Jamais une URL libre."),
                new ConfigField(
                        "docusignIntegrationKey",
                        "DocuSign integration key",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "Clé d'intégration (iss du JWT). Requis quand le fournisseur actif est DocuSign."),
                new ConfigField(
                        "docusignUserId",
                        "DocuSign user id",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "Identifiant de l'utilisateur impersonné (sub du JWT)."),
                new ConfigField(
                        "docusignPrivateKey",
                        "DocuSign RSA private key",
                        ConfigFieldType.SECRET_TEXTAREA,
                        false,
                        null,
                        List.of(),
                        "Clé privée RSA au format PEM (PKCS#8 ou PKCS#1), celle de la clé d'intégration."),
                new ConfigField(
                        "docusignAccountId",
                        "DocuSign account id",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "Compte à utiliser parmi ceux renvoyés par userinfo. Vide : le compte par défaut."));
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
        Object environment = config.get("yousignEnvironment");
        if (environment != null && !String.valueOf(environment).isBlank()
                && !Set.of("sandbox", "production").contains(String.valueOf(environment))) {
            throw new ConnectorValidationException("yousignEnvironment must be sandbox or production");
        }
        Object docusignEnvironment = config.get("docusignEnvironment");
        if (docusignEnvironment != null && !String.valueOf(docusignEnvironment).isBlank()
                && !Set.of("sandbox", "production").contains(String.valueOf(docusignEnvironment))) {
            throw new ConnectorValidationException("docusignEnvironment must be sandbox or production");
        }
        Object privateKey = config.get("docusignPrivateKey");
        if (privateKey != null && !String.valueOf(privateKey).isBlank()) {
            String pem = String.valueOf(privateKey);
            if (pem.length() > 16_000 || !pem.contains("PRIVATE KEY")) {
                throw new ConnectorValidationException("docusignPrivateKey must be a PEM RSA private key");
            }
        }
    }

    @Override
    public void handleCallback(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        if (YousignSignatureAdapter.CODE.equals(port.code())) {
            // Un événement sans issue (signer.notified, rappel) répond quand même 2xx : Yousign rejoue le reste.
            YousignCallbackParser.parse(rawPayload).ifPresent(useCase::apply);
            return;
        }
        if (DocusignSignatureAdapter.CODE.equals(port.code())) {
            DocusignCallbackParser.parse(rawPayload).ifPresent(useCase::apply);
            return;
        }
        SignaturePort.SignatureCallbackEvent event = port.parseCallback(rawPayload);
        useCase.apply(event);
    }
}
