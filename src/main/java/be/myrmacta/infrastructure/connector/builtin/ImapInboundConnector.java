package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.PollingTriggerConnector;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.config.InboundConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §87 (D3c) — connecteur TRIGGER « e-mail entrant » : interroge une boîte IMAP ; un message crée un dossier, ou alimente le
 * dossier dont la référence figure dans l'objet ({@code referencePattern}) ; les pièces jointes sont rattachées après contrôles
 * (types, signature du contenu, antivirus). Les réponses automatiques et les listes sont refusées (pas de boucle avec nos propres
 * envois). Un message pris en charge est rangé dans le dossier « traités », un message invalide dans « refusés » : rien n'est supprimé.
 * Authentification : mot de passe, ou OAuth2 {@code client_credentials} (D3a) en XOAUTH2 — Exchange Online n'accepte plus le mot de passe.
 */
@Component
public class ImapInboundConnector implements ConnectorDefinition, PollingTriggerConnector {

    static final String PASSWORD_MODE = "PASSWORD";
    /** TODO §128 (D-IMAP-LOCAL-LOOPBACK) — sans chiffrement : seulement vers la boucle locale en profil local ou test. */
    static final String PLAIN = "PLAIN";
    private static final Logger log = LoggerFactory.getLogger(ImapInboundConnector.class);

    private final InboundMailRouting routing;
    private final OutboundUrlGuard outboundUrlGuard;
    private final ImapMailbox mailbox;
    private final InboundConnectorProperties inbound;
    private final OAuth2TokenProvider tokenProvider;

    @Autowired
    public ImapInboundConnector(
            SimpleJsonPathMapper mapper,
            OutboundUrlGuard outboundUrlGuard,
            ObjectMapper objectMapper,
            ConnectorProperties connectorProperties,
            InboundConnectorProperties inbound,
            ImapMailbox mailbox) {
        this(mapper, outboundUrlGuard, objectMapper, connectorProperties, inbound, mailbox, Clock.systemUTC());
    }

    ImapInboundConnector(
            SimpleJsonPathMapper mapper,
            OutboundUrlGuard outboundUrlGuard,
            ObjectMapper objectMapper,
            ConnectorProperties connectorProperties,
            InboundConnectorProperties inbound,
            ImapMailbox mailbox,
            Clock clock) {
        this.routing = new InboundMailRouting(mapper, "imap-inbound");
        this.outboundUrlGuard = Objects.requireNonNull(outboundUrlGuard, "outboundUrlGuard");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
        this.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        this.tokenProvider = new OAuth2TokenProvider(
                outboundUrlGuard, objectMapper, connectorProperties, PinnedHttpClient.sender(), clock);
    }

    @Override
    public String getType() {
        return "imap-inbound";
    }

    @Override
    public String getLabel() {
        return "E-mail entrant (IMAP, interrogation)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.TRIGGER;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        List<ConfigField> fields = new ArrayList<>(List.of(
                new ConfigField("host", "IMAP host", ConfigFieldType.STRING, true, null, List.of(),
                        "e.g. imap.example.com or outlook.office365.com. Must resolve to a public address "
                                + "(localhost is accepted in the local and test profiles only)."),
                new ConfigField("port", "IMAP port", ConfigFieldType.INTEGER, false, "993", List.of(), null),
                new ConfigField("security", "Transport security", ConfigFieldType.SELECT, true, "SSL", securityOptions(),
                        "SSL = IMAPS (993). STARTTLS is required, never optional. The server certificate is always verified. "
                                + "PLAIN (no encryption) is only accepted for a localhost mailbox in the local and test profiles."),
                new ConfigField("username", "Mailbox user", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField(OAuth2ConfigSupport.AUTH_MODE, "Authentication", ConfigFieldType.SELECT, false, PASSWORD_MODE,
                        List.of(PASSWORD_MODE, OAuth2TokenProvider.MODE),
                        "PASSWORD, or OAUTH2_CLIENT_CREDENTIALS (XOAUTH2 with an Entra ID application, scope "
                                + "https://outlook.office365.com/.default)."),
                new ConfigField("password", "Mailbox password", ConfigFieldType.PASSWORD, false, null, List.of(),
                        "Encrypted at rest. Required with PASSWORD."),
                new ConfigField("folder", "Folder to read", ConfigFieldType.STRING, false, "INBOX", List.of(), null),
                new ConfigField("processedFolder", "Processed folder", ConfigFieldType.STRING, false, "Myrmacta-processed", List.of(),
                        "Taken-in messages are moved here (created if missing), never deleted."),
                new ConfigField("rejectedFolder", "Rejected folder", ConfigFieldType.STRING, false, "Myrmacta-rejected", List.of(),
                        "Automatic replies, lists, oversized or invalid messages are moved here.")));
        fields.addAll(InboundMailRouting.fields());
        fields.addAll(OAuth2ConfigSupport.fields());
        return fields;
    }

    /** {@code PLAIN} n'est proposé que là où il peut être accepté (profil local ou test). */
    private List<String> securityOptions() {
        return outboundUrlGuard.isLocalLoopback("localhost") ? List.of("SSL", "STARTTLS", PLAIN) : List.of("SSL", "STARTTLS");
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        String host = OAuth2ConfigSupport.text(config, "host");
        if (!host.matches("[A-Za-z0-9.-]{1,253}")) {
            throw new ConnectorValidationException("host must be a plain host name");
        }
        boolean localLoopback = outboundUrlGuard.isLocalLoopback(host);
        if (!localLoopback) {
            try {
                outboundUrlGuard.assertSafe("https://" + host);
            } catch (IllegalArgumentException ex) {
                throw new ConnectorValidationException("host is not an acceptable public address");
            }
        }
        int port = InboundMailRouting.intOr(config.get("port"), 993);
        if (port < 1 || port > 65535) {
            throw new ConnectorValidationException("port must be between 1 and 65535");
        }
        String security = OAuth2ConfigSupport.text(config, "security");
        if (PLAIN.equals(security) && !localLoopback) {
            throw new ConnectorValidationException("security PLAIN is only accepted for a localhost mailbox in the local and test profiles");
        }
        if (!"SSL".equals(security) && !"STARTTLS".equals(security) && !PLAIN.equals(security)) {
            throw new ConnectorValidationException("Unsupported security: " + security);
        }
        OAuth2ConfigSupport.text(config, "username");
        String mode = config.get(OAuth2ConfigSupport.AUTH_MODE) == null ? PASSWORD_MODE : String.valueOf(config.get(OAuth2ConfigSupport.AUTH_MODE));
        if (PASSWORD_MODE.equals(mode)) {
            OAuth2ConfigSupport.text(config, "password");
        } else if (OAuth2TokenProvider.MODE.equals(mode)) {
            OAuth2ConfigSupport.validate(config, outboundUrlGuard);
        } else {
            throw new ConnectorValidationException("Unsupported authMode: " + mode);
        }
        for (String key : List.of("folder", "processedFolder", "rejectedFolder")) {
            Object value = config.get(key);
            if (value != null && !(value instanceof String s && s.matches("[^\\p{Cntrl}]{1,200}"))) {
                throw new ConnectorValidationException(key + " is not a valid folder name");
            }
        }
        if (sameFolder(InboundMailRouting.stringOr(config.get("processedFolder"), "Myrmacta-processed"), InboundMailRouting.stringOr(config.get("folder"), "INBOX"))
                || sameFolder(InboundMailRouting.stringOr(config.get("rejectedFolder"), "Myrmacta-rejected"), InboundMailRouting.stringOr(config.get("folder"), "INBOX"))
                || sameFolder(InboundMailRouting.stringOr(config.get("processedFolder"), "Myrmacta-processed"), InboundMailRouting.stringOr(config.get("rejectedFolder"), "Myrmacta-rejected"))) {
            // Casse ignorée : « inbox » et « INBOX » sont le même dossier IMAP (revue D3c/D3d, M-A : sinon le message se copierait dans la boîte).
            throw new ConnectorValidationException("processedFolder and rejectedFolder must differ from the folder read and from each other");
        }
        InboundMailRouting.validate(config, inbound);
    }

    @Override
    public void poll(ConnectorInstanceData instance, EventDelivery delivery) {
        Objects.requireNonNull(instance, "instance");
        Map<String, Object> config = instance.config();
        // L'hôte est re-vérifié à CHAQUE interrogation (pas seulement à la création) : un nom qui désigne ensuite une adresse
        // privée est refusé avant toute connexion (revue D3c/D3d, M1).
        String host = OAuth2ConfigSupport.text(config, "host");
        boolean localLoopback = outboundUrlGuard.isLocalLoopback(host);
        if (!localLoopback) {
            try {
                outboundUrlGuard.assertSafe("https://" + host);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("host is not an acceptable public address");
            }
        }
        boolean plain = PLAIN.equals(config.get("security"));
        if (plain && !localLoopback) {
            throw new IllegalStateException("security PLAIN is only accepted for a localhost mailbox in the local and test profiles");
        }
        if (sameFolder(InboundMailRouting.stringOr(config.get("processedFolder"), "Myrmacta-processed"), InboundMailRouting.stringOr(config.get("folder"), "INBOX"))
                || sameFolder(InboundMailRouting.stringOr(config.get("rejectedFolder"), "Myrmacta-rejected"), InboundMailRouting.stringOr(config.get("folder"), "INBOX"))) {
            throw new IllegalStateException("processedFolder and rejectedFolder must differ from the folder read");
        }
        boolean oauth = OAuth2ConfigSupport.isOAuth(config);
        String secret = oauth ? tokenProvider.accessToken(instance) : OAuth2ConfigSupport.text(config, "password");
        ImapMailbox.Settings settings = new ImapMailbox.Settings(
                // Boucle locale : connexion à l'adresse littérale, jamais au nom (que le résolveur du poste pourrait détourner).
                localLoopback ? loopbackAddress(host) : host,
                InboundMailRouting.intOr(config.get("port"), 993),
                plain ? ImapMailbox.Security.PLAIN_FOR_TESTS
                        : "STARTTLS".equals(config.get("security")) ? ImapMailbox.Security.STARTTLS : ImapMailbox.Security.SSL,
                OAuth2ConfigSupport.text(config, "username"),
                secret,
                oauth,
                InboundMailRouting.stringOr(config.get("folder"), "INBOX"),
                InboundMailRouting.stringOr(config.get("processedFolder"), "Myrmacta-processed"),
                InboundMailRouting.stringOr(config.get("rejectedFolder"), "Myrmacta-rejected"),
                Math.toIntExact(Duration.ofSeconds(30).toMillis()));
        ImapMailbox.Limits limits = new ImapMailbox.Limits(inbound.maxMessageBytes(), inbound.maxBodyChars(),
                inbound.maxAttachmentBytes(), InboundMailRouting.intOr(config.get("maxAttachments"), 5));
        mailbox.process(settings, inbound.maxMessagesPerPoll(), limits, routing.handler(instance, delivery));
    }

    private static String loopbackAddress(String host) {
        return host.strip().equalsIgnoreCase("localhost") ? "127.0.0.1" : host.strip();
    }

    private static boolean sameFolder(String a, String b) {
        return a.strip().equalsIgnoreCase(b.strip());
    }

}
