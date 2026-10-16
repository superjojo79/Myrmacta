package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.model.NotificationValidationException;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import com.flowforge.domain.workflow.portal.port.out.PortalRecipientResolverPort;
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

    private static final String RECIPIENT = "notificationTo";

    private final TenantProperties tenantProperties;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final PortalRecipientResolverPort recipientResolver;

    public NotificationDispatchTask(
            TenantProperties tenantProperties,
            SendNotificationUseCase sendNotificationUseCase,
            PortalRecipientResolverPort recipientResolver) {
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.sendNotificationUseCase =
                Objects.requireNonNull(sendNotificationUseCase, "sendNotificationUseCase");
        this.recipientResolver = Objects.requireNonNull(recipientResolver, "recipientResolver");
    }

    @Override
    public void execute(DelegateExecution execution) {
        List<NotificationChannel> channels = parseChannels(DeployedActivityParameters.get(execution, "notificationChannels"));
        String to = recipient(execution);
        String subject = stringOrNull(DeployedActivityParameters.get(execution, "notificationSubject"));
        String body = stringOrNull(DeployedActivityParameters.get(execution, "notificationBody"));
        String userId = stringOrNull(DeployedActivityParameters.get(execution, "notificationUserId"));
        UUID webhookConfigId = parseUuid(DeployedActivityParameters.get(execution, "notificationWebhookConfigId"));
        Map<String, Object> payload = parsePayload(execution.getVariable("notificationPayload"));

        NotificationEvent event =
                new NotificationEvent(channels, to, subject, body, userId, webhookConfigId, payload);
        sendNotificationUseCase.send(event, tenantProperties.id());
    }

    /**
     * TODO §93 (D-NOTIF-SOURCE-LITERAL) — {@code flowable:notificationTo="dossierField:<champ>"} lit le champ <b>à jour</b>
     * dans le dossier, {@code "variable:<nom>"} la variable de process désignée. Une source n'est résolue que si elle est écrite
     * dans l'attribut littéral du BPMN déployé : une variable {@code notificationTo} (posée au démarrage ou à la complétion
     * d'une tâche, donc par un utilisateur) reste une adresse littérale et ne peut pas désigner un autre champ. Une valeur
     * résolue absente, vide ou non textuelle fait échouer la tâche (incident) ; le message nomme la source, jamais une valeur.
     */
    private String recipient(DelegateExecution execution) {
        String literal = DeployedActivityParameters.getLiteral(execution, RECIPIENT);
        if (literal == null || !PortalRecipientSource.hasSourcePrefix(literal)) {
            return stringOrNull(DeployedActivityParameters.get(execution, RECIPIENT));
        }
        PortalRecipientSource source = PortalRecipientSource.parse(literal)
                .orElseThrow(() -> new NotificationValidationException(RECIPIENT + " source is malformed: " + literal));
        Object value = switch (source.kind()) {
            case PROCESS_VARIABLE -> execution.getVariable(source.name());
            case DOSSIER_FIELD -> recipientResolver
                    .resolve(source, dossierId(execution, literal), execution.getProcessInstanceId())
                    .orElse(null);
        };
        if (value == null) {
            throw new NotificationValidationException(RECIPIENT + " source " + literal + " resolved to no value");
        }
        if (!(value instanceof CharSequence text)) {
            throw new NotificationValidationException(RECIPIENT + " source " + literal + " is not a text value");
        }
        String address = text.toString().strip();
        if (address.isEmpty()) {
            throw new NotificationValidationException(RECIPIENT + " source " + literal + " resolved to an empty value");
        }
        return address;
    }

    private static UUID dossierId(DelegateExecution execution, String literal) {
        Object raw = execution.getVariable(WorkflowVariables.DOSSIER_ID);
        if (raw == null || raw.toString().isBlank()) {
            throw new NotificationValidationException(RECIPIENT + " source " + literal + " needs the dossierId variable");
        }
        return raw instanceof UUID uuid ? uuid : UUID.fromString(raw.toString().trim());
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
