package be.myrmacta.infrastructure.demo;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.InboundAttachment;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.deadline.port.in.ManageDeadlineUseCase;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.port.in.CreateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.mailroom.model.DepositFile;
import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import be.myrmacta.domain.mailroom.port.in.DepositMailUseCase;
import be.myrmacta.domain.mailroom.port.in.ManageReplyWorkflowUseCase;
import be.myrmacta.domain.notification.template.port.in.ActivateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.in.DeployBpmnProcessUseCase;
import be.myrmacta.domain.workflow.port.in.DeployDecisionTableUseCase;
import be.myrmacta.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §113 (lot 7 de #217, démo reproductible) et §127 (D-DEMO-PACKS) — paquet « mail room » de la démo, installé par
 * {@link DemoSeeder} avec le profil {@code demo} ou, seul, avec le profil {@code demo-mailroom}. Présent seulement dans le
 * build {@code embedded-db} (jamais dans le jar de production). Sur une base où il est absent, installe l'exemple
 * {@code exemples/digital-mail-room} par les cas d'usage réels (comme l'API), puis fait arriver trois courriers par le vrai
 * pipeline des connecteurs : une facture par e-mail (routée vers la comptabilité, dossier « facture » lié), une amende par
 * l'écran « Déposer » (PDF, file de tri), un e-mail non reconnu (file de tri). Une base déjà installée n'est pas touchée :
 * {@code scripts/demo.sh reset} (ou {@code scripts/demo-mailroom.sh reset}) la vide pour rejouer la démo.
 */
@Component
@Profile({"demo", "demo-mailroom", VideoMailRoomDemoPack.PROFILE})
public class MailRoomDemoPack implements DemoPack {

    /** Premier paquet installé. */
    static final int ORDER = 10;

    static final String DEMO_ACTOR = "demo-mailroom";
    private static final Logger log = LoggerFactory.getLogger(MailRoomDemoPack.class);
    private static final AccessContext.User ADMIN = AccessContext.user(DEMO_ACTOR, Set.of("TENANT_ADMIN", "WORKFLOW_ADMIN"));

    private final DemoExample example;
    private final ObjectMapper json;
    private final TenantProperties tenant;
    private final DossierTypeRepository types;
    private final UserGroupsUseCase groups;
    private final CreateDossierTypeUseCase createType;
    private final SaveWorkflowTypeConfigUseCase saveConfig;
    private final DeployDecisionTableUseCase deployDmn;
    private final DeployBpmnProcessUseCase deployBpmn;
    private final ManageReplyWorkflowUseCase replyWorkflows;
    private final ManageDeadlineUseCase deadlines;
    private final CreateNotificationTemplateUseCase createNotificationTemplate;
    private final ActivateNotificationTemplateUseCase activateNotificationTemplate;
    private final ManageConnectorInstanceUseCase connectors;
    private final ConnectorTriggerRuntime runtime;
    private final DepositMailUseCase deposits;
    private final TransactionTemplate transaction;
    private final boolean sampleMail;

    public MailRoomDemoPack(@Value("${app.demo.mailroom.dir:exemples/digital-mail-room}") String exampleDir,
            @Value("${app.demo.locale:fr}") String locale, ObjectMapper json,
            TenantProperties tenant, DossierTypeRepository types, UserGroupsUseCase groups, CreateDossierTypeUseCase createType,
            SaveWorkflowTypeConfigUseCase saveConfig, DeployDecisionTableUseCase deployDmn, DeployBpmnProcessUseCase deployBpmn,
            ManageReplyWorkflowUseCase replyWorkflows, ManageDeadlineUseCase deadlines, ManageConnectorInstanceUseCase connectors,
            CreateNotificationTemplateUseCase createNotificationTemplate, ActivateNotificationTemplateUseCase activateNotificationTemplate,
            ConnectorTriggerRuntime runtime, DepositMailUseCase deposits, PlatformTransactionManager transactionManager,
            Environment environment) {
        this.example = new DemoExample(Path.of(exampleDir), locale, json);
        this.json = json;
        this.tenant = tenant;
        this.types = types;
        this.groups = groups;
        this.createType = createType;
        this.saveConfig = saveConfig;
        this.deployDmn = deployDmn;
        this.deployBpmn = deployBpmn;
        this.replyWorkflows = replyWorkflows;
        this.deadlines = deadlines;
        this.createNotificationTemplate = createNotificationTemplate;
        this.activateNotificationTemplate = activateNotificationTemplate;
        this.connectors = connectors;
        this.runtime = runtime;
        this.deposits = deposits;
        this.transaction = new TransactionTemplate(transactionManager);
        // TODO §128 (D-VIDEO-MAIL) — pour la capsule vidéo, les courriers arrivent en direct pendant le tournage.
        this.sampleMail = !environment.acceptsProfiles(Profiles.of(VideoMailRoomDemoPack.PROFILE));
    }

    @Override
    public String code() {
        return "mailroom";
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public boolean installed() {
        return types.findByCode(tenant.id(), "COURRIER").isPresent();
    }

    @Override
    public void install() {
        if (!Files.isDirectory(example.dir())) {
            throw new IllegalStateException("demo-mailroom: example directory not found: " + example.dir().toAbsolutePath()
                    + " (start from the repository root, or set APP_DEMO_MAILROOM_DIR)");
        }
        installExample();
        ConnectorInstanceData depositPoint = depositPoint();
        if (!sampleMail) {
            log.info("demo-mailroom: example installed; no sample mail (video profile: mail arrives live)");
            return;
        }
        arrive(depositPoint);
        log.info("demo-mailroom: example installed; 3 mail items arrived (invoice by e-mail, fine by deposit, unknown mail)");
    }

    /** Même ordre que le README de l'exemple : groupes, types, configurations, table, process, réglages, points d'entrée. */
    private void installExample() {
        for (JsonNode group : read("config/groupes.json")) {
            groups.create(group.get("code").asString(), group.get("label").asString(), null);
        }
        for (String file : List.of("config/type-dossier.json", "config/type-facture.json", "config/type-reponse-facture.json")) {
            JsonNode type = read(file);
            List<FieldDefinition> fields = json.convertValue(type.get("fieldDefinitions"), new TypeReference<List<FieldDefinition>>() {});
            createType.create(new CreateDossierTypeUseCase.CreateDossierTypeCommand(tenant.id(), type.get("code").asString(),
                    type.get("label").asString(), type.get("workflowKey").asString(), true, false, fields, null, false, null, List.of()),
                    ADMIN);
        }
        for (String file : List.of("config/workflow-config.json", "config/workflow-config-facture.json",
                "config/workflow-config-reponse.json")) {
            JsonNode config = read(file);
            saveConfig.create(new WorkflowTypeConfig(UUID.randomUUID(), tenant.id(), config.get("processDefinitionKey").asString(),
                    config.get("label").asString(),
                    json.convertValue(config.get("statusDefinitions"), new TypeReference<List<WorkflowStatusDefinition>>() {}),
                    json.convertValue(config.get("allowedDossierStatuses"), new TypeReference<List<DossierStatus>>() {}),
                    config.hasNonNull("onStartDossierStatus") ? DossierStatus.valueOf(config.get("onStartDossierStatus").asString()) : null,
                    List.of(), DEMO_ACTOR, DEMO_ACTOR), ADMIN);
        }
        deployDmn.deploy("routage-courrier.dmn", bytes("routage-courrier.dmn"));
        // Issue #19 : l'accusé de réception de l'exemple (gabarit actif avant le premier courrier attesté).
        JsonNode acknowledgement = read("config/gabarit-accuse-reception.json");
        var template = createNotificationTemplate.create(new CreateNotificationTemplateUseCase.CreateCommand(
                acknowledgement.get("templateKey").asString(), null, null, acknowledgement.get("label").asString(),
                acknowledgement.get("subject").asString(), acknowledgement.get("textBody").asString(),
                acknowledgement.get("htmlBody").asString(), DEMO_ACTOR));
        activateNotificationTemplate.activate(template.id(), DEMO_ACTOR);
        for (String bpmn : List.of("traiter-facture.bpmn20.xml", "traiter-reponse.bpmn20.xml", "tri-courrier.bpmn20.xml")) {
            deployBpmn.deploy(bpmn, bytes(bpmn));
        }
        JsonNode reply = read("config/reponse-facture.json");
        replyWorkflows.set("FACTURE", ReplyWorkflowSetting.Mode.valueOf(reply.get("mode").asString()), reply.get("workflowKey").asString(),
                reply.get("childTypeCode").asString());
        JsonNode deadline = read("config/echeance-facture.json");
        deadlines.set("FACTURE", deadline.get("fieldName").asString(), deadline.get("reminderDays").asInt(),
                deadline.get("escalationGroup").asString());
    }

    /** Le point de dépôt de l'écran « Déposer un courrier » (aussi dans la capsule vidéo, qui y dépose l'amende). */
    private ConnectorInstanceData depositPoint() {
        JsonNode depositExample = read("config/connecteur-depot.exemple.json");
        @SuppressWarnings("unchecked")
        Map<String, Object> depositConfig = json.convertValue(depositExample.get("config"), Map.class);
        return connectors.create(new ManageConnectorInstanceUseCase.CreateCommand(tenant.id(),
                depositExample.get("name").asString(), depositExample.get("connectorType").asString(), true, depositConfig), ADMIN);
    }

    private void arrive(ConnectorInstanceData depositPoint) {
        ConnectorInstanceData mailbox = simulatedMailbox();

        String invoice = example.text("Facture 2026-117", "Invoice 2026-117");
        email(mailbox, "<demo-f117@fournisseur.example>", "compta@fournisseur.example", invoice,
                List.of(new InboundAttachment(example.text("facture-2026-117.pdf", "invoice-2026-117.pdf"), "application/pdf",
                        pdf(invoice))));
        String fine = example.text("Amende de stationnement 2026-0042", "Parking fine 2026-0042");
        transaction.executeWithoutResult(status -> deposits.deposit(depositPoint.id(),
                new DepositFile(example.text("amende-2026-0042.pdf", "fine-2026-0042.pdf"), pdf(fine)), "pv@police.example",
                fine, ADMIN));
        email(mailbox, "<demo-q1@ailleurs.example>", "quelquun@ailleurs.example",
                example.text("Question diverse", "General question"), List.of());
    }

    /**
     * La boîte e-mail de la démo : pas de serveur de messagerie, l'e-mail est remis comme l'IMAP le ferait. Instance
     * désactivée, jamais interrogée ; le type « dépôt » ne se déclenche jamais seul.
     */
    ConnectorInstanceData simulatedMailbox() {
        return connectors.create(new ManageConnectorInstanceUseCase.CreateCommand(tenant.id(), simulatedMailboxName(),
                read("config/connecteur-depot.exemple.json").get("connectorType").asString(), false,
                Map.of("processKey", "triCourrier", "dossierTypeCode", "COURRIER")), ADMIN);
    }

    String simulatedMailboxName() {
        return example.text("Boîte courrier (démo, e-mail simulé)", "Mailbox (demo, simulated e-mail)");
    }

    DemoExample example() {
        return example;
    }

    void email(ConnectorInstanceData mailbox, String messageId, String from, String subject, List<InboundAttachment> files) {
        Map<String, Object> variables = Map.of("mailMessageId", messageId, "mailFrom", from, "mailSubject", subject,
                "mailSenderAuthenticated", true);
        WorkflowTriggerEvent event = new WorkflowTriggerEvent("triCourrier", "COURRIER", Map.of("expediteur", from, "objet", subject),
                variables, null, new ArrayList<>(files), true, new WorkflowTriggerEvent.InboundMailIdentity(messageId, null, from));
        transaction.executeWithoutResult(status -> runtime.deliver(mailbox, event));
    }

    private JsonNode read(String file) {
        return example.json(file);
    }

    private byte[] bytes(String file) {
        return example.bytes(file);
    }

    /** Un PDF minimal mais bien formé (une page, un texte), pour la démo. */
    static byte[] pdf(String text) {
        String safe = text.replace("\\", "").replace("(", "").replace(")", "");
        String content = "BT /F1 18 Tf 72 720 Td (" + safe + ") Tj ET";
        String body = "%PDF-1.4\n"
                + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
                + "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
                + "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
                + "/Resources << /Font << /F1 5 0 R >> >> >> endobj\n"
                + "4 0 obj << /Length " + content.length() + " >> stream\n" + content + "\nendstream endobj\n"
                + "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n"
                + "trailer << /Root 1 0 R >>\n%%EOF\n";
        return body.getBytes(StandardCharsets.US_ASCII);
    }
}
