package com.flowforge.domain.promotion.usecase;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import com.flowforge.domain.dossier.port.in.CreateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.promotion.model.ArtifactType;
import com.flowforge.domain.promotion.model.BundleEntry;
import com.flowforge.domain.promotion.model.ConfigImportRecord;
import com.flowforge.domain.promotion.model.ConfigurationBundle;
import com.flowforge.domain.promotion.model.ImportOutcome;
import com.flowforge.domain.promotion.model.ImportPlan;
import com.flowforge.domain.promotion.model.ImportReport;
import com.flowforge.domain.promotion.model.InvalidBundleException;
import com.flowforge.domain.promotion.model.PlanAction;
import com.flowforge.domain.promotion.model.PlanEntry;
import com.flowforge.domain.promotion.model.ReportEntry;
import com.flowforge.domain.promotion.model.doc.ConfigDocs.ConnectorDoc;
import com.flowforge.domain.promotion.model.doc.ConfigDocs.DossierTypeDoc;
import com.flowforge.domain.promotion.model.doc.ConfigDocs.TaskQuestionSchemaDoc;
import com.flowforge.domain.promotion.model.doc.ConfigDocs.WorkflowConfigDoc;
import com.flowforge.domain.promotion.port.in.ImportConfigurationUseCase;
import com.flowforge.domain.promotion.port.out.ArtifactCodec;
import com.flowforge.domain.promotion.port.out.ConfigImportLogRepository;
import com.flowforge.domain.workflow.model.Provenance;
import com.flowforge.domain.workflow.model.ProposedWorkflowConfig;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.in.ProposeDraftUseCase;
import com.flowforge.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import com.flowforge.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO §83 (D4) — import d'un bundle : plan à blanc puis application, par les cas d'usage existants (jamais d'écriture directe).
 *
 * <ul>
 *   <li>le plan et l'application suivent le même calcul ; ce qui est {@code BLOCKED}/{@code CONFLICT} est ignoré et rapporté ;</li>
 *   <li>BPMN et DMN deviennent des <b>brouillons</b> (provenance {@code IMPORT}), jamais des déploiements (D-PROMO-APPLY) ;</li>
 *   <li>une configuration de workflow est jointe au brouillon BPMN du même nom (appliquée à sa publication) ; sans brouillon à
 *       publier, elle est appliquée directement — sauf si le BPMN correspondant est bloqué (jamais une configuration neuve sur
 *       l'ancien diagramme) ;</li>
 *   <li>un connecteur est créé <b>désactivé</b> : ses URLs viennent d'un autre environnement, un humain les relit puis l'active ;</li>
 *   <li>aucun secret dans un bundle (un bundle qui en porte est refusé) ; les secrets d'une création viennent de la requête, ne
 *       sont jamais journalisés, et sont refusés par le canal MCP.</li>
 * </ul>
 */
public final class ImportConfigurationUseCaseImpl implements ImportConfigurationUseCase {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_.-]{1,64})}}");
    private static final int MAX_REASON_CHARS = 400;

    private final String tenantId;
    private final Clock clock;
    private final TenantAdminGuard guard;
    private final ArtifactCodec codec;
    private final DossierTypeRepository types;
    private final CreateDossierTypeUseCase createType;
    private final UpdateDossierTypeUseCase updateType;
    private final CheckDossierTypeSchemaCompatibilityUseCase compatibility;
    private final TaskQuestionSchemaRepository schemas;
    private final SaveTaskQuestionSchemaUseCase saveSchema;
    private final ConnectorInstanceRepository connectors;
    private final ManageConnectorInstanceUseCase manageConnector;
    private final ConnectorCatalog catalog;
    private final DmnDeploymentPort dmnDeployment;
    private final DmnParserPort dmnParser;
    private final WorkflowDeploymentPort workflowDeployment;
    private final BpmnParserPort bpmnParser;
    private final WorkflowTypeConfigRepository workflowConfigs;
    private final SaveWorkflowTypeConfigUseCase saveWorkflowConfig;
    private final ProposeDraftUseCase propose;
    private final ConfigImportLogRepository journal;

    public ImportConfigurationUseCaseImpl(
            String tenantId,
            Clock clock,
            TenantAdminGuard guard,
            ArtifactCodec codec,
            DossierTypeRepository types,
            CreateDossierTypeUseCase createType,
            UpdateDossierTypeUseCase updateType,
            CheckDossierTypeSchemaCompatibilityUseCase compatibility,
            TaskQuestionSchemaRepository schemas,
            SaveTaskQuestionSchemaUseCase saveSchema,
            ConnectorInstanceRepository connectors,
            ManageConnectorInstanceUseCase manageConnector,
            ConnectorCatalog catalog,
            DmnDeploymentPort dmnDeployment,
            DmnParserPort dmnParser,
            WorkflowDeploymentPort workflowDeployment,
            BpmnParserPort bpmnParser,
            WorkflowTypeConfigRepository workflowConfigs,
            SaveWorkflowTypeConfigUseCase saveWorkflowConfig,
            ProposeDraftUseCase propose,
            ConfigImportLogRepository journal) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.types = Objects.requireNonNull(types, "types");
        this.createType = Objects.requireNonNull(createType, "createType");
        this.updateType = Objects.requireNonNull(updateType, "updateType");
        this.compatibility = Objects.requireNonNull(compatibility, "compatibility");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.saveSchema = Objects.requireNonNull(saveSchema, "saveSchema");
        this.connectors = Objects.requireNonNull(connectors, "connectors");
        this.manageConnector = Objects.requireNonNull(manageConnector, "manageConnector");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.dmnDeployment = Objects.requireNonNull(dmnDeployment, "dmnDeployment");
        this.dmnParser = Objects.requireNonNull(dmnParser, "dmnParser");
        this.workflowDeployment = Objects.requireNonNull(workflowDeployment, "workflowDeployment");
        this.bpmnParser = Objects.requireNonNull(bpmnParser, "bpmnParser");
        this.workflowConfigs = Objects.requireNonNull(workflowConfigs, "workflowConfigs");
        this.saveWorkflowConfig = Objects.requireNonNull(saveWorkflowConfig, "saveWorkflowConfig");
        this.propose = Objects.requireNonNull(propose, "propose");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    // ------------------------------------------------------------------ plan

    @Override
    public ImportPlan plan(AccessContext.User actor, ConfigurationBundle bundle, Map<String, String> parameters) {
        guard.requireTenantAdminOrApiService(actor);
        Objects.requireNonNull(bundle, "bundle");
        return computePlan(actor, bundle, parameters == null ? Map.of() : parameters, Map.of());
    }

    private ImportPlan computePlan(
            AccessContext.User actor,
            ConfigurationBundle bundle,
            Map<String, String> parameters,
            Map<String, Map<String, String>> secrets) {
        rejectSecretsInBundle(bundle);
        Map<String, PlanEntry> byKey = new LinkedHashMap<>();
        List<BundleEntry> ordered = bundle.entries().stream()
                .sorted(Comparator.comparing((BundleEntry e) -> e.type().ordinal()).thenComparing(BundleEntry::key))
                .toList();
        List<PlanEntry> entries = new ArrayList<>();
        for (BundleEntry entry : ordered) {
            PlanEntry planned = switch (entry.type()) {
                case DOSSIER_TYPE -> planDossierType(entry);
                case TASK_QUESTION_SCHEMA -> planSchema(entry);
                case CONNECTOR_INSTANCE -> planConnector(entry, parameters, secrets);
                case DMN -> planDmn(actor, entry);
                case BPMN -> planBpmn(actor, entry, bundle, byKey);
                case WORKFLOW_CONFIG -> planWorkflowConfig(entry, byKey);
            };
            byKey.put(entry.type() + "/" + entry.key(), planned);
            entries.add(planned);
        }
        return new ImportPlan(bundle.sha256(), bundle.formatVersion(), bundle.sourceLabel(), entries);
    }

    /** Un bundle ne porte aucun secret : un fichier de connecteur qui en contient est un bundle forgé ou mal exporté. */
    private void rejectSecretsInBundle(ConfigurationBundle bundle) {
        for (BundleEntry e : bundle.of(ArtifactType.CONNECTOR_INSTANCE)) {
            ConnectorDoc doc = decode(e, ConnectorDoc.class);
            for (Map.Entry<String, Object> field : doc.config().entrySet()) {
                if (ExportConfigurationUseCaseImpl.isSecret(doc.connectorType(), field.getKey(), catalog)
                        && field.getValue() != null && !String.valueOf(field.getValue()).isBlank()) {
                    throw new InvalidBundleException("connector '" + doc.name() + "' carries a value for the secret field '"
                            + field.getKey() + "': a bundle never contains secrets (refused)");
                }
            }
        }
    }

    private <T> T decode(BundleEntry entry, Class<T> type) {
        try {
            return codec.decode(entry.content(), type);
        } catch (InvalidBundleException e) {
            throw new InvalidBundleException(entry.type() + " '" + entry.key() + "': " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new InvalidBundleException(entry.type() + " '" + entry.key() + "' is unreadable: " + e.getMessage(), e);
        }
    }

    private PlanEntry planDossierType(BundleEntry entry) {
        DossierTypeDoc doc = decode(entry, DossierTypeDoc.class);
        requireSameKey(entry, doc.code());
        Optional<DossierType> current = types.findByCode(tenantId, doc.code());
        if (current.isEmpty()) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.CREATE, "");
        }
        if (sameContent(ExportConfigurationUseCaseImpl.typeDoc(current.get()), doc)) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "");
        }
        var check = compatibility.check(new CheckDossierTypeSchemaCompatibilityUseCase.Query(
                tenantId, doc.code(), doc.fieldDefinitions(), doc.allowUnknownFields()));
        if (!check.compatible()) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.CONFLICT, check.incompatibleCount()
                    + " existing dossier(s) would no longer match this schema (e.g. " + check.sampleReferences() + ")");
        }
        return new PlanEntry(entry.type(), entry.key(), PlanAction.UPDATE, "");
    }

    /** Égalité de contenu : même encodage canonique (ordre des clés et espaces sans effet). */
    private boolean sameContent(Object currentDoc, Object bundleDoc) {
        return java.util.Arrays.equals(codec.encode(currentDoc), codec.encode(bundleDoc));
    }

    private PlanEntry planSchema(BundleEntry entry) {
        TaskQuestionSchemaDoc doc = decode(entry, TaskQuestionSchemaDoc.class);
        requireSameKey(entry, doc.schemaKey());
        Optional<TaskQuestionSchema> current = schemas.findByKey(tenantId, doc.schemaKey());
        if (current.isEmpty()) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.CREATE, "");
        }
        boolean same = sameContent(ExportConfigurationUseCaseImpl.schemaDoc(current.get()), doc);
        return new PlanEntry(entry.type(), entry.key(), same ? PlanAction.UNCHANGED : PlanAction.UPDATE, "");
    }

    private PlanEntry planConnector(
            BundleEntry entry, Map<String, String> parameters, Map<String, Map<String, String>> secrets) {
        ConnectorDoc raw = decode(entry, ConnectorDoc.class);
        ConnectorDoc doc;
        try {
            doc = substitute(raw, parameters);
        } catch (MissingParameterException e) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED, e.getMessage(), raw.secretFields());
        }
        Optional<ConnectorInstanceData> current = connectors.listByTenantId(tenantId).stream()
                .filter(c -> c.name().equals(doc.name()))
                .findFirst();
        if (current.isPresent()) {
            if (!current.get().connectorType().equals(doc.connectorType())) {
                return new PlanEntry(entry.type(), entry.key(), PlanAction.CONFLICT,
                        "a connector named '" + doc.name() + "' exists with another type (" + current.get().connectorType() + ")");
            }
            ConnectorDoc now = ExportConfigurationUseCaseImpl.connectorDoc(
                    current.get().name(), current.get().connectorType(), current.get().enabled(), current.get().config(), catalog);
            // « enabled » n'est jamais importé en mise à jour : seule la configuration compte pour l'écart
            boolean same = now.config().equals(doc.config());
            return new PlanEntry(entry.type(), entry.key(), same ? PlanAction.UNCHANGED : PlanAction.UPDATE,
                    same ? "" : "configuration updated; enabled state and secrets are kept");
        }
        Map<String, String> provided = secrets.getOrDefault(doc.name(), Map.of());
        Map<String, Object> candidate = new LinkedHashMap<>(doc.config());
        provided.forEach(candidate::put);
        List<String> missing = doc.secretFields().stream().filter(f -> !provided.containsKey(f)).toList();
        try {
            catalog.get(doc.connectorType()).validate(candidate);
        } catch (RuntimeException e) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED,
                    cap("cannot be created: " + e.getMessage()), missing);
        }
        return new PlanEntry(entry.type(), entry.key(), PlanAction.CREATE, "created disabled: review its endpoints, then enable it", missing);
    }

    private PlanEntry planDmn(AccessContext.User actor, BundleEntry entry) {
        String xml = entry.text();
        try {
            propose.validateDmn(actor, entry.key(), xml);
        } catch (RuntimeException e) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED, cap(e.getMessage()));
        }
        Optional<byte[]> deployed = dmnDeployment.findDeployedXml(tenantId, entry.key());
        if (deployed.isEmpty()) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.CREATE, "saved as a draft to publish");
        }
        return sameXml(deployed.get(), entry.content())
                ? new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "")
                : new PlanEntry(entry.type(), entry.key(), PlanAction.UPDATE, "saved as a draft to publish");
    }

    private PlanEntry planBpmn(AccessContext.User actor, BundleEntry entry, ConfigurationBundle bundle, Map<String, PlanEntry> planned) {
        String xml = entry.text();
        Optional<BundleEntry> configEntry = bundle.find(ArtifactType.WORKFLOW_CONFIG, entry.key());
        ProposedWorkflowConfig config = configEntry.map(e -> toProposed(decode(e, WorkflowConfigDoc.class))).orElse(null);
        if (config == null && workflowConfigs.findByWorkflowKey(tenantId, entry.key()).isEmpty()) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED,
                    "no workflow configuration for '" + entry.key() + "', in the bundle or on this instance");
        }
        try {
            for (String decisionKey : bpmnParser.extractReferencedDecisionKeys(xml.getBytes(StandardCharsets.UTF_8))) {
                if (dmnDeployment.findDeploymentInfo(tenantId, decisionKey).isEmpty()
                        && bundle.find(ArtifactType.DMN, decisionKey).isPresent()) {
                    return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED, "depends on the decision table '"
                            + decisionKey + "', which is only a draft: publish it first, then run the import again");
                }
            }
            propose.validateBpmn(actor, entry.key(), xml, config);
        } catch (RuntimeException e) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED, cap(e.getMessage()));
        }
        Optional<byte[]> deployed = workflowDeployment.findDeployedXml(tenantId, entry.key());
        if (deployed.isEmpty()) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.CREATE, "saved as a draft to publish");
        }
        return sameXml(deployed.get(), entry.content())
                ? new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "")
                : new PlanEntry(entry.type(), entry.key(), PlanAction.UPDATE, "saved as a draft to publish");
    }

    private PlanEntry planWorkflowConfig(BundleEntry entry, Map<String, PlanEntry> planned) {
        WorkflowConfigDoc doc = decode(entry, WorkflowConfigDoc.class);
        requireSameKey(entry, doc.workflowKey());
        Optional<WorkflowTypeConfig> current = workflowConfigs.findByWorkflowKey(tenantId, doc.workflowKey());
        PlanAction action;
        if (current.isEmpty()) {
            action = PlanAction.CREATE;
        } else {
            action = sameContent(ExportConfigurationUseCaseImpl.workflowDoc(current.get()), doc)
                    ? PlanAction.UNCHANGED
                    : PlanAction.UPDATE;
        }
        PlanEntry bpmn = planned.get(ArtifactType.BPMN + "/" + entry.key());
        if (bpmn != null && (bpmn.action() == PlanAction.BLOCKED || bpmn.action() == PlanAction.CONFLICT)) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED,
                    "its BPMN process is blocked: the configuration is not applied alone on top of the old diagram");
        }
        if (action != PlanAction.UNCHANGED && bpmn != null && bpmn.applicable()) {
            return new PlanEntry(entry.type(), entry.key(), action, "joined to the BPMN draft, applied when it is published");
        }
        return new PlanEntry(entry.type(), entry.key(), action, "");
    }

    // ----------------------------------------------------------------- apply

    @Override
    public ImportReport apply(
            AccessContext.User actor,
            ConfigurationBundle bundle,
            Map<String, Map<String, String>> secrets,
            Map<String, String> parameters,
            String expectedBundleSha256,
            Channel channel) {
        guard.requireTenantAdminOrApiService(actor);
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(channel, "channel");
        Map<String, Map<String, String>> providedSecrets = secrets == null ? Map.of() : secrets;
        Map<String, String> params = parameters == null ? Map.of() : parameters;
        if (channel == Channel.MCP && !providedSecrets.isEmpty()) {
            throw new IllegalArgumentException("secrets are never accepted through MCP: enter them in the connector screen");
        }
        if (expectedBundleSha256 != null && !expectedBundleSha256.equals(bundle.sha256())) {
            throw new IllegalArgumentException("the bundle differs from the one that was planned (expected "
                    + expectedBundleSha256 + ", got " + bundle.sha256() + "): plan it again");
        }
        ImportPlan plan = computePlan(actor, bundle, params, providedSecrets);
        List<ReportEntry> report = new ArrayList<>();
        for (PlanEntry planned : plan.entries()) {
            BundleEntry entry = bundle.find(planned.type(), planned.key()).orElseThrow();
            switch (planned.action()) {
                case UNCHANGED -> report.add(new ReportEntry(planned.type(), planned.key(), ImportOutcome.UNCHANGED, ""));
                case CONFLICT, BLOCKED -> report.add(new ReportEntry(planned.type(), planned.key(), ImportOutcome.SKIPPED,
                        planned.reason()));
                case CREATE, UPDATE -> report.add(channel == Channel.MCP && !mcpMayApply(planned)
                        ? new ReportEntry(planned.type(), planned.key(), ImportOutcome.SKIPPED,
                                "not applied through MCP (only BPMN and DMN drafts are): use the admin import screen")
                        : applyOne(actor, planned, entry, bundle, plan, params, providedSecrets));
            }
        }
        ImportReport result = new ImportReport(bundle.sha256(), clock.instant().truncatedTo(ChronoUnit.MICROS), report);
        recordJournal(actor, channel, bundle, result);
        return result;
    }

    /**
     * D-PROMO-MCP : le canal MCP ne dépose que des brouillons BPMN/DMN (relus avant mise en ligne) — avec la configuration de
     * workflow qui leur est jointe. Types de dossier, schémas, connecteurs et configurations autonomes passent par l'écran.
     */
    private static boolean mcpMayApply(PlanEntry planned) {
        return planned.type() == ArtifactType.BPMN
                || planned.type() == ArtifactType.DMN
                || (planned.type() == ArtifactType.WORKFLOW_CONFIG && planned.reason().startsWith("joined to the BPMN draft"));
    }

    private ReportEntry applyOne(
            AccessContext.User actor,
            PlanEntry planned,
            BundleEntry entry,
            ConfigurationBundle bundle,
            ImportPlan plan,
            Map<String, String> params,
            Map<String, Map<String, String>> secrets) {
        try {
            String message = switch (entry.type()) {
                case DOSSIER_TYPE -> applyDossierType(actor, entry, planned.action());
                case TASK_QUESTION_SCHEMA -> applySchema(actor, entry, planned.action());
                case CONNECTOR_INSTANCE -> applyConnector(actor, entry, planned.action(), params, secrets);
                case DMN -> applyDmn(actor, entry, bundle);
                case BPMN -> applyBpmn(actor, entry, bundle);
                case WORKFLOW_CONFIG -> applyWorkflowConfig(actor, entry, plan);
            };
            return new ReportEntry(entry.type(), entry.key(), ImportOutcome.APPLIED, message);
        } catch (RuntimeException e) {
            // Un refus à l'écriture n'arrête pas les autres artefacts : rapporté, et rejouer converge.
            return new ReportEntry(entry.type(), entry.key(), ImportOutcome.FAILED, cap(String.valueOf(e.getMessage())));
        }
    }

    private String applyDossierType(AccessContext.User actor, BundleEntry entry, PlanAction action) {
        DossierTypeDoc d = decode(entry, DossierTypeDoc.class);
        if (action == PlanAction.CREATE) {
            createType.create(new CreateDossierTypeUseCase.CreateDossierTypeCommand(tenantId, d.code(), d.label(),
                    d.workflowKey(), d.active(), d.allowUnknownFields(), d.fieldDefinitions(), d.retentionDays(),
                    d.retentionNever(), d.slaTargetHours(), d.documentTemplateKeys()), actor);
            return "created";
        }
        updateType.update(new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(tenantId, d.code(), d.label(), d.workflowKey(),
                d.active(), d.allowUnknownFields(), d.fieldDefinitions(), d.retentionDays(), d.retentionNever(),
                d.slaTargetHours(), d.documentTemplateKeys()), actor);
        return "updated";
    }

    private String applySchema(AccessContext.User actor, BundleEntry entry, PlanAction action) {
        TaskQuestionSchemaDoc d = decode(entry, TaskQuestionSchemaDoc.class);
        Optional<TaskQuestionSchema> current = schemas.findByKey(tenantId, d.schemaKey());
        saveSchema.save(new TaskQuestionSchema(
                current.map(TaskQuestionSchema::id).orElseGet(UUID::randomUUID), tenantId, d.schemaKey(), d.label(),
                d.fields(), current.map(TaskQuestionSchema::createdBy).orElse(actor.userId()), actor.userId()), actor);
        return action == PlanAction.CREATE ? "created" : "updated";
    }

    private String applyConnector(
            AccessContext.User actor,
            BundleEntry entry,
            PlanAction action,
            Map<String, String> params,
            Map<String, Map<String, String>> secrets) {
        ConnectorDoc doc = substitute(decode(entry, ConnectorDoc.class), params);
        Optional<ConnectorInstanceData> current = connectors.listByTenantId(tenantId).stream()
                .filter(c -> c.name().equals(doc.name()))
                .findFirst();
        if (action == PlanAction.CREATE) {
            Map<String, Object> config = new LinkedHashMap<>(doc.config());
            secrets.getOrDefault(doc.name(), Map.of()).forEach(config::put);
            // désactivé : ses URLs viennent d'un autre environnement (D-PROMO-CONNECTOR-DISABLED)
            manageConnector.create(new ManageConnectorInstanceUseCase.CreateCommand(
                    tenantId, doc.name(), doc.connectorType(), false, config), actor);
            return "created disabled — review its endpoints, then enable it";
        }
        manageConnector.update(new ManageConnectorInstanceUseCase.UpdateCommand(
                current.orElseThrow().id(), tenantId, null, null, doc.config(), false), actor);
        return "configuration updated (enabled state and secrets kept)";
    }

    private String applyDmn(AccessContext.User actor, BundleEntry entry, ConfigurationBundle bundle) {
        propose.proposeDmn(actor, Provenance.IMPORT, entry.key(), entry.text(), note(bundle), false);
        return "draft saved — publish it from 'Brouillons à publier'";
    }

    private String applyBpmn(AccessContext.User actor, BundleEntry entry, ConfigurationBundle bundle) {
        ProposedWorkflowConfig config = bundle.find(ArtifactType.WORKFLOW_CONFIG, entry.key())
                .map(e -> toProposed(decode(e, WorkflowConfigDoc.class)))
                .orElse(null);
        propose.proposeBpmn(actor, Provenance.IMPORT, entry.key(), entry.text(), config, false);
        return "draft saved" + (config != null ? " with its workflow configuration" : "")
                + " — publish it from 'Brouillons à publier'";
    }

    private String applyWorkflowConfig(AccessContext.User actor, BundleEntry entry, ImportPlan plan) {
        PlanEntry planned = plan.find(entry.type(), entry.key()).orElseThrow();
        if (planned.reason().startsWith("joined to the BPMN draft")) {
            return "joined to the BPMN draft, applied when it is published";
        }
        WorkflowConfigDoc d = decode(entry, WorkflowConfigDoc.class);
        Optional<WorkflowTypeConfig> existing = workflowConfigs.findByWorkflowKey(tenantId, d.workflowKey());
        WorkflowTypeConfig config = existing
                .map(c -> new WorkflowTypeConfig(c.id(), c.tenantId(), d.workflowKey(), d.label(), d.statusDefinitions(),
                        d.allowedDossierStatuses(), d.onStartDossierStatus(), d.observerUserIds(), c.createdBy(), actor.userId()))
                .orElseGet(() -> new WorkflowTypeConfig(UUID.randomUUID(), tenantId, d.workflowKey(), d.label(),
                        d.statusDefinitions(), d.allowedDossierStatuses(), d.onStartDossierStatus(), d.observerUserIds(),
                        actor.userId(), actor.userId()));
        if (existing.isPresent()) {
            saveWorkflowConfig.update(config, actor);
            return "updated";
        }
        saveWorkflowConfig.create(config, actor);
        return "created";
    }

    private static String note(ConfigurationBundle bundle) {
        String label = bundle.sourceLabel().isBlank() ? "" : " (" + bundle.sourceLabel() + ")";
        return "Import du bundle " + bundle.sha256().substring(0, 8) + label;
    }

    private void recordJournal(AccessContext.User actor, Channel channel, ConfigurationBundle bundle, ImportReport report) {
        try {
            journal.save(new ConfigImportRecord(UUID.randomUUID(), tenantId, actor.userId(), report.performedAt(),
                    channel.name(), bundle.sha256(), bundle.formatVersion(), bundle.sourceLabel(),
                    (int) report.count(ImportOutcome.APPLIED), (int) report.count(ImportOutcome.UNCHANGED),
                    (int) report.count(ImportOutcome.SKIPPED), (int) report.count(ImportOutcome.FAILED),
                    new String(codec.encode(report), StandardCharsets.UTF_8)));
        } catch (RuntimeException journalFailure) {
            System.getLogger(ImportConfigurationUseCaseImpl.class.getName()).log(System.Logger.Level.ERROR,
                    "configuration import NOT journaled: bundle=" + bundle.sha256() + " by " + actor.userId(), journalFailure);
        }
    }

    @Override
    public List<ConfigImportRecord> recentImports(AccessContext.User actor, int limit) {
        guard.requireTenantAdminOrApiService(actor);
        return journal.findRecent(tenantId, Math.max(1, Math.min(limit, 200)));
    }

    // ---------------------------------------------------------------- helpers

    private static ProposedWorkflowConfig toProposed(WorkflowConfigDoc d) {
        return new ProposedWorkflowConfig(d.label(), d.statusDefinitions(), d.allowedDossierStatuses(),
                d.onStartDossierStatus(), d.observerUserIds());
    }

    private static void requireSameKey(BundleEntry entry, String docIdentity) {
        if (!entry.key().equals(docIdentity)) {
            throw new InvalidBundleException(entry.type() + " file '" + entry.key() + "' describes '" + docIdentity
                    + "': the file name and its content must agree");
        }
    }

    private static boolean sameXml(byte[] a, byte[] b) {
        return normalize(a).equals(normalize(b));
    }

    private static String normalize(byte[] xml) {
        return new String(xml, StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
    }

    private static String cap(String text) {
        String t = text == null ? "" : text;
        return t.length() > MAX_REASON_CHARS ? t.substring(0, MAX_REASON_CHARS) + "…" : t;
    }

    /** Remplace {@code {{nom}}} dans les valeurs texte de la configuration d'un connecteur ; un paramètre absent bloque. */
    @SuppressWarnings("unchecked")
    private static ConnectorDoc substitute(ConnectorDoc doc, Map<String, String> parameters) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : doc.config().entrySet()) {
            out.put(e.getKey(), substituteValue(e.getValue(), parameters, doc.name()));
        }
        return new ConnectorDoc(doc.name(), doc.connectorType(), doc.enabled(), out, doc.secretFields());
    }

    @SuppressWarnings("unchecked")
    private static Object substituteValue(Object value, Map<String, String> parameters, String connectorName) {
        if (value instanceof String s) {
            Matcher m = PLACEHOLDER.matcher(s);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String replacement = parameters.get(m.group(1));
                if (replacement == null) {
                    throw new MissingParameterException("connector '" + connectorName + "' needs the environment parameter '"
                            + m.group(1) + "'");
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            return sb.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            ((Map<String, Object>) map).forEach((k, v) -> copy.put(k, substituteValue(v, parameters, connectorName)));
            return copy;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(v -> substituteValue(v, parameters, connectorName)).toList();
        }
        return value;
    }

    private static final class MissingParameterException extends RuntimeException {
        MissingParameterException(String message) {
            super(message);
        }
    }
}
