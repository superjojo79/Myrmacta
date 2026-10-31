package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.connector.model.InboundAttachment;
import be.myrmacta.domain.connector.port.in.AttachInboundFilesUseCase;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierRelation;
import be.myrmacta.domain.dossier.model.DuplicateDossierRelationException;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.RelationType;
import be.myrmacta.domain.dossier.port.in.CreateDossierRelationUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import be.myrmacta.domain.mailroom.port.out.MailMessageIndex;
import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationEvent;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * TODO §113 (D-MAILROOM-THREAD, D-MAILROOM-REACT modes 1 et 2) — brique « Rattacher au fil », en tête du tri. Cherche le fil
 * d'un courrier dans l'index (In-Reply-To, puis References de la plus récente à la plus ancienne, puis conversationId Graph).
 * Rattache si l'expéditeur est attesté, si son domaine est celui de l'interlocuteur inscrit, et si le dossier du fil est encore
 * ouvert : lien {@code LINKED}, copie des pièces (contrôles des pièces reçues), avertissement des participants, message
 * {@code courrierRecu} à toutes les exécutions du dossier qui l'attendent. Sinon le courrier suit le routage ordinaire.
 */
@Component("attachToThreadTask")
class AttachToThreadDelegate implements JavaDelegate {

    static final String MESSAGE = "courrierRecu";
    static final String RESULT = "filResultat";
    static final String THREAD_DOSSIER_ID = "filDossierId";
    static final String THREAD_DOSSIER_REFERENCE = "filDossierReference";
    static final String REASON = "filMotif";
    static final int MAX_COPIED_PIECES = 20;

    private static final AccessContext SYSTEM = AccessContext.system("flowable-delegate");

    private final MailMessageIndex index;
    private final DossierRepository dossiers;
    private final DossierRelationRepository relations;
    private final CreateDossierRelationUseCase createRelation;
    private final DossierElementRepository elements;
    private final DocumentStoragePort storage;
    private final AttachInboundFilesUseCase attachFiles;
    private final DossierWorkflowLinkRepository links;
    private final WorkflowParticipantPort participants;
    private final SendNotificationUseCase notifications;
    private final WorkflowEnginePort engine;
    private final TenantProperties tenantProperties;

    AttachToThreadDelegate(
            MailMessageIndex index,
            DossierRepository dossiers,
            DossierRelationRepository relations,
            CreateDossierRelationUseCase createRelation,
            DossierElementRepository elements,
            DocumentStoragePort storage,
            AttachInboundFilesUseCase attachFiles,
            DossierWorkflowLinkRepository links,
            WorkflowParticipantPort participants,
            SendNotificationUseCase notifications,
            WorkflowEnginePort engine,
            TenantProperties tenantProperties) {
        this.index = Objects.requireNonNull(index, "index");
        this.dossiers = Objects.requireNonNull(dossiers, "dossiers");
        this.relations = Objects.requireNonNull(relations, "relations");
        this.createRelation = Objects.requireNonNull(createRelation, "createRelation");
        this.elements = Objects.requireNonNull(elements, "elements");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.attachFiles = Objects.requireNonNull(attachFiles, "attachFiles");
        this.links = Objects.requireNonNull(links, "links");
        this.participants = Objects.requireNonNull(participants, "participants");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
    }

    @Override
    public void execute(DelegateExecution execution) {
        String tenantId = tenantProperties.id();
        UUID mailId = UUID.fromString(String.valueOf(execution.getVariable(WorkflowVariables.DOSSIER_ID)));
        Dossier mail = dossiers.findById(mailId).orElseThrow(() -> new IllegalStateException("mail dossier not found: " + mailId));
        Optional<MailIndexEntry> found = lookup(tenantId, execution);
        if (found.isEmpty()) {
            notThreaded(execution, "hors fil");
            return;
        }
        if (!Boolean.TRUE.equals(execution.getVariable("mailSenderAuthenticated"))) {
            notThreaded(execution, "expéditeur non attesté");
            return;
        }
        MailIndexEntry entry = found.get();
        String senderDomain = domain(text(execution.getVariable("mailFrom")));
        if (entry.counterpartDomain() == null || !entry.counterpartDomain().equals(senderDomain)) {
            notThreaded(execution, "domaine de l'expéditeur différent de celui du fil");
            return;
        }
        Optional<Dossier> thread = threadDossier(tenantId, entry);
        if (thread.isEmpty() || thread.get().id().equals(mailId)) {
            notThreaded(execution, "dossier du fil introuvable");
            return;
        }
        Dossier target = thread.get();
        if (target.status().blocksMutation()) {
            notThreaded(execution, "dossier du fil clôturé : " + target.reference());
            return;
        }

        String idempotencyKey = execution.getProcessInstanceId() + ":" + execution.getCurrentActivityId();
        link(tenantId, mailId, target.id(), idempotencyKey);
        copyPieces(tenantId, mail, target);
        warnParticipants(tenantId, mail, target);
        engine.correlateMessageToAllWaiting(MESSAGE, target.id().toString(),
                Map.of("courrierId", mailId.toString(), "courrierReference", mail.reference()));
        execution.setVariable(RESULT, "RATTACHE");
        execution.setVariable(THREAD_DOSSIER_ID, target.id().toString());
        execution.setVariable(THREAD_DOSSIER_REFERENCE, target.reference());
    }

    /** In-Reply-To, puis References de la plus récente à la plus ancienne, puis conversationId. */
    private Optional<MailIndexEntry> lookup(String tenantId, DelegateExecution execution) {
        List<String> ids = new ArrayList<>();
        String inReplyTo = text(execution.getVariable("mailInReplyTo"));
        if (inReplyTo != null) {
            ids.add(inReplyTo);
        }
        String references = text(execution.getVariable("mailReferences"));
        if (references != null) {
            List<String> listed = List.of(references.split("\\s+"));
            for (int i = listed.size() - 1; i >= 0; i--) {
                ids.add(listed.get(i));
            }
        }
        for (String id : new LinkedHashSet<>(ids)) {
            Optional<MailIndexEntry> entry = index.find(tenantId, MailIndexEntry.Kind.MESSAGE_ID, id);
            if (entry.isPresent()) {
                return entry;
            }
        }
        String conversation = text(execution.getVariable("mailConversationId"));
        return conversation == null ? Optional.empty() : index.find(tenantId, MailIndexEntry.Kind.CONVERSATION, conversation);
    }

    /** Un e-mail envoyé désigne son dossier ; un e-mail reçu, le courrier d'origine ou le dossier métier où il a été routé. */
    private Optional<Dossier> threadDossier(String tenantId, MailIndexEntry entry) {
        Optional<Dossier> origin = dossiers.findById(entry.dossierId()).filter(dossier -> tenantId.equals(dossier.tenantId()));
        if (origin.isEmpty() || entry.direction() == MailIndexEntry.Direction.OUT) {
            return origin;
        }
        for (DossierRelation relation : relations.findByDossier(tenantId, origin.get().id())) {
            if (relation.type() == RelationType.LINKED && relation.sourceDossierId().equals(origin.get().id())
                    && relation.idempotencyKey() != null) {
                Optional<Dossier> business = dossiers.findById(relation.targetDossierId());
                if (business.isPresent()) {
                    return business;
                }
            }
        }
        return origin;
    }

    private void link(String tenantId, UUID mailId, UUID targetId, String idempotencyKey) {
        if (relations.findByIdempotencyKey(tenantId, mailId, idempotencyKey).isPresent()) {
            return;
        }
        try {
            createRelation.create(new CreateDossierRelationUseCase.CreateDossierRelationCommand(tenantId, mailId, targetId,
                    RelationType.LINKED, "workflow", idempotencyKey), SYSTEM);
        } catch (DuplicateDossierRelationException alreadyLinked) {
            // Rejeu du job : le lien existe déjà.
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

    private static void notThreaded(DelegateExecution execution, String reason) {
        execution.setVariable(RESULT, "NOUVEAU");
        execution.setVariable(REASON, reason);
    }

    private static String domain(String address) {
        if (address == null) {
            return null;
        }
        int at = address.lastIndexOf('@');
        return at < 0 || at == address.length() - 1 ? null : address.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().strip();
    }
}
