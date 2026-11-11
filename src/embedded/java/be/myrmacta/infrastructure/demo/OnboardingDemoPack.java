package be.myrmacta.infrastructure.demo;

import be.myrmacta.application.workflow.ManualStartOrchestrator;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.port.in.CreateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierStatusUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.in.CompleteTaskUseCase;
import be.myrmacta.domain.workflow.port.in.DeployBpmnProcessUseCase;
import be.myrmacta.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import be.myrmacta.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §127 (D-DEMO-PACKS, D-DEMO-FAKE-APIS, lot 1b de #55) — paquet « onboarding » de la démo. Installe l'exemple
 * {@code exemples/onboarding-collaborateur} par les cas d'usage réels (type, questionnaires, configuration, connecteurs,
 * BPMN), avec ses trois connecteurs pointés sur les faux SIRH, IAM et ITSM de {@link DemoFakeApis}. Puis sème trois
 * arrivées par le vrai processus :
 * <ul>
 *   <li>une arrivée provisionnée (manager, SIRH, IAM, validation des accès, ITSM) : champs « Provisioning » remplis ;</li>
 *   <li>une arrivée qui attend la validation du manager ;</li>
 *   <li>une arrivée approuvée, déjà créée dans le SIRH et l'IAM, qui attend la validation des accès par l'IT.</li>
 * </ul>
 * Les appels asynchrones sont exécutés ici, job par job, jusqu'à la tâche attendue : le paquet ne dépend pas de l'exécuteur
 * asynchrone. Build {@code embedded-db} seulement.
 */
@Component
@Profile("demo")
public class OnboardingDemoPack implements DemoPack {

    static final int ORDER = 20;
    static final String TYPE = "ONBOARDING_COLLABORATEUR";
    static final String PROCESS = "onboardingCollaborateur";
    private static final String ACTOR = "demo-onboarding";
    private static final AccessContext.User ADMIN = AccessContext.user(ACTOR, Set.of("TENANT_ADMIN", "WORKFLOW_ADMIN"));
    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(OnboardingDemoPack.class);

    private final Path example;
    private final ObjectMapper json;
    private final TenantProperties tenant;
    private final DossierTypeRepository types;
    private final CreateDossierTypeUseCase createType;
    private final SaveTaskQuestionSchemaUseCase saveQuestions;
    private final SaveWorkflowTypeConfigUseCase saveConfig;
    private final ManageConnectorInstanceUseCase connectors;
    private final DeployBpmnProcessUseCase deployBpmn;
    private final CreateDossierUseCase createDossier;
    private final UpdateDossierStatusUseCase updateStatus;
    private final ManualStartOrchestrator starter;
    private final CompleteTaskUseCase completeTask;
    private final DemoFakeApis apis;
    private final TaskService tasks;
    private final RuntimeService runtime;
    private final ManagementService management;
    private final TransactionTemplate transaction;

    public OnboardingDemoPack(@Value("${app.demo.onboarding.dir:exemples/onboarding-collaborateur}") String exampleDir,
            ObjectMapper json, TenantProperties tenant, DossierTypeRepository types, CreateDossierTypeUseCase createType,
            SaveTaskQuestionSchemaUseCase saveQuestions, SaveWorkflowTypeConfigUseCase saveConfig,
            ManageConnectorInstanceUseCase connectors, DeployBpmnProcessUseCase deployBpmn, CreateDossierUseCase createDossier,
            UpdateDossierStatusUseCase updateStatus, ManualStartOrchestrator starter, CompleteTaskUseCase completeTask,
            DemoFakeApis apis, TaskService tasks, RuntimeService runtime, ManagementService management,
            PlatformTransactionManager transactionManager) {
        this.example = Path.of(exampleDir);
        this.json = json;
        this.tenant = tenant;
        this.types = types;
        this.createType = createType;
        this.saveQuestions = saveQuestions;
        this.saveConfig = saveConfig;
        this.connectors = connectors;
        this.deployBpmn = deployBpmn;
        this.createDossier = createDossier;
        this.updateStatus = updateStatus;
        this.starter = starter;
        this.completeTask = completeTask;
        this.apis = apis;
        this.tasks = tasks;
        this.runtime = runtime;
        this.management = management;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public String code() {
        return "onboarding";
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public boolean installed() {
        return types.findByCode(tenant.id(), TYPE).isPresent();
    }

    @Override
    public void install() {
        if (!Files.isDirectory(example)) {
            throw new IllegalStateException("demo: onboarding example directory not found: " + example.toAbsolutePath()
                    + " (start from the repository root, or set APP_DEMO_ONBOARDING_DIR)");
        }
        installExample();
        seedArrivals();
        log.info("demo: onboarding example installed; 3 arrivals (provisioned, awaiting the manager, awaiting IT)");
    }

    /** Même ordre que la mise en service du README de l'exemple : type, questionnaires, configuration, connecteurs, BPMN. */
    private void installExample() {
        JsonNode type = read("config/type-dossier.json");
        List<FieldDefinition> fields = json.convertValue(type.get("fieldDefinitions"), new TypeReference<List<FieldDefinition>>() {});
        createType.create(new CreateDossierTypeUseCase.CreateDossierTypeCommand(tenant.id(), type.get("code").asString(),
                type.get("label").asString(), type.get("workflowKey").asString(), true, false, fields, null, false, null,
                List.of()), ADMIN);
        for (JsonNode schema : read("config/questions.json")) {
            saveQuestions.save(new TaskQuestionSchema(UUID.randomUUID(), tenant.id(), schema.get("schemaKey").asString(),
                    schema.get("label").asString(),
                    json.convertValue(schema.get("fields"), new TypeReference<List<FieldDefinition>>() {}), ACTOR, ACTOR),
                    ADMIN);
        }
        JsonNode config = read("config/workflow-config.json");
        saveConfig.create(new WorkflowTypeConfig(UUID.randomUUID(), tenant.id(), config.get("processDefinitionKey").asString(),
                config.get("label").asString(),
                json.convertValue(config.get("statusDefinitions"), new TypeReference<List<WorkflowStatusDefinition>>() {}),
                json.convertValue(config.get("allowedDossierStatuses"), new TypeReference<List<DossierStatus>>() {}),
                config.hasNonNull("onStartDossierStatus") ? DossierStatus.valueOf(config.get("onStartDossierStatus").asString()) : null,
                List.of(), ACTOR, ACTOR), ADMIN);

        // Connecteurs « service seul » de l'exemple, sur les faux services (en-tête statique pour les trois, comme le test de
        // l'exemple) ; le BPMN reçoit leurs identifiants à la place des marqueurs de config/connecteurs.json.
        String bpmn = string("onboarding-collaborateur.bpmn20.xml");
        for (JsonNode connector : read("config/connecteurs.json")) {
            String name = connector.get("name").asString();
            Map<String, Object> connectorConfig = new LinkedHashMap<>();
            connectorConfig.put("webhookEnabled", false);
            connectorConfig.put("baseUrl", apis.baseUrl() + "/" + name.toLowerCase(java.util.Locale.ROOT));
            connectorConfig.put("authHeaderValue", "Bearer demo-" + name.toLowerCase(java.util.Locale.ROOT));
            ConnectorInstanceData created = connectors.create(new ManageConnectorInstanceUseCase.CreateCommand(tenant.id(),
                    name + " (démo)", connector.get("connectorType").asString(), true, connectorConfig), ADMIN);
            bpmn = bpmn.replace(connector.get("_placeholder").asString(), created.id().toString());
        }
        deployBpmn.deploy("onboarding-collaborateur.bpmn20.xml", bpmn.getBytes(StandardCharsets.UTF_8));
    }

    private void seedArrivals() {
        String provisioned = arrive("Élise", "Van den Broeck", "elise.vdb@example.org", "Développeuse Java", "IT",
                "DEVELOPPEUR");
        complete(provisioned, "validationManager", Map.of("decisionManager", "APPROUVER"));
        advanceTo(provisioned, "validationAcces");
        complete(provisioned, "validationAcces", Map.of("niveauAcces", "DEVELOPPEURS"));
        advanceToEnd(provisioned);

        arrive("Thomas", "Peeters", "thomas.peeters@example.org", "Gestionnaire de dossiers", "OPERATIONS", "STANDARD");

        String awaitingIt = arrive("Sarah", "Dubois", "sarah.dubois@example.org", "Directrice financière", "FINANCE",
                "DIRECTION");
        complete(awaitingIt, "validationManager", Map.of("decisionManager", "APPROUVER"));
        advanceTo(awaitingIt, "validationAcces");
    }

    /** Crée et ouvre un dossier d'arrivée, puis démarre le processus : il attend la validation du manager. */
    private String arrive(String firstName, String lastName, String email, String function, String department, String profile) {
        JsonNode sample = read("config/exemple-dossier.json");
        Map<String, Object> fields = new HashMap<>(json.convertValue(sample.get("fields"), new TypeReference<Map<String, Object>>() {}));
        fields.put("prenom", firstName);
        fields.put("nom", lastName);
        fields.put("emailPersonnel", email);
        fields.put("fonction", function);
        fields.put("departement", department);
        fields.put("profilPoste", profile);
        String label = "Arrivée — " + firstName + " " + lastName + " — " + fields.get("dateArrivee");
        Dossier dossier = transaction.execute(status -> createDossier.create(
                new CreateDossierUseCase.CreateDossierCommand(tenant.id(), TYPE, label, fields, ACTOR), ADMIN));
        transaction.executeWithoutResult(status -> updateStatus.update(
                new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(dossier.id(), DossierStatus.OUVERT, ACTOR, null), ADMIN));
        var started = transaction.execute(status -> starter.start(dossier.id(), PROCESS, Map.of(),
                "demo-onboarding-" + dossier.id(), ACTOR));
        String instance = started.link().processInstanceId();
        advanceTo(instance, "validationManager");
        return instance;
    }

    private void complete(String instance, String taskKey, Map<String, Object> variables) {
        Task task = tasks.createTaskQuery().processInstanceId(instance).taskDefinitionKey(taskKey).singleResult();
        if (task == null) {
            throw new IllegalStateException("demo: onboarding task " + taskKey + " not found on " + instance);
        }
        String dossierId = String.valueOf(runtime.getVariable(instance, "dossierId"));
        transaction.executeWithoutResult(status -> completeTask.complete(task.getId(), dossierId, variables, ADMIN));
    }

    private void advanceTo(String instance, String taskKey) {
        advance(instance, () -> tasks.createTaskQuery().processInstanceId(instance).taskDefinitionKey(taskKey).count() > 0,
                "task " + taskKey);
    }

    private void advanceToEnd(String instance) {
        advance(instance, () -> runtime.createProcessInstanceQuery().processInstanceId(instance).count() == 0, "the end");
    }

    /**
     * Exécute les jobs du processus (appels de connecteurs et leurs contrôles, asynchrones) jusqu'à la condition. Un job déjà
     * pris par l'exécuteur asynchrone, s'il est actif, est laissé à celui-ci.
     */
    private void advance(String instance, java.util.function.BooleanSupplier reached, String target) {
        Instant deadline = Instant.now().plus(STEP_TIMEOUT);
        FlowableException last = null;
        while (Instant.now().isBefore(deadline)) {
            if (reached.getAsBoolean()) {
                return;
            }
            var jobs = management.createJobQuery().processInstanceId(instance).list();
            for (var job : jobs) {
                try {
                    management.executeJob(job.getId());
                } catch (FlowableException ex) {
                    last = ex;
                }
            }
            if (jobs.isEmpty()) {
                sleep();
            }
        }
        throw new IllegalStateException("demo: onboarding process " + instance + " did not reach " + target, last);
    }

    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("demo: interrupted", ex);
        }
    }

    private JsonNode read(String file) {
        return json.readTree(string(file));
    }

    private String string(String file) {
        try {
            return Files.readString(example.resolve(file));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
