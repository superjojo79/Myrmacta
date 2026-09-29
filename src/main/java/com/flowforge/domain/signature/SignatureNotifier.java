package com.flowforge.domain.signature;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.signature.model.SignatureOutcome;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Notifications BROWSER (inbox) des demandes de signature (TODO §72, US-7, US-10). Même contrat de
 * payload que {@code PortalParticipantNotifier} : {@code type}, {@code title}, {@code body},
 * {@code linkId} (lien dossier/workflow), {@code dossierId}. Jamais d'adresse en clair, jamais de jeton.
 */
public final class SignatureNotifier {

    public static final String SIGNATURE_OUTCOME = "SIGNATURE_OUTCOME";
    public static final String SIGNATURE_FAILED = "SIGNATURE_FAILED";
    public static final String SIGNATURE_REQUESTED = "SIGNATURE_REQUESTED";
    public static final String SIGNATURE_INVITE_FAILED = "SIGNATURE_INVITE_FAILED";

    private final String tenantId;
    private final DossierWorkflowLinkRepository workflowLinkRepository;
    private final WorkflowParticipantPort participantPort;
    private final SendNotificationUseCase sendNotificationUseCase;

    public SignatureNotifier(
            String tenantId,
            DossierWorkflowLinkRepository workflowLinkRepository,
            WorkflowParticipantPort participantPort,
            SendNotificationUseCase sendNotificationUseCase) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.workflowLinkRepository = Objects.requireNonNull(workflowLinkRepository, "workflowLinkRepository");
        this.participantPort = Objects.requireNonNull(participantPort, "participantPort");
        this.sendNotificationUseCase = Objects.requireNonNull(sendNotificationUseCase, "sendNotificationUseCase");
    }

    /** Participants internes du workflow : issue d'une demande (US-7). */
    public void notifyOutcome(SignatureRequest request, SignatureOutcome outcome) {
        String title;
        String body;
        String type = SIGNATURE_OUTCOME;
        switch (outcome) {
            case SIGNED -> {
                title = "Signature obtenue";
                body = "Le document a été signé par tous les signataires requis"
                        + (request.simulated() ? " (SIMULATION, sans valeur juridique)." : ".");
            }
            case DECLINED -> {
                title = "Signature refusée";
                body = "Un signataire requis a refusé de signer le document.";
            }
            case EXPIRED -> {
                title = "Signature expirée";
                body = "L'échéance de la demande de signature est dépassée.";
            }
            case CANCELLED -> {
                title = "Signature annulée";
                body = "La demande de signature a été annulée.";
            }
            case FAILED -> {
                type = SIGNATURE_FAILED;
                title = "Signature en échec";
                body = "La demande de signature n'a pas pu aboutir : " + reasonText(request.statusReason()) + ".";
            }
            default -> throw new IllegalStateException("unexpected outcome " + outcome);
        }
        notifyParticipants(request, type, title, body);
    }

    /** Une invitation externe n'a pas pu être envoyée après plusieurs tentatives (adresse masquée). */
    public void notifyInviteFailed(SignatureRequest request, String maskedRecipient) {
        notifyParticipants(request, SIGNATURE_INVITE_FAILED, "Invitation à signer non envoyée",
                "L'invitation destinée à " + maskedRecipient + " n'a pas pu être envoyée.");
    }

    /** Signataire interne : c'est à lui de signer (à son tour en séquentiel). */
    public void notifyInternalSigner(SignatureRequest request, String userId) {
        Optional<DossierWorkflowLink> link =
                workflowLinkRepository.findByProcessInstanceId(tenantId, request.processInstanceId());
        Map<String, Object> payload = payload(request, link, SIGNATURE_REQUESTED, "Signature demandée",
                "Un document attend votre signature (voir « Mes signatures »).");
        sendNotificationUseCase.send(
                new NotificationEvent(List.of(NotificationChannel.BROWSER), null, null, null, userId, null, payload),
                tenantId);
    }

    static String reasonText(String reason) {
        if (reason == null) {
            return "erreur inattendue";
        }
        return switch (reason) {
            case "NO_ADDRESS" -> "un signataire n'a pas d'adresse ou d'identifiant valide";
            case "DUPLICATE_SIGNER" -> "la même personne est désignée deux fois";
            case "SOURCE_NOT_FOUND" -> "le document à signer est introuvable dans le dossier";
            case "SOURCE_TOO_LARGE" -> "le document à signer dépasse la taille autorisée";
            case "TOO_MANY_SIGNERS" -> "le nombre de signataires dépasse la limite autorisée";
            case "SOURCE_NOT_PDF" -> "le document à signer n'est pas un PDF";
            case "MODE_UNSUPPORTED" -> "le fournisseur ne supporte pas l'ordre de signature demandé";
            case "OPTIONAL_UNSUPPORTED" -> "le fournisseur ne supporte pas les signataires facultatifs";
            case "PROVIDER_ERROR" -> "le fournisseur de signature a répondu par une erreur";
            default -> "erreur inattendue";
        };
    }

    private void notifyParticipants(SignatureRequest request, String type, String title, String body) {
        Optional<DossierWorkflowLink> link =
                workflowLinkRepository.findByProcessInstanceId(tenantId, request.processInstanceId());
        if (link.isEmpty()) {
            return;
        }
        Map<String, Object> payload = payload(request, link, type, title, body);
        for (String userId : participantPort.findParticipantUserIds(link.get())) {
            sendNotificationUseCase.send(
                    new NotificationEvent(List.of(NotificationChannel.BROWSER), null, null, null, userId, null, payload),
                    tenantId);
        }
    }

    private static Map<String, Object> payload(
            SignatureRequest request, Optional<DossierWorkflowLink> link, String type, String title, String body) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("type", type);
        payload.put("title", title);
        payload.put("body", body);
        link.ifPresent(l -> payload.put("linkId", l.id().toString()));
        payload.put("dossierId", request.dossierId().toString());
        payload.put("signatureRequestId", request.id().toString());
        return payload;
    }
}
