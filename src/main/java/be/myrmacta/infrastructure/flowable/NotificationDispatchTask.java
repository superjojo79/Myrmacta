package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.notification.model.EmailAttachment;
import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationEvent;
import be.myrmacta.domain.notification.model.NotificationValidationException;
import be.myrmacta.domain.notification.port.in.ResolveEmailAttachmentUseCase;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.portal.model.PortalRecipientSource;
import be.myrmacta.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("notificationDispatchTask")
public class NotificationDispatchTask implements JavaDelegate {

    private static final String RECIPIENT = "notificationTo";
    private static final String ATTACHMENT = "notificationAttachment";
    private static final String ATTACHMENT_NAME = "notificationAttachmentName";
    /** Une seule boîte {@code local@domaine} : ni espace ni séparateur de liste, ni forme « Nom <adresse> ». */
    private static final Pattern SINGLE_MAILBOX = Pattern.compile("[^\\s@,;:<>()\\[\\]\\\\\"]+@[^\\s@,;:<>()\\[\\]\\\\\"]+");

    private final TenantProperties tenantProperties;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final PortalRecipientResolverPort recipientResolver;
    private final ResolveEmailAttachmentUseCase attachmentResolver;

    public NotificationDispatchTask(
            TenantProperties tenantProperties,
            SendNotificationUseCase sendNotificationUseCase,
            PortalRecipientResolverPort recipientResolver,
            ResolveEmailAttachmentUseCase attachmentResolver) {
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.sendNotificationUseCase =
                Objects.requireNonNull(sendNotificationUseCase, "sendNotificationUseCase");
        this.recipientResolver = Objects.requireNonNull(recipientResolver, "recipientResolver");
        this.attachmentResolver = Objects.requireNonNull(attachmentResolver, "attachmentResolver");
    }

    @Override
    public void execute(DelegateExecution execution) {
        // Revue adversariale #183 (M1, L5) : avec une pièce jointe, canaux et destinataire ne viennent que du BPMN, et le canal
        // EMAIL est vérifié avant toute lecture du document.
        boolean withAttachment = DeployedActivityParameters.getLiteral(execution, ATTACHMENT) != null;
        List<NotificationChannel> channels = withAttachment
                ? attachmentChannels(execution)
                : parseChannels(DeployedActivityParameters.get(execution, "notificationChannels"));
        String to = recipient(execution, withAttachment);
        String subject = stringOrNull(DeployedActivityParameters.get(execution, "notificationSubject"));
        String body = stringOrNull(DeployedActivityParameters.get(execution, "notificationBody"));
        String userId = stringOrNull(DeployedActivityParameters.get(execution, "notificationUserId"));
        UUID webhookConfigId = parseUuid(DeployedActivityParameters.get(execution, "notificationWebhookConfigId"));
        Map<String, Object> payload = parsePayload(execution.getVariable("notificationPayload"));

        NotificationEvent event = new NotificationEvent(channels, to, subject, body, userId, webhookConfigId, payload)
                .withAttachment(attachment(execution))
                .withOriginDossier(originDossier(execution));
        sendNotificationUseCase.send(event, tenantProperties.id());
    }

    /**
     * Issue #183 (D-NOTIF-ATTACH-LITERAL) — {@code flowable:notificationAttachment="variable:<nom>|dossierField:<nom>"} et
     * {@code flowable:notificationAttachmentName} ne sont lus que dans le BPMN déployé : une variable de même nom, posée par un
     * utilisateur, est ignorée. La valeur résolue est une référence de stockage, vérifiée par {@link ResolveEmailAttachmentUseCase}
     * (document produit et rattaché à <b>ce</b> dossier, PDF, taille). Toute erreur nomme la source, jamais la valeur.
     */
    private EmailAttachment attachment(DelegateExecution execution) {
        String literal = DeployedActivityParameters.getLiteral(execution, ATTACHMENT);
        if (literal == null) {
            return null;
        }
        String origin = ATTACHMENT + " source " + literal;
        PortalRecipientSource source = PortalRecipientSource.parse(literal)
                .orElseThrow(() -> new NotificationValidationException(ATTACHMENT + " source is malformed: " + literal));
        UUID dossierId = dossierId(execution, origin);
        Object value = switch (source.kind()) {
            case PROCESS_VARIABLE -> execution.getVariable(source.name());
            case DOSSIER_FIELD -> recipientResolver
                    .resolve(source, dossierId, execution.getProcessInstanceId())
                    .orElse(null);
        };
        if (value == null || value.toString().isBlank()) {
            throw new NotificationValidationException(origin + " resolved to no value");
        }
        if (!(value instanceof CharSequence text)) {
            throw new NotificationValidationException(origin + " is not a text value");
        }
        return attachmentResolver.resolve(dossierId, text.toString().strip(),
                DeployedActivityParameters.getLiteral(execution, ATTACHMENT_NAME), origin);
    }

    /**
     * TODO §93 (D-NOTIF-SOURCE-LITERAL) — {@code flowable:notificationTo="dossierField:<champ>"} lit le champ <b>à jour</b>
     * dans le dossier, {@code "variable:<nom>"} la variable de process désignée. Une source n'est résolue que si elle est écrite
     * dans l'attribut littéral du BPMN déployé : une variable {@code notificationTo} (posée au démarrage ou à la complétion
     * d'une tâche, donc par un utilisateur) reste une adresse littérale et ne peut pas désigner un autre champ. Une valeur
     * résolue absente, vide, non textuelle ou qui n'est pas une seule adresse fait échouer l'étape : incident si la brique est
     * asynchrone ({@code flowable:async}), sinon refus 422 de la complétion ou du démarrage qui l'exécute (revue M1). Le message
     * nomme la source, jamais une valeur.
     */
    private String recipient(DelegateExecution execution, boolean withAttachment) {
        String literal = DeployedActivityParameters.getLiteral(execution, RECIPIENT);
        if (literal != null && !PortalRecipientSource.hasSourcePrefix(literal)) {
            return literal; // écrit par le concepteur dans le BPMN déployé : inchangé
        }
        if (literal == null && withAttachment) {
            throw new NotificationValidationException(ATTACHMENT + " requires flowable:" + RECIPIENT + " in the BPMN");
        }
        if (literal == null) {
            // Revue L3 : repli sur la variable notificationTo, posée au démarrage ou à la complétion d'une tâche (saisie
            // utilisateur) — même contrôle « une seule adresse » qu'une source.
            String variable = stringOrNull(DeployedActivityParameters.get(execution, RECIPIENT));
            return variable == null ? null : singleMailbox(variable.strip(), RECIPIENT + " variable");
        }
        PortalRecipientSource source = PortalRecipientSource.parse(literal)
                .orElseThrow(() -> new NotificationValidationException(RECIPIENT + " source is malformed: " + literal));
        Object value = switch (source.kind()) {
            case PROCESS_VARIABLE -> execution.getVariable(source.name());
            case DOSSIER_FIELD -> recipientResolver
                    .resolve(source, dossierId(execution, RECIPIENT + " source " + literal), execution.getProcessInstanceId())
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
        return singleMailbox(address, RECIPIENT + " source " + literal);
    }

    /**
     * Une valeur saisie par un utilisateur (champ du dossier, valeur reçue d'un portail, variable de démarrage ou de complétion)
     * doit être une seule boîte {@code local@domaine}. Le relais SMTP refuse déjà plusieurs adresses, mais tard et sans incident ;
     * ce contrôle échoue tôt et écarte aussi la forme « Nom <adresse> », la syntaxe de groupe et les retours à la ligne. Le
     * message nomme l'origine, jamais la valeur.
     */
    private static String singleMailbox(String address, String origin) {
        if (!SINGLE_MAILBOX.matcher(address).matches()) {
            throw new NotificationValidationException(origin + " is not a single e-mail address");
        }
        return address;
    }

    /** @param origin « {@code <paramètre> source <source>} », repris tel quel dans le message d'erreur */
    /** TODO §113 — variable de plateforme {@code dossierId} (réservée) ; absente ou illisible : pas d'inscription. */
    private static UUID originDossier(DelegateExecution execution) {
        Object raw = execution.getVariable(WorkflowVariables.DOSSIER_ID);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static UUID dossierId(DelegateExecution execution, String origin) {
        Object raw = execution.getVariable(WorkflowVariables.DOSSIER_ID);
        if (raw == null || raw.toString().isBlank()) {
            throw new NotificationValidationException(origin + " needs the dossierId variable");
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(raw.toString().trim());
        } catch (IllegalArgumentException malformed) {
            throw new NotificationValidationException(origin + " needs a valid dossierId variable");
        }
    }

    private static List<NotificationChannel> attachmentChannels(DelegateExecution execution) {
        String literal = DeployedActivityParameters.getLiteral(execution, "notificationChannels");
        if (literal == null) {
            throw new NotificationValidationException(ATTACHMENT + " requires flowable:notificationChannels in the BPMN");
        }
        List<NotificationChannel> channels = parseChannels(literal);
        if (!channels.contains(NotificationChannel.EMAIL)) {
            throw new NotificationValidationException(ATTACHMENT + " requires EMAIL in flowable:notificationChannels");
        }
        return channels;
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
