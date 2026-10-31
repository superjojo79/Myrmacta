package be.myrmacta.domain.notification.usecase;

import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.notification.model.DossierFieldsSnapshot;
import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationEvent;
import be.myrmacta.domain.notification.model.NotificationPayloadJson;
import be.myrmacta.domain.notification.model.NotificationValidationException;
import be.myrmacta.domain.notification.model.NotificationWebhookConfig;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.notification.port.out.NotificationOutboxRepository;
import be.myrmacta.domain.notification.port.out.NotificationWebhookRepository;
import be.myrmacta.domain.notification.port.out.PendingOutboxWrite;
import be.myrmacta.domain.notification.port.out.UserNotificationRepository;
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
    private final UserNotificationRepository userNotificationRepository;

    public SendNotificationUseCaseImpl(
            NotificationOutboxRepository outboxRepository,
            NotificationWebhookRepository webhookRepository,
            DossierTypeRepository dossierTypeRepository,
            UserNotificationRepository userNotificationRepository) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.webhookRepository = Objects.requireNonNull(webhookRepository, "webhookRepository");
        this.dossierTypeRepository = Objects.requireNonNull(dossierTypeRepository, "dossierTypeRepository");
        this.userNotificationRepository =
                Objects.requireNonNull(userNotificationRepository, "userNotificationRepository");
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
        // D-NOTIF-SSE-ID: generated up front so the BROWSER outbox snapshot can carry the same id
        // that will identify the user_notification row — the relay uses it as the SSE event id,
        // letting the client mark that exact inbox row read instead of the outbox row's own id
        // (which the inbox never shares and the client can never resolve to a 200 on PATCH).
        UUID inboxId = channels.contains(NotificationChannel.BROWSER) ? UUID.randomUUID() : null;
        if (event.attachment() != null && !channels.contains(NotificationChannel.EMAIL)) {
            // D-NOTIF-ATTACH-EMAIL-ONLY : jamais ignorée en silence.
            throw new NotificationValidationException("attachment requires the EMAIL channel");
        }

        // Validate all channels before any write (atomic fan-out).
        List<PendingOutboxWrite> writes = new ArrayList<>(channels.size());
        for (NotificationChannel channel : channels) {
            validateChannel(channel, event, tenantId);
            writes.add(new PendingOutboxWrite(channel, snapshot(channel, event, payload, inboxId)));
        }
        List<UUID> outboxIds = outboxRepository.savePendingBatch(tenantId, writes, now);
        if (channels.contains(NotificationChannel.BROWSER)) {
            // D-NOTIF-PERSISTENCE: mirrors the BROWSER fan-out into the user-facing history,
            // independent of notification_outbox's own retry/retention lifecycle. Written after
            // the outbox batch, not in the same transaction (no shared transaction manager
            // reachable from this pure domain class) — a failure here never orphans an outbox
            // row that would otherwise still deliver via SSE/email/webhook.
            userNotificationRepository.save(
                    inboxId, tenantId, event.userId(), NotificationPayloadJson.plain(payload), now);
        }
        return outboxIds;
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
            NotificationChannel channel, NotificationEvent event, Map<String, Object> payload, UUID inboxId) {
        return switch (channel) {
            case EMAIL -> NotificationPayloadJson.email(event.to(), event.subject(), event.body(), event.attachment(),
                    event.originDossierId());
            case WEBHOOK -> NotificationPayloadJson.webhook(event.webhookConfigId(), payload);
            case BROWSER -> NotificationPayloadJson.browser(event.userId(), payload, inboxId);
        };
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new NotificationValidationException(field + " is required");
        }
    }
}
