package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.ServiceConnector;
import be.myrmacta.domain.ebox.port.in.SendEboxMessageUseCase;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * TODO §155 (D-EBOX-SEND-CHANNELS, lot 2) — type de connecteur {@value #TYPE} : le compte expéditeur eBox d'un tenant (URL de
 * publication, de la fédération et du recommandé, mandat, client OAuth v5 et clé du certificat, chiffrée au repos). La
 * configuration est celle que lit {@link EboxOutboundSettings} à l'envoi, contrôlée ici à l'enregistrement par les mêmes règles.
 *
 * <p>Ce connecteur n'envoie rien lui-même (revue H1, D-EBOX-SEND-BLOCK) : tout appel est refusé, qu'il vienne de l'API
 * d'administration, de l'API M2M ou d'une étape « Appeler un connecteur », même avec une requête littérale du BPMN. Seule la
 * brique « Envoyer en eBox » ({@code eboxSendTask}) envoie, en appelant {@link SendEboxMessageUseCase} avec cette instance comme
 * compte expéditeur. Au déploiement, la source de référence {@code connectorInstances} n'offre pas ce type : une étape
 * {@code connectorServiceTask} qui le vise est refusée ; la brique le choisit par la source {@code eboxSenders}.
 *
 * <p>La voie citoyen n'a pas encore de réglage par instance : l'API de l'eBox citoyen n'est pas connue (stub,
 * D-EBOX-SEND-CITIZEN-STUB) ; ses champs s'ajouteront avec le vrai adaptateur.
 */
@Component
public class EboxOutboundConnector implements ConnectorDefinition, ServiceConnector {

    static final String TYPE = EboxOutboundSettings.TYPE;
    static final String REFUSED = TYPE + ": this connector never sends by itself; use the « Envoyer en eBox » process step";

    private final OutboundUrlGuard guard;

    public EboxOutboundConnector(OutboundUrlGuard guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getLabel() {
        return "eBox (envoi entreprise et citoyen)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.SERVICE;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField("publicationUrl", "Registre de messages (publication)", ConfigFieldType.URL, true, null, List.of(),
                        "e-Box REST 3.8, https, ex. https://services.socialsecurity.be/REST/ebox/enterprise/messageRegistry/v3"),
                new ConfigField("federationUrl", "e-Box Federation", ConfigFieldType.URL, true, null, List.of(),
                        "Préférence eBox des entreprises (Federation 1.4), https, ex. "
                                + "https://services.socialsecurity.be/REST/ebox/enterprise/federation/v1"),
                new ConfigField("registeredMailUrl", "Registered Mail (recommandé)", ConfigFieldType.URL, false, null, List.of(),
                        "Registered Mail 1.1, https. Vide : un envoi recommandé est refusé"),
                new ConfigField("onBehalfOf", "Mandat (BCE représenté)", ConfigFieldType.STRING, false, null, List.of(),
                        "Numéro BCE de la commune quand Myrmacta publie pour elle en prestataire technique (mandat Mahis) ; "
                                + "vide sinon"),
                new ConfigField("allowedHostSuffixes", "Domaines autorisés", ConfigFieldType.STRING, false,
                        EboxOutboundSettings.DEFAULT_SUFFIXES, List.of(),
                        "Le jeton n'est envoyé qu'aux hôtes qui finissent par l'un de ces domaines, pris parmi "
                                + "socialsecurity.be, fgov.be et belgium.be"),
                new ConfigField("oauthTokenUrl", "OAuth v5 token URL", ConfigFieldType.URL, true, null, List.of(),
                        "ex. https://services.socialsecurity.be/REST/oauth/v5/token"),
                new ConfigField("oauthClientId", "OAuth v5 client id", ConfigFieldType.STRING, true, null, List.of(),
                        "Client obtenu à l'onboarding Document Sender, 200 caractères au plus"),
                new ConfigField("oauthPrivateKey", "Clé privée du certificat", ConfigFieldType.SECRET_TEXTAREA, true, null, List.of(),
                        "PEM PKCS#8 RSA. Chiffrée au repos"),
                new ConfigField("oauthCertThumbprint", "Empreinte du certificat", ConfigFieldType.STRING, true, null, List.of(),
                        "SHA-1 (40) ou SHA-256 (64) en hexadécimal"),
                new ConfigField("oauthScope", "OAuth v5 scope (publication)", ConfigFieldType.STRING, false,
                        EboxOutboundSettings.DEFAULT_SCOPE, List.of(), null),
                new ConfigField("registeredMailScope", "OAuth v5 scope (recommandé)", ConfigFieldType.STRING, false,
                        EboxOutboundSettings.DEFAULT_REGISTERED_MAIL_SCOPE, List.of(), null));
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        try {
            EboxOutboundSettings.validate(config, guard);
        } catch (IllegalStateException invalid) {
            throw new ConnectorValidationException(invalid.getMessage());
        }
        Map<String, Object> oauth = new HashMap<>(config);
        oauth.put("oauthClientAuthMethod", OAuth2TokenProvider.METHOD_JWT);
        OAuth2ConfigSupport.validate(oauth, guard);
    }

    /**
     * Revue H1 : refuse tout appel, y compris d'une étape de process dont la requête est construite par le BPMN déployé. Seule
     * la brique « Envoyer en eBox » envoie, en appelant {@link SendEboxMessageUseCase} elle-même.
     */
    @Override
    public Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> input) {
        throw new IllegalArgumentException(REFUSED);
    }

    @Override
    public Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> input, ConnectorCallContext context) {
        throw new IllegalArgumentException(REFUSED);
    }
}
