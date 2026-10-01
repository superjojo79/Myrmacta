package com.flowforge.infrastructure.connector.builtin;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.TriggerConnector;
import com.flowforge.domain.connector.model.WorkflowTriggerEvent;
import com.flowforge.infrastructure.config.ConnectorProperties;
import com.flowforge.infrastructure.config.InboundConnectorProperties;
import com.flowforge.infrastructure.connector.mapping.SimpleJsonPathMapper;
import com.flowforge.infrastructure.notification.OutboundUrlGuard;
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
public class ImapInboundConnector implements ConnectorDefinition, TriggerConnector {

    static final String PASSWORD_MODE = "PASSWORD";
    static final int MAX_SUBJECT_CHARS = 500;
    static final int MAX_REGEX_CHARS = 200;
    private static final Logger log = LoggerFactory.getLogger(ImapInboundConnector.class);

    private final SimpleJsonPathMapper mapper;
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
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.outboundUrlGuard = Objects.requireNonNull(outboundUrlGuard, "outboundUrlGuard");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
        this.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        this.tokenProvider = new OAuth2TokenProvider(
                outboundUrlGuard, objectMapper, connectorProperties, HttpRestConnector.urlConnectionSender(), clock);
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
                        "e.g. imap.example.com or outlook.office365.com. Must resolve to a public address."),
                new ConfigField("port", "IMAP port", ConfigFieldType.INTEGER, false, "993", List.of(), null),
                new ConfigField("security", "Transport security", ConfigFieldType.SELECT, true, "SSL", List.of("SSL", "STARTTLS"),
                        "SSL = IMAPS (993). STARTTLS is required, never optional. The server certificate is always verified."),
                new ConfigField("username", "Mailbox user", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField(OAuth2ConfigSupport.AUTH_MODE, "Authentication", ConfigFieldType.SELECT, false, PASSWORD_MODE,
                        List.of(PASSWORD_MODE, OAuth2TokenProvider.MODE),
                        "PASSWORD, or OAUTH2_CLIENT_CREDENTIALS (XOAUTH2 with an Entra ID application, scope "
                                + "https://outlook.office365.com/.default)."),
                new ConfigField("password", "Mailbox password", ConfigFieldType.PASSWORD, false, null, List.of(),
                        "Encrypted at rest. Required with PASSWORD."),
                new ConfigField("folder", "Folder to read", ConfigFieldType.STRING, false, "INBOX", List.of(), null),
                new ConfigField("processedFolder", "Processed folder", ConfigFieldType.STRING, false, "FlowForge-processed", List.of(),
                        "Taken-in messages are moved here (created if missing), never deleted."),
                new ConfigField("rejectedFolder", "Rejected folder", ConfigFieldType.STRING, false, "FlowForge-rejected", List.of(),
                        "Automatic replies, lists, oversized or invalid messages are moved here."),
                new ConfigField("pollSeconds", "Polling interval (seconds)", ConfigFieldType.INTEGER, false, "60", List.of(), null),
                new ConfigField("maxAttachments", "Attachments kept per message", ConfigFieldType.INTEGER, false, "5", List.of(),
                        "Further attachments are skipped (and counted in the process variable inboundAttachmentsRejected)."),
                new ConfigField("referencePattern", "Dossier reference pattern", ConfigFieldType.STRING, false, null, List.of(),
                        "Regular expression with ONE capture group, applied to the subject, e.g. \\[(DOS-[0-9-]+)\\]. A match adds "
                                + "the message to that dossier; otherwise (or when the dossier does not exist) a new one is created."),
                new ConfigField("processKey", "Process key", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("dossierTypeCode", "Dossier type", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("mapping", "Message mapping", ConfigFieldType.STRING, false, null, List.of(),
                        "JSON object: {\"fields\": {\"objet\": \"$.subject\", \"texte\": \"$.body\", \"expediteur\": \"$.from\"}, "
                                + "\"variables\": {...}}. Available: messageId, from, fromName, subject, body, sentAt, attachmentCount.")));
        fields.addAll(OAuth2ConfigSupport.fields());
        return fields;
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
        try {
            outboundUrlGuard.assertSafe("https://" + host);
        } catch (IllegalArgumentException ex) {
            throw new ConnectorValidationException("host is not an acceptable public address");
        }
        int port = intOr(config.get("port"), 993);
        if (port < 1 || port > 65535) {
            throw new ConnectorValidationException("port must be between 1 and 65535");
        }
        String security = OAuth2ConfigSupport.text(config, "security");
        if (!"SSL".equals(security) && !"STARTTLS".equals(security)) {
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
        if (String.valueOf(config.getOrDefault("processedFolder", "FlowForge-processed"))
                .equals(String.valueOf(config.getOrDefault("folder", "INBOX")))
                || String.valueOf(config.getOrDefault("rejectedFolder", "FlowForge-rejected"))
                        .equals(String.valueOf(config.getOrDefault("folder", "INBOX")))) {
            throw new ConnectorValidationException("processedFolder and rejectedFolder must differ from the folder read");
        }
        int poll = intOr(config.get("pollSeconds"), 60);
        if (poll < inbound.minPollSeconds()) {
            throw new ConnectorValidationException("pollSeconds must be at least " + inbound.minPollSeconds());
        }
        int maxAttachments = intOr(config.get("maxAttachments"), 5);
        if (maxAttachments < 0 || maxAttachments > 20) {
            throw new ConnectorValidationException("maxAttachments must be between 0 and 20");
        }
        referencePattern(config);
        OAuth2ConfigSupport.text(config, "processKey");
        OAuth2ConfigSupport.text(config, "dossierTypeCode");
    }

    @Override
    public List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        Map<String, Object> config = instance.config();
        boolean oauth = OAuth2ConfigSupport.isOAuth(config);
        String secret = oauth ? tokenProvider.accessToken(instance) : OAuth2ConfigSupport.text(config, "password");
        ImapMailbox.Settings settings = new ImapMailbox.Settings(
                OAuth2ConfigSupport.text(config, "host"),
                intOr(config.get("port"), 993),
                "STARTTLS".equals(config.get("security")) ? ImapMailbox.Security.STARTTLS : ImapMailbox.Security.SSL,
                OAuth2ConfigSupport.text(config, "username"),
                secret,
                oauth,
                stringOr(config.get("folder"), "INBOX"),
                stringOr(config.get("processedFolder"), "FlowForge-processed"),
                stringOr(config.get("rejectedFolder"), "FlowForge-rejected"),
                Math.toIntExact(Duration.ofSeconds(30).toMillis()));
        ImapMailbox.Limits limits = new ImapMailbox.Limits(inbound.maxMessageBytes(), inbound.maxBodyChars(),
                inbound.maxAttachmentBytes(), intOr(config.get("maxAttachments"), 5));
        Pattern reference = referencePattern(config);
        Map<String, Object> mapping = asMap(config.get("mapping"));
        Map<String, Object> fieldPaths = asMap(mapping.get("fields"));
        Map<String, Object> variablePaths = asMap(mapping.get("variables"));
        String processKey = OAuth2ConfigSupport.text(config, "processKey");
        String dossierType = OAuth2ConfigSupport.text(config, "dossierTypeCode");

        List<WorkflowTriggerEvent> events = new ArrayList<>();
        mailbox.process(settings, inbound.maxMessagesPerPoll(), limits, new ImapMailbox.Handler() {
            @Override
            public ImapMailbox.Outcome handle(ImapMailbox.Mail mail) {
                if (mail.automatic() || mail.fromAddress().isBlank()) {
                    return ImapMailbox.Outcome.REJECTED;
                }
                Map<String, Object> payload = payload(mail);
                Map<String, Object> fields = mapPaths(payload, fieldPaths);
                for (Object value : fields.values()) {
                    if (value == null || (value instanceof String text && text.isBlank())) {
                        log.warn("imap-inbound: instance {} message refused: a mapped field is empty", instance.id());
                        return ImapMailbox.Outcome.REJECTED;
                    }
                }
                Map<String, Object> variables = new LinkedHashMap<>(mapPaths(payload, variablePaths));
                variables.values().removeIf(Objects::isNull);
                variables.putIfAbsent("mailMessageId", payload.get("messageId"));
                variables.putIfAbsent("mailFrom", payload.get("from"));
                variables.putIfAbsent("mailSubject", payload.get("subject"));
                if (mail.attachmentsSkipped() > 0) {
                    variables.put("inboundAttachmentsSkipped", mail.attachmentsSkipped());
                }
                String existing = reference == null ? null : firstGroup(reference, mail.subject());
                events.add(new WorkflowTriggerEvent(processKey, dossierType, fields, variables, existing, mail.attachments(), true));
                return ImapMailbox.Outcome.PROCESSED;
            }

            @Override
            public void oversize(long bytes) {
                log.warn("imap-inbound: instance {} message of {} bytes refused (over maxMessageBytes)", instance.id(), bytes);
            }
        });
        return List.copyOf(events);
    }

    private static Map<String, Object> payload(ImapMailbox.Mail mail) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("messageId", mail.messageId());
        p.put("from", mail.fromAddress());
        p.put("fromName", mail.fromDisplay());
        p.put("subject", mail.subject());
        p.put("body", mail.body());
        p.put("sentAt", mail.sentAt() == null ? "" : mail.sentAt().toString());
        p.put("attachmentCount", mail.attachments().size());
        return p;
    }

    private static String firstGroup(Pattern pattern, String subject) {
        String bounded = subject == null ? "" : subject.length() > MAX_SUBJECT_CHARS ? subject.substring(0, MAX_SUBJECT_CHARS) : subject;
        try {
            Matcher matcher = pattern.matcher(new DeadlineCharSequence(bounded, 50));
            return matcher.find() && matcher.groupCount() >= 1 ? matcher.group(1) : null;
        } catch (IllegalStateException timeout) {
            return null; // motif trop coûteux sur cet objet : aucune corrélation, jamais un blocage de la boîte
        }
    }

    private static Pattern referencePattern(Map<String, Object> config) {
        Object raw = config.get("referencePattern");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        String text = String.valueOf(raw);
        if (text.length() > MAX_REGEX_CHARS) {
            throw new ConnectorValidationException("referencePattern is too long");
        }
        try {
            Pattern pattern = Pattern.compile(text);
            if (pattern.matcher("").groupCount() != 1) {
                throw new ConnectorValidationException("referencePattern must have exactly one capture group");
            }
            return pattern;
        } catch (PatternSyntaxException ex) {
            throw new ConnectorValidationException("referencePattern is not a valid regular expression");
        }
    }

    /** Texte dont la lecture échoue passé un délai : borne le temps d'une expression régulière écrite par un administrateur. */
    static final class DeadlineCharSequence implements CharSequence {
        private final CharSequence delegate;
        private final long deadlineNanos;

        DeadlineCharSequence(CharSequence delegate, long millis) {
            this.delegate = delegate;
            this.deadlineNanos = System.nanoTime() + millis * 1_000_000L;
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            if (System.nanoTime() > deadlineNanos) {
                throw new IllegalStateException("regular expression took too long");
            }
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineCharSequence(delegate.subSequence(start, end), Math.max(1, (deadlineNanos - System.nanoTime()) / 1_000_000L));
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    private Map<String, Object> mapPaths(Object item, Map<String, Object> paths) {
        if (paths.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            Object value = mapper.read(item, String.valueOf(entry.getValue()));
            out.put(entry.getKey(), value);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String stringOr(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static int intOr(Object value, int fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new ConnectorValidationException("not a whole number: " + value);
        }
    }
}
