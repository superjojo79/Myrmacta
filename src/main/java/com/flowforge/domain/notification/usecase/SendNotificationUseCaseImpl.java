package com.flowforge.domain.notification.usecase;

import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.notification.model.DossierFieldsSnapshot;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.model.NotificationPayloadJson;
import com.flowforge.domain.notification.model.NotificationValidationException;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.notification.port.out.NotificationOutboxRepository;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.notification.port.out.PendingOutboxWrite;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Validates notification events and fans out to the notification outbox.
 * Does not invoke channel ports (DD-UC-NO-IO).
 */
public final class SendNotificationUseCaseImpl implements SendNotificationUseCase {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationWebhookRepository webhookRepository;
    private final DossierTypeRepository dossierTypeRepository;

    public SendNotificationUseCaseImpl(
            NotificationOutboxRepository outboxRepository,
            NotificationWebhookRepository webhookRepository,
            DossierTypeRepository dossierTypeRepository) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.webhookRepository = Objects.requireNonNull(webhookRepository, "webhookRepository");
        this.dossierTypeRepository = Objects.requireNonNull(dossierTypeRepository, "dossierTypeRepository");
    }

    @Override
    public List<UUID> send(NotificationEvent event, String tenantId) {
        Objects.requireNonNull(event, "event");
        if (tenantId == null || tenantId.isBlank()) {
            throw new NotificationValidationException("tenantId is required");
        }

        List<NotificationChannel> channels = dedupeChannels(event.channels());
        Map<String, Object> payload = buildPayload(event, tenantId);
        Instant now = Instant.now();

        // Validate all channels before any write (atomic fan-out).
        List<PendingOutboxWrite> writes = new ArrayList<>(channels.size());
        for (NotificationChannel channel : channels) {
            validateChannel(channel, event, tenantId);
            writes.add(new PendingOutboxWrite(channel, snapshot(channel, event, payload)));
        }
        return outboxRepository.savePendingBatch(tenantId, writes, now);
    }

    /**
     * D-WEBHOOK-MASK: if the event carries a {@link DossierFieldsSnapshot}, resolve the real
     * {@link FieldDefinition}s for its {@code dossierTypeCode} and strip sensitive values before
     * this payload ever reaches an outbox row — protects every channel that reads {@code payload}
     * (webhook, browser) from a single choke point, regardless of which producer built the event.
     * When {@code dossierFieldsSnapshot} is absent (both real producers today), this is a no-op
     * and never queries {@link #dossierTypeRepository} — zero cost on the current 100% traffic
     * path.
     */
    private Map<String, Object> buildPayload(NotificationEvent event, String tenantId) {
        Map<String, Object> basePayload = event.payload() == null ? Map.of() : event.payload();
        DossierFieldsSnapshot snapshot = event.dossierFieldsSnapshot();
        if (snapshot == null) {
            return basePayload;
        }
        Map<String, Object> merged = new LinkedHashMap<>(basePayload);
        redactSensitiveFields(snapshot, tenantId).ifPresent(fields -> merged.put("dossierFields", fields));
        return Map.copyOf(merged);
    }

    /**
     * Empty result (not the raw snapshot) when {@code dossierTypeCode} can't be resolved — fail
     * safe: without real {@code FieldDefinition}s there is no way to know which values are
     * sensitive, so nothing is included rather than passing the raw map through unredacted.
     */
    private Optional<Map<String, Object>> redactSensitiveFields(DossierFieldsSnapshot snapshot, String tenantId) {
        Optional<DossierType> type = dossierTypeRepository.findByCode(tenantId, snapshot.dossierTypeCode());
        if (type.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> redacted = new LinkedHashMap<>(snapshot.fields());
        for (FieldDefinition definition : type.get().fieldDefinitions()) {
            if (definition.isSensitive()) {
                redacted.remove(definition.name());
            }
        }
        return Optional.of(Map.copyOf(redacted));
    }

    private static List<NotificationChannel> dedupeChannels(List<NotificationChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            throw new NotificationValidationException("channels is required");
        }
        Set<NotificationChannel> seen = EnumSet.noneOf(NotificationChannel.class);
        List<NotificationChannel> ordered = new ArrayList<>(channels.size());
        for (NotificationChannel channel : channels) {
            if (channel == null) {
                throw new NotificationValidationException("channels contains null");
            }
            if (seen.add(channel)) {
                ordered.add(channel);
            }
        }
        return ordered;
    }

    private void validateChannel(NotificationChannel channel, NotificationEvent event, String tenantId) {
        switch (channel) {
            case EMAIL -> {
                requireNonBlank(event.to(), "to");
                requireNonBlank(event.subject(), "subject");
                requireNonBlank(event.body(), "body");
            }
            case WEBHOOK -> {
                if (event.webhookConfigId() == null) {
                    throw new NotificationValidationException("webhookConfigId is required");
                }
                NotificationWebhookConfig config = webhookRepository
                        .findByIdAndTenantId(event.webhookConfigId(), tenantId)
                        .orElseThrow(() -> new NotificationValidationException(
                                "webhook config not found: " + event.webhookConfigId()));
                if (!config.enabled()) {
                    throw new NotificationValidationException(
                            "webhook config is not enabled: " + event.webhookConfigId());
                }
            }
            case BROWSER -> requireNonBlank(event.userId(), "userId");
        }
    }

    private static String snapshot(
            NotificationChannel channel, NotificationEvent event, Map<String, Object> payload) {
        return switch (channel) {
            case EMAIL -> NotificationPayloadJson.email(event.to(), event.subject(), event.body());
            case WEBHOOK -> NotificationPayloadJson.webhook(event.webhookConfigId(), payload);
            case BROWSER -> NotificationPayloadJson.browser(event.userId(), payload);
        };
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new NotificationValidationException(field + " is required");
        }
    }
}
