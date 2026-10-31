package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.PollingTriggerConnector;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.infrastructure.config.InboundConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO §87 (D3c, D3d-bis) — règles communes aux connecteurs « e-mail entrant » (IMAP, Microsoft Graph) : champs de routage,
 * validation, corrélation par référence dans l'objet, attestation de l'expéditeur, anti-boucle (réponses automatiques refusées),
 * mapping vers le dossier. Un connecteur ne fournit que l'accès à la boîte ; ce qu'il en fait est identique quel que soit le protocole.
 */
final class InboundMailRouting {

    static final int MAX_SUBJECT_CHARS = 500;
    static final int MAX_REGEX_CHARS = 200;
    /** TODO §113 — variables que seule la plateforme pose (un mapping de même nom est ignoré). */
    static final Set<String> PLATFORM_VARIABLES = Set.of("mailMessageId", "mailFrom", "mailSubject", "mailInReplyTo",
            "mailReferences", "mailConversationId", "mailSenderAuthenticated", "mailReferenceIgnored");
    private static final Logger log = LoggerFactory.getLogger(InboundMailRouting.class);

    private final SimpleJsonPathMapper mapper;
    private final String connectorType;

    InboundMailRouting(SimpleJsonPathMapper mapper, String connectorType) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.connectorType = Objects.requireNonNull(connectorType, "connectorType");
    }

    /** Champs de configuration de routage, à la suite de ceux de l'accès à la boîte. */
    static List<ConfigField> fields() {
        return List.of(
                new ConfigField("pollSeconds", "Polling interval (seconds)", ConfigFieldType.INTEGER, false, "60", List.of(), null),
                new ConfigField("maxAttachments", "Attachments kept per message", ConfigFieldType.INTEGER, false, "5", List.of(),
                        "Further attachments are skipped (and counted in the process variable inboundAttachmentsSkipped)."),
                new ConfigField("requireAuthenticatedSender", "Require an authenticated sender to join an existing dossier",
                        ConfigFieldType.BOOLEAN, false, "true", List.of(),
                        "true (default): a reference in the subject only adds the message to that dossier when the FIRST Authentication-Results "
                                + "header, written by YOUR receiving server (trustedAuthservId), attests the sender: dmarc=pass, or dkim=pass / spf=pass "
                                + "aligned with the From domain. Otherwise a new dossier is created (the sender address alone can be forged). Set false "
                                + "only if you accept that anyone who guesses a reference can feed that dossier."),
                new ConfigField("trustedAuthservId", "Authentication-Results server id", ConfigFieldType.STRING, false, null, List.of(),
                        "The authserv-id your receiving server writes first in Authentication-Results (e.g. mx.example.com, or "
                                + "'spf.protection.outlook.com' style identifier shown in a received message's headers). Required to join existing dossiers "
                                + "while requireAuthenticatedSender is true."),
                new ConfigField("referencePattern", "Dossier reference pattern", ConfigFieldType.STRING, false, null, List.of(),
                        "Regular expression with ONE capture group, applied to the subject, e.g. \\[(DOS-[0-9-]+)\\]. A match adds "
                                + "the message to that dossier; otherwise (or when the dossier does not exist) a new one is created."),
                new ConfigField("processKey", "Process key", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("dossierTypeCode", "Dossier type", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("mapping", "Message mapping", ConfigFieldType.STRING, false, null, List.of(),
                        "JSON object: {\"fields\": {\"objet\": \"$.subject\", \"texte\": \"$.body\", \"expediteur\": \"$.from\"}, "
                                + "\"variables\": {...}}. Available: messageId, from, fromName, subject, body, sentAt, attachmentCount, inReplyTo, references, conversationId."));
    }

    static void validate(Map<String, Object> config, InboundConnectorProperties inbound) {
        if (referencePattern(config) != null && !"false".equals(String.valueOf(config.get("requireAuthenticatedSender")))
                && (config.get("trustedAuthservId") == null || String.valueOf(config.get("trustedAuthservId")).isBlank())) {
            throw new ConnectorValidationException("referencePattern needs trustedAuthservId (the server id your mail server writes first in "
                    + "Authentication-Results), or requireAuthenticatedSender=false to accept that the sender address can be forged");
        }
        int poll = intOr(config.get("pollSeconds"), 60);
        if (poll < inbound.minPollSeconds()) {
            throw new ConnectorValidationException("pollSeconds must be at least " + inbound.minPollSeconds());
        }
        int maxAttachments = intOr(config.get("maxAttachments"), 5);
        if (maxAttachments < 0 || maxAttachments > 20) {
            throw new ConnectorValidationException("maxAttachments must be between 0 and 20");
        }
        OAuth2ConfigSupport.text(config, "processKey");
        OAuth2ConfigSupport.text(config, "dossierTypeCode");
    }

    /** Gestionnaire d'un message de la boîte : le remet au processus avant que la boîte ne le range. */
    ImapMailbox.Handler handler(ConnectorInstanceData instance, PollingTriggerConnector.EventDelivery delivery) {
        Map<String, Object> config = instance.config();
        Pattern reference = referencePattern(config);
        Map<String, Object> mapping = asMap(config.get("mapping"));
        Map<String, Object> fieldPaths = asMap(mapping.get("fields"));
        Map<String, Object> variablePaths = asMap(mapping.get("variables"));
        String processKey = OAuth2ConfigSupport.text(config, "processKey");
        String dossierType = OAuth2ConfigSupport.text(config, "dossierTypeCode");
        boolean requireAuthenticated = !"false".equals(String.valueOf(config.get("requireAuthenticatedSender")));
        String trustedAuthservId = config.get("trustedAuthservId") == null ? null : String.valueOf(config.get("trustedAuthservId"));
        return new ImapMailbox.Handler() {
            @Override
            public ImapMailbox.Outcome handle(ImapMailbox.Mail mail) {
                if (mail.automatic() || mail.fromAddress().isBlank()) {
                    return ImapMailbox.Outcome.REJECTED;
                }
                Map<String, Object> payload = payload(mail);
                Map<String, Object> fields = mapPaths(payload, fieldPaths);
                for (Object value : fields.values()) {
                    if (value == null || (value instanceof String text && text.isBlank())) {
                        log.warn("{}: instance {} message refused: a mapped field is empty", connectorType, instance.id());
                        return ImapMailbox.Outcome.REJECTED;
                    }
                }
                Map<String, Object> variables = new LinkedHashMap<>(mapPaths(payload, variablePaths));
                variables.values().removeIf(Objects::isNull);
                // TODO §113 — variables posées par la plateforme : un mapping de connecteur ne les remplace pas (le rattachement à un
                // fil les relit).
                variables.keySet().removeAll(PLATFORM_VARIABLES);
                variables.put("mailMessageId", payload.get("messageId"));
                variables.put("mailFrom", payload.get("from"));
                variables.put("mailSubject", payload.get("subject"));
                putThread(variables, mail.thread());
                boolean authenticated = AuthenticationResults.attests(mail.authenticationResults(), trustedAuthservId, mail.fromAddress());
                variables.put("mailSenderAuthenticated", authenticated);
                if (mail.attachmentsSkipped() > 0) {
                    variables.put("inboundAttachmentsSkipped", mail.attachmentsSkipped());
                }
                String existing = reference == null ? null : firstGroup(reference, mail.subject());
                if (existing != null && requireAuthenticated && !authenticated) {
                    // L'expéditeur est usurpable : sans attestation de NOTRE serveur de réception, le message ne s'ajoute pas à un
                    // dossier existant (il en crée un nouveau, signalé au processus) — revue D3c/D3d, H3.
                    variables.put("mailReferenceIgnored", existing);
                    existing = null;
                }
                try {
                    delivery.deliver(new WorkflowTriggerEvent(processKey, dossierType, fields, variables, existing, mail.attachments(), true,
                            new WorkflowTriggerEvent.InboundMailIdentity(mail.messageId(), mail.thread().conversationId(),
                                    mail.fromAddress())));
                } catch (RuntimeException failure) {
                    // Le message n'est PAS rangé comme traité : il va dans « refusés », visible et rejouable à la main.
                    log.warn("{}: instance {} could not take a message in charge ({}): moved to the rejected folder",
                            connectorType, instance.id(), failure.getClass().getSimpleName());
                    return ImapMailbox.Outcome.REJECTED;
                }
                return ImapMailbox.Outcome.PROCESSED;
            }

            @Override
            public void unreadable(String reason) {
                log.warn("{}: instance {} message unreadable ({}): moved to the rejected folder", connectorType, instance.id(), reason);
            }

            @Override
            public void oversize(long bytes) {
                log.warn("{}: instance {} message of {} bytes refused (over maxMessageBytes)", connectorType, instance.id(), bytes);
            }
        };
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
        p.put("inReplyTo", mail.thread().inReplyTo());
        p.put("references", String.join(" ", mail.thread().references()));
        p.put("conversationId", mail.thread().conversationId());
        return p;
    }

    /** En-têtes de fil remis au process, seulement s'ils existent (TODO §113, D-MAILROOM-THREAD). */
    private static void putThread(Map<String, Object> variables, MailThread thread) {
        if (!thread.inReplyTo().isEmpty()) {
            variables.put("mailInReplyTo", thread.inReplyTo());
        }
        if (!thread.references().isEmpty()) {
            variables.put("mailReferences", String.join(" ", thread.references()));
        }
        if (!thread.conversationId().isEmpty()) {
            variables.put("mailConversationId", thread.conversationId());
        }
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

    static Pattern referencePattern(Map<String, Object> config) {
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
    static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    static String stringOr(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    static int intOr(Object value, int fallback) {
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
