package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TODO §155 (lot 2) — type de connecteur {@code ebox-outbound} : configuration du compte expéditeur ; aucun envoi par le connecteur (revue H1). */
class EboxOutboundConnectorTest {

    private static final String TENANT = "tenant_dev";
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-0000000000e5");
    private static final UUID DOSSIER = UUID.fromString("00000000-0000-0000-0000-00000000d055");
    private final OutboundUrlGuard guard =
            new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName("193.191.208.10")});
    private final EboxOutboundConnector connector = new EboxOutboundConnector(guard);
    private final ConnectorInstanceData instance =
            new ConnectorInstanceData(INSTANCE, TENANT, "eBox envoi", "ebox-outbound", true, Map.of());

    private static Map<String, Object> config() {
        Map<String, Object> config = new HashMap<>();
        config.put("publicationUrl", "https://services.socialsecurity.be/REST/ebox/enterprise/messageRegistry/v3");
        config.put("federationUrl", "https://services.socialsecurity.be/REST/ebox/enterprise/federation/v1");
        config.put("oauthTokenUrl", "https://services.socialsecurity.be/REST/oauth/v5/token");
        config.put("oauthClientId", "myrmacta-commune");
        config.put("oauthPrivateKey", "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n");
        config.put("oauthCertThumbprint", "a".repeat(40));
        return config;
    }

    private static Map<String, Object> input() {
        Map<String, Object> input = new HashMap<>();
        input.put("recipientType", "ENTREPRISE");
        input.put("recipient", "0212.345.678");
        input.put("documentRef", "tenant_dev/doc-1");
        input.put("subject", "Votre permis");
        input.put("senderApplicationId", "myrmacta-commune");
        input.put("messageTypeId", "decision");
        input.put("language", "nl");
        input.put("registered", false);
        input.put("fallbackChannel", "PAPER");
        return input;
    }

    @Test
    void describesAServiceTypeWithTheSenderAccountSettings() {
        assertThat(connector.getType()).isEqualTo("ebox-outbound");
        assertThat(connector.getCapability()).isEqualTo(ConnectorCapability.SERVICE);
        assertThat(connector.getConfigSchema()).extracting(ConfigField::name).containsExactly("publicationUrl", "federationUrl",
                "registeredMailUrl", "onBehalfOf", "allowedHostSuffixes", "oauthTokenUrl", "oauthClientId", "oauthPrivateKey",
                "oauthCertThumbprint", "oauthScope", "registeredMailScope");
        assertThat(connector.getConfigSchema()).filteredOn(f -> f.name().equals("oauthPrivateKey"))
                .extracting(ConfigField::type).containsExactly(ConfigFieldType.SECRET_TEXTAREA);
    }

    @Test
    void aCompleteConfigurationIsAccepted() {
        connector.validate(config());
        Map<String, Object> withRegistered = config();
        withRegistered.put("registeredMailUrl", "https://services.socialsecurity.be/REST/registeredMail/v1");
        withRegistered.put("onBehalfOf", "0207.654.321");
        connector.validate(withRegistered);
    }

    @Test
    void anIncompleteOrUnsafeConfigurationIsRefused() {
        assertThatThrownBy(() -> connector.validate(null)).isInstanceOf(ConnectorValidationException.class);
        for (String key : List.of("publicationUrl", "federationUrl", "oauthTokenUrl", "oauthClientId", "oauthPrivateKey",
                "oauthCertThumbprint")) {
            Map<String, Object> config = config();
            config.remove(key);
            assertThatThrownBy(() -> connector.validate(config)).as(key).isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining(key);
        }
        Map<String, Object> foreign = config();
        foreign.put("publicationUrl", "https://ebox.attacker.example/REST");
        assertThatThrownBy(() -> connector.validate(foreign)).isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("publicationUrl");
        Map<String, Object> widened = config();
        widened.put("allowedHostSuffixes", "attacker.example");
        assertThatThrownBy(() -> connector.validate(widened)).isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("allowedHostSuffixes");
        Map<String, Object> secret = config();
        secret.put("oauthClientAuthMethod", "client_secret_post");
        assertThatThrownBy(() -> connector.validate(secret)).isInstanceOf(ConnectorValidationException.class);
        Map<String, Object> mandate = config();
        mandate.put("onBehalfOf", "12");
        assertThatThrownBy(() -> connector.validate(mandate)).isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("onBehalfOf");
    }

    /**
     * Revue H1 : une étape « Appeler un connecteur » avec une requête littérale ({@code processWithBuiltRequest}) ne doit pas
     * pouvoir envoyer ; seule la brique « Envoyer en eBox » envoie, sans passer par ce connecteur.
     */
    @Test
    void theConnectorRefusesEveryCallEvenFromAProcessStepWithALiteralRequest() {
        for (ConnectorCallContext context : List.of(ConnectorCallContext.direct(), ConnectorCallContext.process(DOSSIER),
                ConnectorCallContext.processWithBuiltRequest(DOSSIER), ConnectorCallContext.processWithBuiltRequest(null))) {
            assertThatThrownBy(() -> connector.execute(instance, input(), context)).as(context.toString())
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Envoyer en eBox");
        }
        assertThatThrownBy(() -> connector.execute(instance, input())).isInstanceOf(IllegalArgumentException.class);
    }
}
