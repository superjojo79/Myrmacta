package com.flowforge.infrastructure.notification;

import com.flowforge.domain.connector.port.out.ConnectorSecretCipher;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationOutboxRecord;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.model.WebhookDeliveryCommand;
import com.flowforge.domain.notification.port.out.BrowserPushPort;
import com.flowforge.domain.notification.port.out.EmailPort;
import com.flowforge.domain.notification.port.out.NotificationOutboxRepository;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.notification.port.out.WebhookPort;
import com.flowforge.infrastructure.config.NotificationEmailProperties;
import com.flowforge.infrastructure.config.NotificationProperties;
import com.flowforge.infrastructure.observability.MdcScope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationOutboxRelay {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationWebhookRepository webhookRepository;
    private final EmailPort emailPort;
    private final WebhookPort webhookPort;
    private final BrowserPushPort browserPushPort;
    private final ConnectorSecretCipher cipher;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final DocumentStoragePort documentStorage;
    private final NotificationEmailProperties emailProperties;

    public NotificationOutboxRelay(
            NotificationOutboxRepository outboxRepository,
            NotificationWebhookRepository webhookRepository,
            EmailPort emailPort,
            WebhookPort webhookPort,
            BrowserPushPort browserPushPort,
            ConnectorSecretCipher cipher,
            NotificationProperties properties,
            ObjectMapper objectMapper,
            DocumentStoragePort documentStorage,
            NotificationEmailProperties emailProperties) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.webhookRepository = Objects.requireNonNull(webhookRepository, "webhookRepository");
        this.emailPort = Objects.requireNonNull(emailPort, "emailPort");
        this.webhookPort = Objects.requireNonNull(webhookPort, "webhookPort");
        this.browserPushPort = Objects.requireNonNull(browserPushPort, "browserPushPort");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.documentStorage = Objects.requireNonNull(documentStorage, "documentStorage");
        this.emailProperties = Objects.requireNonNull(emailProperties, "emailProperties");
    }

    @Scheduled(fixedDelayString = "${app.notification.outbox.poll-delay:1s}")
    void relayPendingEvents() {
        relayPending(Instant.now());
    }

    void relayPending(Instant now) {
        List<NotificationOutboxRecord> batch =
                outboxRepository.lockNextPending(properties.outbox().batchSize(), now);
        for (NotificationOutboxRecord record : batch) {
            // Revue D5 : bail renouvelé juste avant l'envoi — le lot entier ne doit pas dépendre d'un bail posé à son début.
            // Une ligne reprise entre-temps par un autre exécutant (bail échu) est sautée.
            // (revue D5 passe 2) instant FRAIS : avec `now` capturé au début du lot le bail renouvelé serait identique au bail initial.
            Instant fresh = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            if (!outboxRepository.renewClaim(record.id(), record.nextAttemptAt(), fresh.isAfter(now) ? fresh : now)) {
                continue;
            }
            processOne(record, now);
        }
        Instant cutoff = now.minus(properties.outbox().retention());
        outboxRepository.purgeTerminalOlderThan(cutoff);
    }

    void processOne(NotificationOutboxRecord record, Instant now) {
        try (MdcScope ignored = MdcScope.tenant(record.tenantId()).dossier(dossierIdFromPayload(record.payloadJson()))) {
            processOneInternal(record, now);
        }
    }

    private void processOneInternal(NotificationOutboxRecord record, Instant now) {
        try {
            switch (record.channel()) {
                case EMAIL -> dispatchEmail(record, now);
                case WEBHOOK -> dispatchWebhook(record, now);
                case BROWSER -> dispatchBrowser(record, now);
            }
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            outboxRepository.recordFailure(
                    record.id(), message, properties.outbox().maxAttempts(), now);
        }
    }

    private void dispatchEmail(NotificationOutboxRecord record, Instant now) {
        JsonNode root = readTree(record.payloadJson());
        String to = textRequired(root, "to");
        String subject = textRequired(root, "subject");
        String body = textRequired(root, "body");
        JsonNode attachment = root.get("attachment");
        if (attachment == null || attachment.isNull()) {
            emailPort.send(to, subject, body);
        } else {
            emailPort.send(to, subject, body, attachmentContent(record.tenantId(), attachment));
        }
        outboxRepository.markProcessed(record.id(), now);
    }

    /**
     * Issue #183 (D-NOTIF-ATTACH-REF-IN-OUTBOX) — contenu relu dans le stockage du tenant de la ligne ; le plafond est revérifié
     * (il a pu baisser depuis l'émission). Document absent ou trop gros : échec ordinaire, l'outbox retente puis abandonne.
     */
    private EmailPort.EmailAttachmentContent attachmentContent(String tenantId, JsonNode attachment) {
        byte[] content;
        try {
            content = documentStorage.retrieve(tenantId, textRequired(attachment, "storageRef"));
        } catch (RuntimeException unreadable) {
            // Revue adversariale I1 : le message de l'adaptateur cite la référence de stockage ; lastError ne la garde pas.
            throw new IllegalStateException("attachment content could not be read");
        }
        if (content == null || content.length > emailProperties.attachmentMaxBytes()) {
            throw new IllegalStateException(
                    "attachment exceeds " + emailProperties.attachmentMaxBytes() + " bytes");
        }
        return new EmailPort.EmailAttachmentContent(
                textRequired(attachment, "fileName"), textRequired(attachment, "contentType"), content);
    }

    private void dispatchWebhook(NotificationOutboxRecord record, Instant now) {
        JsonNode root = readTree(record.payloadJson());
        UUID webhookConfigId = UUID.fromString(textRequired(root, "webhookConfigId"));
        Map<String, Object> payload = readPayloadMap(root.get("payload"));

        Optional<NotificationWebhookConfig> found =
                webhookRepository.findByIdAndTenantId(webhookConfigId, record.tenantId());
        if (found.isEmpty() || !found.get().enabled() || found.get().deletedAt() != null) {
            outboxRepository.markDead(record.id(), "webhook config missing, disabled, or deleted", now);
            return;
        }

        NotificationWebhookConfig config = found.get();
        String secretPlain = null;
        if (StringUtils.hasText(config.secretCiphertext())) {
            secretPlain = cipher.decrypt(config.tenantId(), config.secretCiphertext());
        }

        try {
            webhookPort.send(new WebhookDeliveryCommand(
                    config.targetUrl(), secretPlain, record.id(), now, payload));
            outboxRepository.markProcessed(record.id(), now);
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            outboxRepository.recordFailure(
                    record.id(), message, properties.outbox().maxAttempts(), now);
        }
    }

    private void dispatchBrowser(NotificationOutboxRecord record, Instant now) {
        JsonNode root = readTree(record.payloadJson());
        String userId = textRequired(root, "userId");
        // D-NOTIF-SSE-ID: the user_notification row id, not this outbox row's own id — lets the
        // client PATCH /api/notifications/{id}/read against the exact history row it just saw.
        String inboxId = textRequired(root, "inboxId");
        Map<String, Object> payload = readPayloadMap(root.get("payload"));

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", inboxId);
        event.put("payload", payload);
        event.put("at", now.toString());

        browserPushPort.push(userId, event);
        // A2: delivered or skipped → PROCESSED (not DEAD)
        outboxRepository.markProcessed(record.id(), now);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Invalid outbox payload JSON", ex);
        }
    }

    private Map<String, Object> readPayloadMap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, MAP_TYPE);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid payload object", ex);
        }
    }

    private static String textRequired(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull() || !value.isValueNode() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required in outbox payload");
        }
        return value.asText();
    }

    private static String dossierIdFromPayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        int key = json.indexOf("\"dossierId\"");
        if (key < 0) {
            return null;
        }
        int colon = json.indexOf(':', key);
        int quote = json.indexOf('"', colon + 1);
        int end = json.indexOf('"', quote + 1);
        if (quote < 0 || end < 0) {
            return null;
        }
        String value = json.substring(quote + 1, end);
        return value.isBlank() ? null : value;
    }
}
