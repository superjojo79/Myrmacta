package be.myrmacta.infrastructure.demo;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.document.port.in.ActivateDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.CreateDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.ListDocumentTemplateUseCase;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.dossier.port.in.CreateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.notification.template.port.in.ActivateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.ListNotificationTemplateUseCase;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.in.DeployBpmnProcessUseCase;
import be.myrmacta.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import be.myrmacta.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §143 (issue #101) — profil {@value #PROFILE} : la scène des capsules C5 (portail) et C6 (pré-vérification IA) et
 * du screenshot S13. L'exemple {@code exemples/autorisation-riverain} est installé par les vrais cas d'usage, dans l'ordre
 * de mise en service de son README : type (avec ses types de pièces), questionnaires, configuration du workflow, modèle de
 * décision et gabarit de demande d'informations, activés, puis une variante du processus, traduite comme le reste :
 * {@code app.demo.riverain.variant} vaut {@code portal} (C5, S13 ; exige {@code app.portal.enabled}) ou
 * {@code preverification} (C6 ; exige {@code app.precheck.enabled}). Les deux partagent la même clé. Hors de
 * la démo complète (§127 : l'exemple en a été retiré) ; jamais dans le jar de production ({@code src/embedded}).
 */
@Component
@Profile(RiverainDemoPack.PROFILE)
public class RiverainDemoPack implements DemoPack {

    public static final String PROFILE = "demo-video-riverain";
    static final String TYPE = "AUTORISATION_RIVERAIN";
    static final String PROCESS = "autorisationRiverain";
    static final String PORTAL_VARIANT = "autorisation-riverain.bpmn20.xml";
    static final String PRECHECK_VARIANT = "autorisation-riverain-preverification.bpmn20.xml";
    private static final int ORDER = 40;
    private static final String ACTOR = "demo-riverain";
    private static final AccessContext.User ADMIN = AccessContext.user(ACTOR, Set.of("TENANT_ADMIN", "WORKFLOW_ADMIN"));
    private static final Logger log = LoggerFactory.getLogger(RiverainDemoPack.class);

    private final DemoExample example;
    private final ObjectMapper json;
    private final TenantProperties tenant;
    private final DossierTypeRepository types;
    private final CreateDossierTypeUseCase createType;
    private final SaveTaskQuestionSchemaUseCase saveQuestions;
    private final TaskQuestionSchemaRepository questionSchemas;
    private final SaveWorkflowTypeConfigUseCase saveConfig;
    private final WorkflowTypeConfigRepository workflowConfigs;
    private final CreateDocumentTemplateUseCase createDocument;
    private final ActivateDocumentTemplateUseCase activateDocument;
    private final ListDocumentTemplateUseCase listDocuments;
    private final CreateNotificationTemplateUseCase createNotification;
    private final ActivateNotificationTemplateUseCase activateNotification;
    private final ListNotificationTemplateUseCase listNotifications;
    private final DeployBpmnProcessUseCase deployBpmn;
    private final RepositoryService repository;
    private final String variant;

    public RiverainDemoPack(@Value("${app.demo.riverain.dir:exemples/autorisation-riverain}") String dir,
            @Value("${app.demo.riverain.variant:portal}") String variant,
            @Value("${app.demo.locale:fr}") String locale, ObjectMapper json, TenantProperties tenant,
            DossierTypeRepository types, CreateDossierTypeUseCase createType, SaveTaskQuestionSchemaUseCase saveQuestions,
            TaskQuestionSchemaRepository questionSchemas, SaveWorkflowTypeConfigUseCase saveConfig,
            WorkflowTypeConfigRepository workflowConfigs, CreateDocumentTemplateUseCase createDocument,
            ActivateDocumentTemplateUseCase activateDocument, ListDocumentTemplateUseCase listDocuments,
            CreateNotificationTemplateUseCase createNotification, ActivateNotificationTemplateUseCase activateNotification,
            ListNotificationTemplateUseCase listNotifications, DeployBpmnProcessUseCase deployBpmn,
            RepositoryService repository) {
        this.example = new DemoExample(Path.of(dir), locale, json);
        this.json = json;
        this.tenant = tenant;
        this.types = types;
        this.createType = createType;
        this.saveQuestions = saveQuestions;
        this.questionSchemas = questionSchemas;
        this.saveConfig = saveConfig;
        this.workflowConfigs = workflowConfigs;
        this.createDocument = createDocument;
        this.activateDocument = activateDocument;
        this.listDocuments = listDocuments;
        this.createNotification = createNotification;
        this.activateNotification = activateNotification;
        this.listNotifications = listNotifications;
        this.deployBpmn = deployBpmn;
        this.repository = repository;
        this.variant = switch (variant) {
            case "portal" -> PORTAL_VARIANT;
            case "preverification" -> PRECHECK_VARIANT;
            default -> throw new IllegalArgumentException(
                    "demo: unknown riverain variant '" + variant + "' (app.demo.riverain.variant: portal or preverification)");
        };
    }

    @Override
    public String code() {
        return "riverain";
    }

    @Override
    public int order() {
        return ORDER;
    }

    /** Installé quand le processus est déployé : c'est la dernière étape (D-DEMO-INSTALL). */
    @Override
    public boolean installed() {
        return repository.createProcessDefinitionQuery().processDefinitionKey(PROCESS)
                .processDefinitionTenantId(tenant.id()).count() > 0;
    }

    @Override
    public void install() {
        if (!Files.isDirectory(example.dir())) {
            throw new IllegalStateException("demo: riverain example directory not found: " + example.dir().toAbsolutePath()
                    + " (start from the repository root, or set app.demo.riverain.dir)");
        }
        JsonNode type = example.json("config/type-dossier.json");
        if (types.findByCode(tenant.id(), TYPE).isEmpty()) {
            createType.create(new CreateDossierTypeUseCase.CreateDossierTypeCommand(tenant.id(), TYPE,
                    type.get("label").asString(), type.get("workflowKey").asString(), true, false,
                    json.convertValue(type.get("fieldDefinitions"), new TypeReference<List<FieldDefinition>>() {}),
                    null, false, null, List.of(),
                    json.convertValue(type.get("pieceTypes"), new TypeReference<List<PieceType>>() {})), ADMIN);
        }
        for (JsonNode schema : example.json("config/questions.json")) {
            if (questionSchemas.findByKey(tenant.id(), schema.get("schemaKey").asString()).isEmpty()) {
                saveQuestions.save(new TaskQuestionSchema(UUID.randomUUID(), tenant.id(), schema.get("schemaKey").asString(),
                        schema.get("label").asString(),
                        json.convertValue(schema.get("fields"), new TypeReference<List<FieldDefinition>>() {}), ACTOR, ACTOR),
                        ADMIN);
            }
        }
        JsonNode config = example.json("config/workflow-config.json");
        if (workflowConfigs.findByWorkflowKey(tenant.id(), PROCESS).isEmpty()) {
            saveConfig.create(new WorkflowTypeConfig(UUID.randomUUID(), tenant.id(), PROCESS, config.get("label").asString(),
                    json.convertValue(config.get("statusDefinitions"), new TypeReference<List<WorkflowStatusDefinition>>() {}),
                    json.convertValue(config.get("allowedDossierStatuses"), new TypeReference<List<DossierStatus>>() {}),
                    DossierStatus.valueOf(config.get("onStartDossierStatus").asString()), List.of(), ACTOR, ACTOR), ADMIN);
        }
        decisionTemplate();
        informationRequestTemplate();
        // En dernier : le déploiement exige le modèle de décision actif, et le portail ou la pré-vérification activés.
        deployBpmn.deploy(variant, example.xml(variant).getBytes(StandardCharsets.UTF_8));
        log.info("demo: riverain example installed ({})", variant);
    }

    /** Le modèle du document de décision, actif ; sa variante anglaise est {@code en/template-decision.html}. */
    private void decisionTemplate() {
        JsonNode model = example.json("config/modele-document.json");
        String key = model.get("templateKey").asString();
        List<DocumentTemplate> versions = listDocuments.list(tenant.id(), key, false, PageQuery.of(0, 20)).content();
        if (versions.stream().anyMatch(t -> t.status() == TemplateStatus.ACTIVE)) {
            return;
        }
        DocumentTemplate template = versions.stream().filter(t -> t.status() == TemplateStatus.DRAFT).findFirst()
                .orElseGet(() -> createDocument.create(new CreateDocumentTemplateUseCase.CreateCommand(key, null, null,
                        model.get("label").asString(), read(model.get("_htmlContent").asString()), ACTOR)));
        activateDocument.activate(template.id(), tenant.id(), ACTOR);
    }

    /** Le gabarit de l'e-mail « informations complémentaires » (variantes sans portail et avec pré-vérification), actif. */
    private void informationRequestTemplate() {
        JsonNode gabarit = example.json("config/gabarit-demande-informations.json");
        String key = gabarit.get("templateKey").asString();
        // Comme le modèle de décision : une installation interrompue entre la création et l'activation est reprise.
        var versions = listNotifications.list(key, PageQuery.of(0, 20)).content();
        if (versions.stream().anyMatch(t -> t.status() == TemplateStatus.ACTIVE)) {
            return;
        }
        var template = versions.stream().filter(t -> t.status() == TemplateStatus.DRAFT).findFirst()
                .orElseGet(() -> createNotification.create(new CreateNotificationTemplateUseCase.CreateCommand(key, null, null,
                        gabarit.get("label").asString(), gabarit.get("subject").asString(),
                        gabarit.get("textBody").asString(), gabarit.get("htmlBody").asString(), ACTOR)));
        activateNotification.activate(template.id(), ACTOR);
    }

    private String read(String file) {
        try {
            return Files.readString(example.dir().resolve(file));
        } catch (IOException e) {
            throw new UncheckedIOException("demo: riverain example file unreadable: " + file, e);
        }
    }
}
