package be.myrmacta.domain.workflow.portal;

import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationEvent;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Notifications BROWSER (inbox, PR #135) des participants internes du workflow d'une tâche
 * externe (US-6). Même contrat de payload que les producteurs existants ({@code type},
 * {@code title}, {@code body}, {@code linkId} = lien dossier/workflow, {@code dossierId}). Le
 * destinataire externe n'apparaît que masqué.
 */
public final class PortalParticipantNotifier {

    public static final String PORTAL_TASK_SUBMITTED = "PORTAL_TASK_SUBMITTED";
    public static final String PORTAL_LINK_EXPIRED = "PORTAL_LINK_EXPIRED";
    public static final String PORTAL_LINK_NOT_SENT = "PORTAL_LINK_NOT_SENT";

    private final String tenantId;
    private final DossierWorkflowLinkRepository workflowLinkRepository;
    private final WorkflowParticipantPort participantPort;
    private final SendNotificationUseCase sendNotificationUseCase;

    public PortalParticipantNotifier(
            String tenantId,
            DossierWorkflowLinkRepository workflowLinkRepository,
            WorkflowParticipantPort participantPort,
            SendNotificationUseCase sendNotificationUseCase) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.workflowLinkRepository = Objects.requireNonNull(workflowLinkRepository, "workflowLinkRepository");
        this.participantPort = Objects.requireNonNull(participantPort, "participantPort");
        this.sendNotificationUseCase = Objects.requireNonNull(sendNotificationUseCase, "sendNotificationUseCase");
    }

    public void notifySubmitted(PortalLink link) {
        notify(link, PORTAL_TASK_SUBMITTED, "Réponse d'un tiers externe",
                "Le tiers (" + link.maskedRecipient() + ") a répondu à la tâche qui lui était adressée.");
    }

    public void notifyExpired(PortalLink link) {
        notify(link, PORTAL_LINK_EXPIRED, "Lien tiers expiré",
                "Le lien envoyé à " + link.maskedRecipient()
                        + " a expiré sans réponse. Renvoyez un lien ou décidez de la suite.");
    }

    public void notifyNotSent(PortalLink link) {
        String reason = switch (link.statusReason()) {
            case NO_ADDRESS -> "aucune adresse e-mail n'est renseignée";
            case INVALID_ADDRESS -> "l'adresse e-mail est invalide";
            case SCHEMA_MISSING -> "le formulaire de la tâche n'existe plus";
            case SEND_FAILED -> "l'e-mail n'a pas pu être envoyé";
            case null, default -> "une erreur est survenue";
        };
        notify(link, PORTAL_LINK_NOT_SENT, "Lien tiers non envoyé",
                "Le lien d'une tâche externe n'a pas été envoyé : " + reason + ".");
    }

    private void notify(PortalLink link, String type, String title, String body) {
        Optional<DossierWorkflowLink> workflowLink =
                workflowLinkRepository.findByProcessInstanceId(tenantId, link.processInstanceId());
        if (workflowLink.isEmpty()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "type", type,
                "title", title,
                "body", body,
                "linkId", workflowLink.get().id().toString(),
                "dossierId", link.dossierId().toString(),
                "taskId", link.taskId());
        for (String userId : participantPort.findParticipantUserIds(workflowLink.get())) {
            sendNotificationUseCase.send(
                    new NotificationEvent(List.of(NotificationChannel.BROWSER), null, null, null, userId, null, payload),
                    tenantId);
        }
    }
}
