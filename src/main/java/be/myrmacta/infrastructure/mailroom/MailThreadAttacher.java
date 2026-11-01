package be.myrmacta.infrastructure.mailroom;

import be.myrmacta.domain.connector.model.InboundAttachment;
import be.myrmacta.domain.connector.port.in.AttachInboundFilesUseCase;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DuplicateDossierRelationException;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.RelationType;
import be.myrmacta.domain.dossier.port.in.CreateDossierRelationUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationEvent;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * TODO §113 (D-MAILROOM-REACT modes 1 et 2, D-MAILROOM-MCP) — effets du rattachement d'un courrier à un dossier, communs à la
 * brique « Rattacher au fil » et à l'outil MCP {@code attach_mail_to_dossier} : lien {@code LINKED} (idempotent), copie des pièces
 * (contrôles des pièces reçues), avertissement des participants du process actif, message {@code courrierRecu} remis à toutes les
 * exécutions du dossier qui l'attendent. L'appelant a fait toutes les vérifications (droits, dossier ouvert).
 */
@Component
public class MailThreadAttacher {

    public static final String MESSAGE = "courrierRecu";
    public static final int MAX_COPIED_PIECES = 20;
    private static final AccessContext SYSTEM = AccessContext.system("mail-thread-attacher");

    private final DossierRelationRepository relations;
    private final CreateDossierRelationUseCase createRelation;
    private final DossierElementRepository elements;
    private final DocumentStoragePort storage;
    private final AttachInboundFilesUseCase attachFiles;
    private final DossierWorkflowLinkRepository links;
    private final WorkflowParticipantPort participants;
    private final SendNotificationUseCase notifications;
    private final WorkflowEnginePort engine;

    public MailThreadAttacher(DossierRelationRepository relations, CreateDossierRelationUseCase createRelation,
            DossierElementRepository elements, DocumentStoragePort storage, AttachInboundFilesUseCase attachFiles,
            DossierWorkflowLinkRepository links, WorkflowParticipantPort participants, SendNotificationUseCase notifications,
            WorkflowEnginePort engine) {
        this.relations = Objects.requireNonNull(relations, "relations");
        this.createRelation = Objects.requireNonNull(createRelation, "createRelation");
        this.elements = Objects.requireNonNull(elements, "elements");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.attachFiles = Objects.requireNonNull(attachFiles, "attachFiles");
        this.links = Objects.requireNonNull(links, "links");
        this.participants = Objects.requireNonNull(participants, "participants");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Si le courrier est déjà lié à ce dossier (rejeu, ou nouvel essai d'un agent MCP), rien n'est refait : ni pièces recopiées,
     * ni participants prévenus, ni message remis (revue de sécurité du lot 6, SR-1).
     *
     * @param idempotencyKey un rejeu avec la même clé ne recrée pas le lien
     * @param createdBy auteur du lien ({@code workflow} pour la brique, l'utilisateur pour l'outil MCP)
     * @return vrai si le lien vient d'être créé (et les effets appliqués), faux s'il existait déjà
     */
    public boolean attach(String tenantId, Dossier mail, Dossier target, String idempotencyKey, String createdBy) {
        if (!link(tenantId, mail.id(), target.id(), idempotencyKey, createdBy)) {
            return false;
        }
        copyPieces(tenantId, mail, target);
        warnParticipants(tenantId, mail, target);
        engine.correlateMessageToAllWaiting(MESSAGE, target.id().toString(),
                Map.of("courrierId", mail.id().toString(), "courrierReference", mail.reference()));
        return true;
    }

    private boolean link(String tenantId, UUID mailId, UUID targetId, String idempotencyKey, String createdBy) {
        if (relations.findByIdempotencyKey(tenantId, mailId, idempotencyKey).isPresent()) {
            return false;
        }
        // Déjà lié, par le fil ou à la main : vérifié AVANT l'insertion, car la violation de uq_dossier_relation condamnerait
        // la transaction PostgreSQL de l'appelant.
        boolean linked = relations.findByDossier(tenantId, mailId).stream().anyMatch(relation -> relation.type() == RelationType.LINKED
                && (targetId.equals(relation.targetDossierId()) || targetId.equals(relation.sourceDossierId())));
        if (linked) {
            return false;
        }
        try {
            createRelation.create(new CreateDossierRelationUseCase.CreateDossierRelationCommand(tenantId, mailId, targetId,
                    RelationType.LINKED, createdBy, idempotencyKey), SYSTEM);
            return true;
        } catch (DuplicateDossierRelationException alreadyLinked) {
            return false;
        }
    }

    /** Les pièces du courrier sont recopiées (relues dans le stockage), avec les contrôles des pièces reçues. */
    private void copyPieces(String tenantId, Dossier mail, Dossier target) {
        List<InboundAttachment> files = new ArrayList<>();
        for (DossierElement element : elements.findByDossierIdOrderByAddedAtAsc(mail.id())) {
            if (element.type() != ElementType.ATTACHMENT || element.storageRef() == null || files.size() == MAX_COPIED_PIECES) {
                continue;
            }
            Object filename = element.metadata().get("filename");
            Object contentType = element.metadata().get("contentType");
            files.add(new InboundAttachment(filename == null ? element.label() : filename.toString(),
                    contentType == null ? "application/octet-stream" : contentType.toString(),
                    storage.retrieve(tenantId, element.storageRef())));
        }
        if (!files.isEmpty()) {
            attachFiles.attach(target.id(), "mailroom:" + mail.reference(), files);
        }
    }

    /** Mode 1 : les participants du workflow en cours du dossier (assignés, observateurs) sont prévenus. */
    private void warnParticipants(String tenantId, Dossier mail, Dossier target) {
        Set<String> recipients = new LinkedHashSet<>();
        links.findActiveByDossierId(target.id()).ifPresent(link -> recipients.addAll(participants.findParticipantUserIds(link)));
        for (String userId : recipients) {
            notifications.send(new NotificationEvent(List.of(NotificationChannel.BROWSER), null, null, null, userId, null,
                    Map.of("type", "MAIL_THREAD_REPLY",
                            "title", "Nouveau courrier",
                            "body", "Un courrier (" + mail.reference() + ") répond au fil du dossier " + target.reference() + ".",
                            "dossierId", target.id().toString(),
                            "courrierId", mail.id().toString())), tenantId);
        }
    }
}
