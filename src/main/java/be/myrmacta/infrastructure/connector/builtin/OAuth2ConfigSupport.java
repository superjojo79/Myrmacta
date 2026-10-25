package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.util.List;
import java.util.Map;

/**
 * TODO §87 (D3c, D3d) — configuration OAuth2 {@code client_credentials} (D3a) partagée par les connecteurs d'entrée : mêmes noms
 * de champs que {@code http-rest} (le {@link OAuth2TokenProvider} les lit tels quels), sans liste d'hôtes autorisés — le jeton ne part
 * que vers l'hôte du connecteur lui-même.
 */
final class OAuth2ConfigSupport {

    static final String AUTH_MODE = "authMode";

    private OAuth2ConfigSupport() {}

    static List<ConfigField> fields() {
        return List.of(
                new ConfigField("oauthTokenUrl", "OAuth2 token URL", ConfigFieldType.URL, false, null, List.of(),
                        "https token endpoint, e.g. https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token. "
                                + "Used only when the authentication mode is OAUTH2_CLIENT_CREDENTIALS."),
                new ConfigField("oauthClientId", "OAuth2 client id", ConfigFieldType.STRING, false, null, List.of(),
                        "Application (client) id."),
                new ConfigField("oauthClientAuthMethod", "OAuth2 client authentication", ConfigFieldType.SELECT, false,
                        OAuth2TokenProvider.METHOD_POST,
                        List.of(OAuth2TokenProvider.METHOD_POST, OAuth2TokenProvider.METHOD_BASIC, OAuth2TokenProvider.METHOD_JWT),
                        "CLIENT_SECRET_POST / CLIENT_SECRET_BASIC use oauthClientSecret; PRIVATE_KEY_JWT uses oauthPrivateKey "
                                + "and oauthCertThumbprint."),
                new ConfigField("oauthClientSecret", "OAuth2 client secret", ConfigFieldType.PASSWORD, false, null, List.of(),
                        "Encrypted at rest."),
                new ConfigField("oauthPrivateKey", "OAuth2 certificate private key", ConfigFieldType.SECRET_TEXTAREA, false, null,
                        List.of(), "PEM PKCS#8 RSA private key. Encrypted at rest. Required for PRIVATE_KEY_JWT."),
                new ConfigField("oauthCertThumbprint", "OAuth2 certificate thumbprint", ConfigFieldType.STRING, false, null,
                        List.of(), "Hexadecimal SHA-1 (40) or SHA-256 (64) thumbprint. Required for PRIVATE_KEY_JWT."),
                new ConfigField("oauthScope", "OAuth2 scope", ConfigFieldType.STRING, false, null, List.of(),
                        "e.g. https://outlook.office365.com/.default (mailbox) or https://servicebus.azure.net/.default (Service Bus)."));
    }

    static boolean isOAuth(Map<String, Object> config) {
        return OAuth2TokenProvider.MODE.equals(String.valueOf(config.get(AUTH_MODE)));
    }

    static void validate(Map<String, Object> config, OutboundUrlGuard guard) {
        String tokenUrl = text(config, "oauthTokenUrl");
        try {
            guard.assertSafe(tokenUrl);
        } catch (IllegalArgumentException ex) {
            throw new ConnectorValidationException("oauthTokenUrl is not an acceptable https endpoint");
        }
        text(config, "oauthClientId");
        String method = config.get("oauthClientAuthMethod") == null
                ? OAuth2TokenProvider.METHOD_POST
                : String.valueOf(config.get("oauthClientAuthMethod"));
        switch (method) {
            case OAuth2TokenProvider.METHOD_POST, OAuth2TokenProvider.METHOD_BASIC -> text(config, "oauthClientSecret");
            case OAuth2TokenProvider.METHOD_JWT -> {
                text(config, "oauthPrivateKey");
                String thumbprint = text(config, "oauthCertThumbprint").replace(":", "").trim();
                if (!thumbprint.matches("(?i)[0-9a-f]{40}|[0-9a-f]{64}")) {
                    throw new ConnectorValidationException(
                            "oauthCertThumbprint must be a hexadecimal SHA-1 (40) or SHA-256 (64) thumbprint");
                }
            }
            default -> throw new ConnectorValidationException("Unsupported oauthClientAuthMethod: " + method);
        }
    }

    static String text(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConnectorValidationException(key + " is required");
        }
        return text;
    }
}
