package com.flowforge.infrastructure.connector.builtin;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.PollingTriggerConnector;
import com.flowforge.infrastructure.config.ConnectorProperties;
import com.flowforge.infrastructure.config.InboundConnectorProperties;
import com.flowforge.infrastructure.connector.mapping.SimpleJsonPathMapper;
import com.flowforge.infrastructure.notification.OutboundUrlGuard;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §87 (D3c-bis, revue D-INTEG-MAIL-PROTOCOL) — connecteur TRIGGER « e-mail entrant » par <b>Microsoft Graph</b> : second accès à
 * une boîte (Exchange Online) pour le même routage que l'IMAP ({@link InboundMailRouting}) — un message crée un dossier ou alimente celui
 * dont la référence figure dans l'objet, pièces jointes contrôlées, réponses automatiques refusées, message rangé dans « traités » ou
 * « refusés » sans jamais être supprimé. Authentification : application Entra ID en OAuth2 {@code client_credentials} (D3a), seul mode
 * de Graph ; l'accès à la seule boîte voulue se restreint côté Microsoft (stratégie d'accès d'application, voir DEPLOIEMENT.md).
 */
@Component
public class GraphInboundConnector implements ConnectorDefinition, PollingTriggerConnector {

    static final String DEFAULT_BASE_URL = "https://graph.microsoft.com/v1.0";
    /** Hôtes Graph (cloud public, US Government, Chine) : le jeton ne part que vers l'un d'eux, jamais vers un hôte saisi librement. */
    static final Set<String> GRAPH_HOSTS =
            Set.of("graph.microsoft.com", "graph.microsoft.us", "dod-graph.microsoft.us", "microsoftgraph.chinacloudapi.cn");

    /** Noms de dossiers système (en anglais) refusés comme dossier de rangement. */
    static final Set<String> SYSTEM_FOLDERS = Set.of("inbox", "drafts", "deleted items", "deleteditems", "sent items", "sentitems",
            "junk email", "junkemail", "outbox", "archive");

    private final OutboundUrlGuard outboundUrlGuard;
    private final GraphMailbox mailbox;
    private final InboundConnectorProperties inbound;
    private final OAuth2TokenProvider tokenProvider;
    private final InboundMailRouting routing;

    @Autowired
    public GraphInboundConnector(
            SimpleJsonPathMapper mapper,
            OutboundUrlGuard outboundUrlGuard,
            ObjectMapper objectMapper,
            ConnectorProperties connectorProperties,
            InboundConnectorProperties inbound,
            GraphMailbox mailbox) {
        this(mapper, outboundUrlGuard, objectMapper, connectorProperties, inbound, mailbox, HttpRestConnector.urlConnectionSender(),
                Clock.systemUTC());
    }

    GraphInboundConnector(
            SimpleJsonPathMapper mapper,
            OutboundUrlGuard outboundUrlGuard,
            ObjectMapper objectMapper,
            ConnectorProperties connectorProperties,
            InboundConnectorProperties inbound,
            GraphMailbox mailbox,
            HttpRestConnector.HttpSender tokenSender,
            Clock clock) {
        this.outboundUrlGuard = Objects.requireNonNull(outboundUrlGuard, "outboundUrlGuard");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
        this.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        this.routing = new InboundMailRouting(mapper, "graph-mail-inbound");
        this.tokenProvider = new OAuth2TokenProvider(
                outboundUrlGuard, objectMapper, connectorProperties, tokenSender, clock);
    }

    @Override
    public String getType() {
        return "graph-mail-inbound";
    }

    @Override
    public String getLabel() {
        return "E-mail entrant (Microsoft Graph, interrogation)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.TRIGGER;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        List<ConfigField> fields = new ArrayList<>(List.of(
                new ConfigField("mailbox", "Mailbox", ConfigFieldType.STRING, true, null, List.of(),
                        "User principal name (shared mailbox included) or object id, e.g. dossiers@example.com. The application needs the "
                                + "Mail.ReadWrite application permission; restrict it to this mailbox with an application access policy."),
                new ConfigField("graphBaseUrl", "Graph endpoint", ConfigFieldType.URL, false, DEFAULT_BASE_URL, List.of(),
                        "Only Microsoft Graph hosts are accepted (graph.microsoft.com, graph.microsoft.us, dod-graph.microsoft.us, "
                                + "microsoftgraph.chinacloudapi.cn), https, e.g. " + DEFAULT_BASE_URL + "."),
                new ConfigField("folder", "Folder to read", ConfigFieldType.STRING, false, "Inbox", List.of(),
                        "Inbox, or the display name of a top-level folder (it must exist)."),
                new ConfigField("processedFolder", "Processed folder", ConfigFieldType.STRING, false, "FlowForge-processed", List.of(),
                        "Taken-in messages are moved here (top-level folder, created if missing), never deleted."),
                new ConfigField("rejectedFolder", "Rejected folder", ConfigFieldType.STRING, false, "FlowForge-rejected", List.of(),
                        "Automatic replies, lists and invalid messages are moved here (top-level folder, created if missing).")));
        fields.addAll(InboundMailRouting.fields());
        fields.addAll(OAuth2ConfigSupport.fields());
        return fields;
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        String mailboxName = OAuth2ConfigSupport.text(config, "mailbox");
        if (!mailboxName.matches("[A-Za-z0-9@._+-]{1,320}")) {
            throw new ConnectorValidationException("mailbox must be a user principal name or an object id");
        }
        baseUrl(config);
        for (String key : List.of("folder", "processedFolder", "rejectedFolder")) {
            Object value = config.get(key);
            if (value != null && !(value instanceof String s && s.matches("[^\\p{Cntrl}]{1,200}"))) {
                throw new ConnectorValidationException(key + " is not a valid folder name");
            }
        }
        String read = InboundMailRouting.stringOr(config.get("folder"), "Inbox");
        String processed = InboundMailRouting.stringOr(config.get("processedFolder"), "FlowForge-processed");
        String rejected = InboundMailRouting.stringOr(config.get("rejectedFolder"), "FlowForge-rejected");
        if (same(processed, read) || same(rejected, read) || same(processed, rejected)) {
            throw new ConnectorValidationException("processedFolder and rejectedFolder must differ from the folder read and from each other");
        }
        if (SYSTEM_FOLDERS.contains(processed.strip().toLowerCase(Locale.ROOT)) || SYSTEM_FOLDERS.contains(rejected.strip().toLowerCase(Locale.ROOT))) {
            throw new ConnectorValidationException("processedFolder and rejectedFolder must not be a system folder (inbox, drafts, deleted items…)");
        }
        String mode = config.get(OAuth2ConfigSupport.AUTH_MODE) == null
                ? OAuth2TokenProvider.MODE
                : String.valueOf(config.get(OAuth2ConfigSupport.AUTH_MODE));
        if (!OAuth2TokenProvider.MODE.equals(mode)) {
            throw new ConnectorValidationException("Microsoft Graph only supports authMode " + OAuth2TokenProvider.MODE);
        }
        OAuth2ConfigSupport.validate(config, outboundUrlGuard);
        OAuth2ConfigSupport.text(config, "oauthScope");
        InboundMailRouting.validate(config, inbound);
    }

    @Override
    public void poll(ConnectorInstanceData instance, EventDelivery delivery) {
        Objects.requireNonNull(instance, "instance");
        Map<String, Object> config = instance.config();
        // Endpoint re-vérifié à CHAQUE interrogation : un nom qui désigne ensuite une adresse privée est refusé avant toute requête.
        String baseUrl;
        try {
            baseUrl = baseUrl(config);
        } catch (ConnectorValidationException ex) {
            throw new IllegalStateException(ex.getMessage());
        }
        String read = InboundMailRouting.stringOr(config.get("folder"), "Inbox");
        String processed = InboundMailRouting.stringOr(config.get("processedFolder"), "FlowForge-processed");
        String rejected = InboundMailRouting.stringOr(config.get("rejectedFolder"), "FlowForge-rejected");
        if (same(processed, read) || same(rejected, read) || same(processed, rejected)) {
            throw new IllegalStateException("processedFolder and rejectedFolder must differ from the folder read and from each other");
        }
        GraphMailbox.Settings settings = new GraphMailbox.Settings(baseUrl, OAuth2ConfigSupport.text(config, "mailbox"),
                tokenProvider.accessToken(instance), read, processed, rejected, 30_000);
        ImapMailbox.Limits limits = new ImapMailbox.Limits(inbound.maxMessageBytes(), inbound.maxBodyChars(),
                inbound.maxAttachmentBytes(), InboundMailRouting.intOr(config.get("maxAttachments"), 5));
        mailbox.process(settings, inbound.maxMessagesPerPoll(), limits, routing.handler(instance, delivery));
    }

    private String baseUrl(Map<String, Object> config) {
        String url = InboundMailRouting.stringOr(config.get("graphBaseUrl"), DEFAULT_BASE_URL).strip().replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(url);
            outboundUrlGuard.assertSafe(url);
        } catch (IllegalArgumentException ex) {
            throw new ConnectorValidationException("graphBaseUrl is not an acceptable https endpoint");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || !GRAPH_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new ConnectorValidationException("graphBaseUrl must be an https Microsoft Graph endpoint");
        }
        return url;
    }

    private static boolean same(String a, String b) {
        return a.strip().equalsIgnoreCase(b.strip());
    }
}
