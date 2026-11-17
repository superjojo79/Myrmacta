package be.myrmacta.infrastructure.demo;

import be.myrmacta.application.workflow.ManualStartOrchestrator;
import be.myrmacta.domain.connector.model.WebhookAuthType;
import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.document.port.in.ActivateDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.CreateDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.ListDocumentTemplateUseCase;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.port.in.CreateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierStatusUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.in.DeployBpmnProcessUseCase;
import be.myrmacta.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.connector.builtin.SignatureCallbackConnector;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.RepositoryService;
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
 * TODO §148 (D-VIDEO-SIGN, issue #59) — profil {@value #PROFILE} : la scène de la capsule C13 (signature électronique).
 * L'exemple {@code exemples/convention-fournisseur} est installé par les vrais cas d'usage : type, configuration du
 * workflow, modèle de la convention (actif), processus ; puis un dossier d'exemple, ouvert, dont le processus attend la
 * relecture. Le signataire est externe : la scène exige le portail ({@code app.portal.enabled}).
 *
 * <p>Fournisseur de signature ({@code app.signature.provider}) : {@code stub} (essais, sans valeur juridique) ou
 * {@code yousign}. Avec Yousign, la scène crée le connecteur {@code signature} du <strong>bac à sable</strong> depuis
 * {@code YOUSIGN_SANDBOX_API_KEY} et {@code YOUSIGN_WEBHOOK_SECRET} (fichier {@code .env}, ignoré par git), et refuse de
 * démarrer sans eux. Hors de la démo complète ; jamais dans le jar de production ({@code src/embedded}).
 */
@Component
@Profile(SignatureDemoPack.PROFILE)
public class SignatureDemoPack implements DemoPack {

    public static final String PROFILE = "demo-video-signature";
    static final String TYPE = "CONVENTION_FOURNISSEUR";
    static final String PROCESS = "conventionFournisseur";
    static final String BPMN = "convention-fournisseur.bpmn20.xml";
    private static final int ORDER = 50;
    private static final String ACTOR = "demo-signature";
    private static final AccessContext.User ADMIN = AccessContext.user(ACTOR, Set.of("TENANT_ADMIN", "WORKFLOW_ADMIN"));
    private static final Logger log = LoggerFactory.getLogger(SignatureDemoPack.class);

    private final DemoExample example;
    private final ObjectMapper json;
    private final TenantProperties tenant;
    private final String yousignApiKey;
    private final String webhookSecret;
    /** Le bac à sable Yousign n'accepte qu'un signataire de l'organisation du compte : il remplace celui de l'exemple. */
    private final String signerEmail;
    private final boolean yousign;
    private final DossierTypeRepository types;
    private final CreateDossierTypeUseCase createType;
    private final SaveWorkflowTypeConfigUseCase saveConfig;
    private final WorkflowTypeConfigRepository workflowConfigs;
    private final CreateDocumentTemplateUseCase createDocument;
    private final ActivateDocumentTemplateUseCase activateDocument;
    private final ListDocumentTemplateUseCase listDocuments;
    private final ManageConnectorInstanceUseCase connectors;
    private final ConnectorInstanceRepository connectorInstances;
    private final DeployBpmnProcessUseCase deployBpmn;
    private final RepositoryService repository;
    private final DossierRepository dossiers;
    private final CreateDossierUseCase createDossier;
    private final UpdateDossierStatusUseCase updateStatus;
    private final ManualStartOrchestrator starter;
    private final TransactionTemplate transaction;

    public SignatureDemoPack(@Value("${app.demo.signature.dir:exemples/convention-fournisseur}") String dir,
            @Value("${app.demo.locale:fr}") String locale, @Value("${app.signature.provider:none}") String provider,
            @Value("${app.demo.signature.yousign-api-key:${YOUSIGN_SANDBOX_API_KEY:}}") String yousignApiKey,
            @Value("${app.demo.signature.webhook-secret:${YOUSIGN_WEBHOOK_SECRET:}}") String webhookSecret,
            @Value("${app.demo.signature.signer-email:${YOUSIGN_SANDBOX_SIGNER_EMAIL:}}") String signerEmail,
            ObjectMapper json, TenantProperties tenant, DossierTypeRepository types, CreateDossierTypeUseCase createType,
            SaveWorkflowTypeConfigUseCase saveConfig, WorkflowTypeConfigRepository workflowConfigs,
            CreateDocumentTemplateUseCase createDocument, ActivateDocumentTemplateUseCase activateDocument,
            ListDocumentTemplateUseCase listDocuments, ManageConnectorInstanceUseCase connectors,
            ConnectorInstanceRepository connectorInstances, DeployBpmnProcessUseCase deployBpmn, RepositoryService repository,
            DossierRepository dossiers, CreateDossierUseCase createDossier, UpdateDossierStatusUseCase updateStatus,
            ManualStartOrchestrator starter, PlatformTransactionManager transactionManager) {
        this.yousign = switch (provider) {
            case "yousign" -> true;
            case "stub" -> false;
            default -> throw new IllegalStateException("demo: the signature scene runs with app.signature.provider "
                    + "stub or yousign, not '" + provider + "'");
        };
        if (yousign && (yousignApiKey.isBlank() || webhookSecret.isBlank() || signerEmail.isBlank())) {
            throw new IllegalStateException("demo: the signature scene needs the Yousign sandbox settings: set "
                    + "YOUSIGN_SANDBOX_API_KEY (API key of the sandbox), YOUSIGN_WEBHOOK_SECRET (any random secret: the "
                    + "webhook is replayed locally) and YOUSIGN_SANDBOX_SIGNER_EMAIL (an address of your Yousign "
                    + "organisation: the sandbox refuses any other signer) in .env");
        }
        this.signerEmail = signerEmail;
        this.example = new DemoExample(Path.of(dir), locale, json);
        this.json = json;
        this.tenant = tenant;
        this.yousignApiKey = yousignApiKey;
        this.webhookSecret = webhookSecret;
        this.types = types;
        this.createType = createType;
        this.saveConfig = saveConfig;
        this.workflowConfigs = workflowConfigs;
        this.createDocument = createDocument;
        this.activateDocument = activateDocument;
        this.listDocuments = listDocuments;
        this.connectors = connectors;
        this.connectorInstances = connectorInstances;
        this.deployBpmn = deployBpmn;
        this.repository = repository;
        this.dossiers = dossiers;
        this.createDossier = createDossier;
        this.updateStatus = updateStatus;
        this.starter = starter;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public String code() {
        return "signature";
    }

    @Override
    public int order() {
        return ORDER;
    }

    /** Installé quand le dossier d'exemple existe : c'est la dernière étape. Chaque étape est reprise si elle manque. */
    @Override
    public boolean installed() {
        return !dossiers.findByDossierTypeCode(tenant.id(), TYPE).isEmpty();
    }

    @Override
    public void install() {
        if (!Files.isDirectory(example.dir())) {
            throw new IllegalStateException("demo: signature example directory not found: "
                    + example.dir().toAbsolutePath() + " (start from the repository root, or set app.demo.signature.dir)");
        }
        JsonNode type = example.json("config/type-dossier.json");
        if (types.findByCode(tenant.id(), TYPE).isEmpty()) {
            createType.create(new CreateDossierTypeUseCase.CreateDossierTypeCommand(tenant.id(), TYPE,
                    type.get("label").asString(), type.get("workflowKey").asString(), true, false,
                    json.convertValue(type.get("fieldDefinitions"), new TypeReference<List<FieldDefinition>>() {}),
                    null, false, null, List.of(), List.of()), ADMIN);
        }
        JsonNode config = example.json("config/workflow-config.json");
        if (workflowConfigs.findByWorkflowKey(tenant.id(), PROCESS).isEmpty()) {
            saveConfig.create(new WorkflowTypeConfig(UUID.randomUUID(), tenant.id(), PROCESS, config.get("label").asString(),
                    json.convertValue(config.get("statusDefinitions"), new TypeReference<List<WorkflowStatusDefinition>>() {}),
                    json.convertValue(config.get("allowedDossierStatuses"), new TypeReference<List<DossierStatus>>() {}),
                    DossierStatus.valueOf(config.get("onStartDossierStatus").asString()), List.of(), ACTOR, ACTOR), ADMIN);
        }
        agreementTemplate();
        if (yousign) {
            yousignConnector();
        }
        if (repository.createProcessDefinitionQuery().processDefinitionKey(PROCESS).processDefinitionTenantId(tenant.id())
                .count() == 0) {
            // Après le modèle : le déploiement exige un fournisseur de signature et le portail (signataire externe).
            deployBpmn.deploy(BPMN, example.bytes(BPMN));
        }
        sampleCase();
        log.info("demo: signature example installed ({})", yousign ? "Yousign sandbox" : "stub");
    }

    /** Le modèle de la convention, actif ; sa variante anglaise est {@code en/template-convention.html}. */
    private void agreementTemplate() {
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

    /** Le connecteur {@code signature} du bac à sable Yousign : clé d'API et secret du webhook, chiffrés au repos. */
    private void yousignConnector() {
        boolean present = connectorInstances.listByTenantId(tenant.id()).stream()
                .anyMatch(instance -> SignatureCallbackConnector.TYPE.equals(instance.connectorType()));
        if (present) {
            return;
        }
        Map<String, Object> config = Map.of("webhookAuthType", WebhookAuthType.YOUSIGN_HMAC.name(),
                "webhookSecret", webhookSecret, "yousignEnvironment", "sandbox", "yousignApiKey", yousignApiKey);
        connectors.create(new ManageConnectorInstanceUseCase.CreateCommand(tenant.id(),
                example.text("Signature — Yousign (bac à sable)", "Signature — Yousign (sandbox)"),
                SignatureCallbackConnector.TYPE, true, config), ADMIN);
    }

    /** Le dossier d'exemple, ouvert ; son processus démarre et attend la relecture de la convention. */
    private void sampleCase() {
        JsonNode sample = example.json("config/exemple-dossier.json");
        Map<String, Object> fields = new HashMap<>(json.convertValue(sample.get("fields"),
                new TypeReference<Map<String, Object>>() {}));
        String label = sample.get("label").asString();
        if (!signerEmail.isBlank()) {
            fields.put("signataireEmail", signerEmail);
        }
        if (example.english()) {
            label = "Agreement — Lambert Printing";
            fields.put("fournisseur", "Lambert Printing Ltd");
            fields.put("objet", "Printing and enveloping of outgoing mail");
            fields.put("montantAnnuel", "EUR 18,000 excl. VAT");
        }
        String title = label;
        Dossier dossier = transaction.execute(status -> createDossier.create(
                new CreateDossierUseCase.CreateDossierCommand(tenant.id(), TYPE, title, fields, ACTOR), ADMIN));
        transaction.executeWithoutResult(status -> updateStatus.update(
                new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(dossier.id(), DossierStatus.OUVERT, ACTOR, null),
                ADMIN));
        transaction.execute(status -> starter.start(dossier.id(), PROCESS, Map.of(), "demo-signature-" + dossier.id(),
                ACTOR));
    }

    private String read(String file) {
        try {
            return Files.readString(example.dir().resolve(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("demo: signature example file unreadable: " + file, e);
        }
    }
}
