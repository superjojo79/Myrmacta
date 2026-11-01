package be.myrmacta.infrastructure.flowable;

import be.myrmacta.application.workflow.ManualStartOrchestrator;
import be.myrmacta.domain.connector.port.in.AttachInboundFilesUseCase;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierRelation;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.DuplicateDossierRelationException;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.RelationType;
import be.myrmacta.domain.dossier.port.in.CreateDossierRelationUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierStatusUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import be.myrmacta.domain.mailroom.port.out.MailMessageIndex;
import be.myrmacta.domain.mailroom.port.out.ReplyWorkflowSettings;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.dossier.ChildDossierRelationConfirmer;
import be.myrmacta.infrastructure.mailroom.MailThreadAttacher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * TODO §113 (D-MAILROOM-THREAD, D-MAILROOM-REACT modes 1 et 2) — brique « Rattacher au fil », en tête du tri. Cherche le fil
 * d'un courrier dans l'index (In-Reply-To, puis References de la plus récente à la plus ancienne, puis conversationId Graph, puis
 * en secours une référence de dossier dans l'objet si ce dossier a déjà échangé avec le domaine de l'expéditeur).
 * Rattache si l'expéditeur est attesté, si son domaine est celui de l'interlocuteur inscrit, et si le dossier du fil est encore
 * ouvert : lien {@code LINKED}, copie des pièces (contrôles des pièces reçues), avertissement des participants, message
 * {@code courrierRecu} à toutes les exécutions du dossier qui l'attendent ({@link MailThreadAttacher}, partagé avec l'outil MCP).
 * Sinon le courrier suit le routage ordinaire.
 */
@Component("attachToThreadTask")
class AttachToThreadDelegate implements JavaDelegate {

    static final String MESSAGE = MailThreadAttacher.MESSAGE;
    static final String RESULT = "filResultat";
    static final String THREAD_DOSSIER_ID = "filDossierId";
    static final String THREAD_DOSSIER_REFERENCE = "filDossierReference";
    static final String REASON = "filMotif";
    static final String REPLY_WORKFLOW = "filReponseWorkflow";
    static final String REPLY_DOSSIER_ID = "filReponseDossierId";
    static final String REPLY_REASON = "filReponseMotif";
    static final int MAX_COPIED_PIECES = MailThreadAttacher.MAX_COPIED_PIECES;
    /** Référence produite par {@code SequentialReferenceGenerator} ({@code DOS-<type>-<année>-<séquence>}) ; bornée, sans retour arrière coûteux. */
    static final Pattern SUBJECT_REFERENCE = Pattern.compile("\\bDOS-[A-Za-z0-9_-]{1,64}-\\d{4}-\\d{6,12}\\b");
    private static final int MAX_SUBJECT_SCAN = 300;
    private static final int MAX_SUBJECT_REFERENCES = 3;
    private static final int SUBJECT_EXCHANGES_SCANNED = 50;
    private static final int SUBJECT_LINKED_SCANNED = 20;

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
    private final ReplyWorkflowSettings replySettings;
    private final WorkflowDeploymentPort deployments;
    private final WorkflowTypeConfigRepository workflowConfigs;
    private final DossierTypeRepository types;
    private final ChildDossierRelationConfirmer childConfirmer;
    private final ManualStartOrchestrator orchestrator;
    private final UpdateDossierStatusUseCase updateStatus;
    private final MailThreadAttacher attacher;

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
            TenantProperties tenantProperties,
            ReplyWorkflowSettings replySettings,
            WorkflowDeploymentPort deployments,
            WorkflowTypeConfigRepository workflowConfigs,
            DossierTypeRepository types,
            ChildDossierRelationConfirmer childConfirmer,
            ManualStartOrchestrator orchestrator,
            UpdateDossierStatusUseCase updateStatus) {
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
        this.replySettings = Objects.requireNonNull(replySettings, "replySettings");
        this.deployments = Objects.requireNonNull(deployments, "deployments");
        this.workflowConfigs = Objects.requireNonNull(workflowConfigs, "workflowConfigs");
        this.types = Objects.requireNonNull(types, "types");
        this.childConfirmer = Objects.requireNonNull(childConfirmer, "childConfirmer");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.updateStatus = Objects.requireNonNull(updateStatus, "updateStatus");
        this.attacher = new MailThreadAttacher(relations, createRelation, elements, storage, attachFiles, links, participants,
                notifications, engine);
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
        if (entry.counterpartDomain() == null || !entry.counterpartDomain().equalsIgnoreCase(senderDomain)) {
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
        attacher.attach(tenantId, mail, target, idempotencyKey, "workflow");
        execution.setVariable(RESULT, "RATTACHE");
        execution.setVariable(THREAD_DOSSIER_ID, target.id().toString());
        execution.setVariable(THREAD_DOSSIER_REFERENCE, target.reference());
        startReplyWorkflow(execution, tenantId, mail, target, idempotencyKey);
    }

    /**
     * Mode 3 (D-MAILROOM-REPLY-WORKFLOW) — le workflow « réponse » du type du dossier du fil, s'il en déclare un. Toutes les
     * vérifications avant la première écriture : un refus laisse le rattachement fait et dit pourquoi ({@code filReponseMotif}).
     */
    private void startReplyWorkflow(DelegateExecution execution, String tenantId, Dossier mail, Dossier target, String idempotencyKey) {
        Optional<ReplyWorkflowSetting> found = replySettings.find(tenantId, target.dossierTypeCode());
        if (found.isEmpty()) {
            execution.setVariable(REPLY_WORKFLOW, "AUCUN");
            return;
        }
        ReplyWorkflowSetting setting = found.get();
        Map<String, Object> variables = Map.of("courrierId", mail.id().toString(), "courrierReference", mail.reference());
        if (deployments.findDeploymentInfo(tenantId, setting.workflowKey()).isEmpty()) {
            replyNotStarted(execution, "process non déployé : " + setting.workflowKey());
            return;
        }
        Optional<WorkflowTypeConfig> config = workflowConfigs.findByWorkflowKey(tenantId, setting.workflowKey());
        if (config.isEmpty()) {
            replyNotStarted(execution, "configuration de workflow absente : " + setting.workflowKey());
            return;
        }
        try {
            // Revue de clôture : un process qui déclare ses variables de démarrage (§111) sans courrierId / courrierReference
            // refuserait le démarrage APRÈS le rattachement, qui serait alors annulé avec lui.
            orchestrator.bindStartVariables(setting.workflowKey(), variables);
        } catch (IllegalArgumentException refused) {
            replyNotStarted(execution, "variables de démarrage refusées par " + setting.workflowKey() + " : " + refused.getMessage());
            return;
        }
        if (setting.mode() == ReplyWorkflowSetting.Mode.SUR_DOSSIER) {
            if (links.findActiveByDossierId(target.id()).isPresent()) {
                replyNotStarted(execution, "un process est déjà actif sur " + target.reference());
                return;
            }
            if (!config.get().allowsDossierStatus(target.status())) {
                replyNotStarted(execution, "statut " + target.status() + " refusé par " + setting.workflowKey());
                return;
            }
            orchestrator.start(target.id(), setting.workflowKey(), variables, idempotencyKey + ":reponse", "workflow");
            execution.setVariable(REPLY_WORKFLOW, "DEMARRE_SUR_DOSSIER");
            return;
        }
        Optional<DossierType> childType = types.findByCode(tenantId, setting.childTypeCode());
        if (childType.isEmpty() || !childType.get().active()) {
            replyNotStarted(execution, "type enfant inconnu ou inactif : " + setting.childTypeCode());
            return;
        }
        for (FieldDefinition definition : childType.get().fieldDefinitions()) {
            // Le dossier enfant naît sans valeur : un champ obligatoire sans défaut, ou obligatoire sous condition (revue de
            // clôture), ferait échouer sa création après le rattachement.
            boolean conditional = definition.requiredWhen() != null && !definition.requiredWhen().isBlank();
            if ((definition.required() && !definition.hasDefaultValue()) || conditional) {
                replyNotStarted(execution, "champ obligatoire du type enfant : " + definition.name());
                return;
            }
        }
        if (!config.get().allowsDossierStatus(DossierStatus.OUVERT)) {
            replyNotStarted(execution, "statut OUVERT refusé par " + setting.workflowKey());
            return;
        }
        String childKey = idempotencyKey + ":reponse";
        UUID child;
        try {
            child = childConfirmer.confirm(tenantId, target.id(), setting.childTypeCode(), "Réponse " + mail.reference(), Map.of(),
                    RelationType.PARENT_CHILD, childKey);
        } catch (DuplicateDossierRelationException race) {
            child = childConfirmer.confirm(tenantId, target.id(), setting.childTypeCode(), "Réponse " + mail.reference(), Map.of(),
                    RelationType.PARENT_CHILD, childKey);
        }
        dossiers.findById(child).filter(dossier -> dossier.status() == DossierStatus.DRAFT).ifPresent(draft -> updateStatus.update(
                new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(draft.id(), DossierStatus.OUVERT, "workflow", null), SYSTEM));
        orchestrator.start(child, setting.workflowKey(), variables, childKey, "workflow");
        execution.setVariable(REPLY_WORKFLOW, "DEMARRE_SUR_ENFANT");
        execution.setVariable(REPLY_DOSSIER_ID, child.toString());
    }

    private static void replyNotStarted(DelegateExecution execution, String reason) {
        execution.setVariable(REPLY_WORKFLOW, "NON");
        execution.setVariable(REPLY_REASON, reason);
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
        Optional<MailIndexEntry> byConversation =
                conversation == null ? Optional.empty() : index.find(tenantId, MailIndexEntry.Kind.CONVERSATION, conversation);
        return byConversation.isPresent() ? byConversation : bySubjectReference(tenantId, execution);
    }

    /**
     * D-MAILROOM-THREAD, 3e moyen, en secours : une référence de dossier dans l'objet. Les références sont séquentielles, donc
     * devinables : elle ne vaut que si ce dossier a déjà échangé un e-mail (reçu ou envoyé) avec le domaine de l'expéditeur. On
     * renvoie alors cette entrée de l'index, et la suite (attestation, domaine, dossier ouvert) est celle des autres moyens.
     */
    private Optional<MailIndexEntry> bySubjectReference(String tenantId, DelegateExecution execution) {
        String subject = text(execution.getVariable("mailSubject"));
        String senderDomain = domain(text(execution.getVariable("mailFrom")));
        if (subject == null || senderDomain == null) {
            return Optional.empty();
        }
        Matcher matcher = SUBJECT_REFERENCE.matcher(subject.length() > MAX_SUBJECT_SCAN ? subject.substring(0, MAX_SUBJECT_SCAN) : subject);
        for (int candidates = 0; candidates < MAX_SUBJECT_REFERENCES && matcher.find(); candidates++) {
            Optional<Dossier> referenced = dossiers.findByReference(tenantId, matcher.group());
            if (referenced.isPresent()) {
                Optional<MailIndexEntry> exchange = exchangeWith(tenantId, referenced.get().id(), senderDomain);
                if (exchange.isPresent()) {
                    // L'entrée désigne le dossier référencé lui-même (sens OUT : il est le dossier du fil), avec l'interlocuteur
                    // qui prouve l'échange ; la suite (attestation, domaine, dossier ouvert) s'applique comme pour les autres moyens.
                    MailIndexEntry proof = exchange.get();
                    return Optional.of(new MailIndexEntry(tenantId, proof.kind(), proof.key(), MailIndexEntry.Direction.OUT,
                            referenced.get().id(), proof.counterpart(), proof.recordedAt()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Un e-mail échangé avec ce domaine : inscrit au nom du dossier (envoyé par « Notifier », ou reçu directement), ou au nom
     * d'un courrier qui lui est lié (un e-mail reçu est inscrit au nom de son courrier, routé ensuite vers le dossier métier).
     */
    private Optional<MailIndexEntry> exchangeWith(String tenantId, UUID dossierId, String senderDomain) {
        List<UUID> candidates = new ArrayList<>();
        candidates.add(dossierId);
        for (DossierRelation relation : relations.findByDossier(tenantId, dossierId)) {
            if (relation.type() == RelationType.LINKED && candidates.size() <= SUBJECT_LINKED_SCANNED) {
                candidates.add(dossierId.equals(relation.sourceDossierId()) ? relation.targetDossierId() : relation.sourceDossierId());
            }
        }
        for (UUID candidate : candidates) {
            for (MailIndexEntry entry : index.findByDossier(tenantId, candidate, SUBJECT_EXCHANGES_SCANNED)) {
                if (senderDomain.equalsIgnoreCase(entry.counterpartDomain())) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
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
