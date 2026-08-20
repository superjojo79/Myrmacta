package com.flowforge.infrastructure.notification;

import com.flowforge.domain.connector.port.out.ConnectorSecretCipher;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationOutboxRecord;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.model.WebhookDeliveryCommand;
import com.flowforge.domain.notification.port.out.BrowserPushPort;
import com.flowforge.domain.notification.port.out.EmailPort;
import com.flowforge.domain.notification.port.out.NotificationOutboxRepository;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.notification.port.out.WebhookPort;
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

    public NotificationOutboxRelay(
            NotificationOutboxRepository outboxRepository,
            NotificationWebhookRepository webhookRepository,
            EmailPort emailPort,
            WebhookPort webhookPort,
            BrowserPushPort browserPushPort,
            ConnectorSecretCipher cipher,
            NotificationProperties properties,
            ObjectMapper objectMapper) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.webhookRepository = Objects.requireNonNull(webhookRepository, "webhookRepository");
        this.emailPort = Objects.requireNonNull(emailPort, "emailPort");
        this.webhookPort = Objects.requireNonNull(webhookPort, "webhookPort");
        this.browserPushPort = Objects.requireNonNull(browserPushPort, "browserPushPort");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Scheduled(fixedDelayString = "${app.notification.outbox.poll-delay:1s}")
    void relayPendingEvents() {
        relayPending(Instant.now());
    }

    void relayPending(Instant now) {
        List<NotificationOutboxRecord> batch =
                outboxRepository.lockNextPending(properties.outbox().batchSize(), now);
        for (NotificationOutboxRecord record : batch) {
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
        emailPort.send(to, subject, body);
        outboxRepository.markProcessed(record.id(), now);
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
        Map<String, Object> payload = readPayloadMap(root.get("payload"));

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", record.id().toString());
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
