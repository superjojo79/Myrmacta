package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("notificationDispatchTask")
public class NotificationDispatchTask implements JavaDelegate {

    private final TenantProperties tenantProperties;
    private final SendNotificationUseCase sendNotificationUseCase;

    public NotificationDispatchTask(
            TenantProperties tenantProperties, SendNotificationUseCase sendNotificationUseCase) {
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.sendNotificationUseCase =
                Objects.requireNonNull(sendNotificationUseCase, "sendNotificationUseCase");
    }

    @Override
    public void execute(DelegateExecution execution) {
        List<NotificationChannel> channels = parseChannels(DeployedActivityParameters.get(execution, "notificationChannels"));
        String to = stringOrNull(DeployedActivityParameters.get(execution, "notificationTo"));
        String subject = stringOrNull(DeployedActivityParameters.get(execution, "notificationSubject"));
        String body = stringOrNull(DeployedActivityParameters.get(execution, "notificationBody"));
        String userId = stringOrNull(DeployedActivityParameters.get(execution, "notificationUserId"));
        UUID webhookConfigId = parseUuid(DeployedActivityParameters.get(execution, "notificationWebhookConfigId"));
        Map<String, Object> payload = parsePayload(execution.getVariable("notificationPayload"));

        NotificationEvent event =
                new NotificationEvent(channels, to, subject, body, userId, webhookConfigId, payload);
        sendNotificationUseCase.send(event, tenantProperties.id());
    }

    private static List<NotificationChannel> parseChannels(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("notificationChannels variable is required");
        }
        List<String> tokens = new ArrayList<>();
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null && !item.toString().isBlank()) {
                    tokens.add(item.toString().trim());
                }
            }
        } else {
            String csv = raw.toString().trim();
            if (csv.isEmpty()) {
                throw new IllegalArgumentException("notificationChannels variable is required");
            }
            for (String part : csv.split(",")) {
                if (!part.isBlank()) {
                    tokens.add(part.trim());
                }
            }
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("notificationChannels variable is required");
        }
        return tokens.stream().map(NotificationChannel::parse).toList();
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(raw.toString().trim());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parsePayload(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return copy;
        }
        throw new IllegalArgumentException("notificationPayload must be a Map");
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
