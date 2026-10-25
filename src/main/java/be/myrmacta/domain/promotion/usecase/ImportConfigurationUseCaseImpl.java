package be.myrmacta.domain.promotion.usecase;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import be.myrmacta.domain.dossier.port.in.CreateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.ConfigImportRecord;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.ImportOutcome;
import be.myrmacta.domain.promotion.model.ImportPlan;
import be.myrmacta.domain.promotion.model.ImportReport;
import be.myrmacta.domain.promotion.model.InvalidBundleException;
import be.myrmacta.domain.promotion.model.PlanAction;
import be.myrmacta.domain.promotion.model.PlanEntry;
import be.myrmacta.domain.promotion.model.ReportEntry;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.ConnectorDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.DossierTypeDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.TaskQuestionSchemaDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.WorkflowConfigDoc;
import be.myrmacta.domain.promotion.port.in.ImportConfigurationUseCase;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;
import be.myrmacta.domain.promotion.port.out.ConfigImportLogRepository;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.domain.workflow.model.ProposedWorkflowConfig;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.palette.InactiveBlockReference;
import be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase;
import be.myrmacta.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import be.myrmacta.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
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
        Set<String> names = connectorNames(bundle);
        Map<String, PlanEntry> byKey = new LinkedHashMap<>();
        List<BundleEntry> ordered = bundle.entries().stream()
                .sorted(Comparator.comparing((BundleEntry e) -> e.type().ordinal()).thenComparing(BundleEntry::key))
                .toList();
        List<PlanEntry> entries = new ArrayList<>();
        for (BundleEntry entry : ordered) {
            PlanEntry planned = switch (entry.type()) {
                case DOSSIER_TYPE -> planDossierType(entry);
                case TASK_QUESTION_SCHEMA -> planSchema(entry);
                case CONNECTOR_INSTANCE -> planConnector(entry, parameters, secrets, names);
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
        Set<String> names = new HashSet<>();
        for (BundleEntry e : bundle.of(ArtifactType.CONNECTOR_INSTANCE)) {
            ConnectorDoc doc = decode(e, ConnectorDoc.class);
            // Revue D4 M2 : un nom de connecteur est unique dans le bundle (deux fichiers homonymes créeraient deux instances)
            if (!names.add(doc.name().strip().toLowerCase(java.util.Locale.ROOT))) {
                throw new InvalidBundleException("two connector files describe the connector named '" + doc.name()
                        + "': names must be unique in a bundle");
            }
            for (Map.Entry<String, Object> field : doc.config().entrySet()) {
                boolean secretField = ExportConfigurationUseCaseImpl.isSecret(doc.connectorType(), field.getKey(), catalog)
                        && field.getValue() != null && !String.valueOf(field.getValue()).isBlank();
                if (secretField || ExportConfigurationUseCaseImpl.carriesSecret(field.getValue())) {
                    throw new InvalidBundleException("connector '" + doc.name() + "' carries a secret in '" + field.getKey()
                            + "' (a value, a nested secret field or credentials in a URL): a bundle never contains secrets (refused)");
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
            BundleEntry entry,
            Map<String, String> parameters,
            Map<String, Map<String, String>> secrets,
            Set<String> bundleNames) {
        ConnectorDoc raw = decode(entry, ConnectorDoc.class);
        ConnectorDoc doc;
        try {
            doc = substitute(raw, parameters);
        } catch (MissingParameterException e) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED, e.getMessage(), raw.secretFields(), raw.name());
        }
        List<ConnectorInstanceData> sameName = connectors.listByTenantId(tenantId).stream()
                .filter(c -> c.name().equals(doc.name()))
                .toList();
        if (sameName.size() > 1) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.CONFLICT,
                    sameName.size() + " connectors are named '" + doc.name() + "' on this instance: rename or delete the "
                            + "duplicates first", List.of(), doc.name());
        }
        if (sameName.size() == 1) {
            ConnectorInstanceData current = sameName.getFirst();
            if (!current.connectorType().equals(doc.connectorType())) {
                return new PlanEntry(entry.type(), entry.key(), PlanAction.CONFLICT,
                        "a connector named '" + doc.name() + "' exists with another type (" + current.connectorType() + ")",
                        List.of(), doc.name());
            }
            ConnectorDoc now = ExportConfigurationUseCaseImpl.connectorDoc(
                    current.name(), current.connectorType(), current.enabled(), current.config(), catalog);
            // « enabled » n'est jamais importé ; la mise à jour FUSIONNE (aucune clé retirée) : on ne compare donc que les clés du
            // bundle (revue D4 M4) — une clé en plus sur la cible ne produit pas un « modifié » permanent.
            Map<String, String> changes = new java.util.TreeMap<>();
            doc.config().forEach((k, v) -> {
                if (!java.util.Objects.equals(now.config().get(k), v)) {
                    changes.put(k, describeChange(now.config().get(k), v));
                }
            });
            if (changes.isEmpty()) {
                return new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "", List.of(), doc.name());
            }
            // Revue D4 M3 : le plan DIT ce qui change (valeurs non secrètes) et signale les adresses : un connecteur activé garde
            // ses secrets et les enverrait à une nouvelle adresse.
            boolean address = changes.keySet().stream().anyMatch(k -> isAddressKey(k, doc.config().get(k)));
            // L'avertissement en TÊTE : seule la liste des changements peut être tronquée (revue D4 M-B)
            String warning = address && current.enabled()
                    ? "WARNING: an address of an ENABLED connector changes, its secrets are kept and will be sent there. "
                    : "";
            String reason = warning + cap("configuration updated (enabled state and secrets kept): " + changes);
            return new PlanEntry(entry.type(), entry.key(), PlanAction.UPDATE, reason, List.of(), doc.name());
        }
        Map<String, String> provided = secretsFor(secrets, doc.name(), entry.key(), bundleNames);
        Map<String, Object> candidate = new LinkedHashMap<>(doc.config());
        provided.forEach(candidate::put);
        List<String> missing = doc.secretFields().stream().filter(f -> !provided.containsKey(f)).toList();
        try {
            catalog.get(doc.connectorType()).validate(candidate);
        } catch (RuntimeException e) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED,
                    cap("cannot be created: " + e.getMessage()), missing, doc.name());
        }
        return new PlanEntry(entry.type(), entry.key(), PlanAction.CREATE,
                "created disabled: review its endpoints, then enable it", missing, doc.name());
    }

    /** Secrets d'un connecteur : indexés par son nom exact ; la clé de fichier est acceptée aussi (revue D4 H1). */
    private static Map<String, String> secretsFor(
            Map<String, Map<String, String>> secrets, String name, String fileKey, Set<String> bundleNames) {
        Map<String, String> byName = secrets.get(name);
        if (byName != null) {
            return byName;
        }
        // le repli sur la clé de fichier ne vaut que si cette clé n'est pas le NOM d'un autre connecteur du bundle (revue D4 L-1)
        return bundleNames.contains(fileKey) ? Map.of() : secrets.getOrDefault(fileKey, Map.of());
    }

    private Set<String> connectorNames(ConfigurationBundle bundle) {
        Set<String> names = new HashSet<>();
        for (BundleEntry e : bundle.of(ArtifactType.CONNECTOR_INSTANCE)) {
            names.add(decode(e, ConnectorDoc.class).name());
        }
        return names;
    }

    private static String describeChange(Object before, Object after) {
        // jamais d'identifiants d'URL dans un plan ou un journal (revue D4 L-4)
        return (before == null ? "(absent)" : redact(String.valueOf(before))) + " → " + redact(String.valueOf(after));
    }

    private static String redact(String text) {
        return ExportConfigurationUseCaseImpl.URL_USERINFO.matcher(text).replaceAll("$1***@");
    }

    private static boolean isAddressKey(String key, Object value) {
        String k = key.toLowerCase(java.util.Locale.ROOT);
        return k.endsWith("url") || k.endsWith("uri") || k.endsWith("host") || k.contains("endpoint")
                || (value instanceof String s && (s.startsWith("http://") || s.startsWith("https://")));
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
        List<InactiveBlockReference> inactive;
        try {
            for (String decisionKey : bpmnParser.extractReferencedDecisionKeys(xml.getBytes(StandardCharsets.UTF_8))) {
                if (dmnDeployment.findDeploymentInfo(tenantId, decisionKey).isEmpty()
                        && bundle.find(ArtifactType.DMN, decisionKey).isPresent()) {
                    return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED, "depends on the decision table '"
                            + decisionKey + "', which is only a draft: publish it first, then run the import again");
                }
            }
            inactive = propose.validateBpmn(actor, entry.key(), xml, config);
        } catch (RuntimeException e) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED, cap(e.getMessage()));
        }
        String draftReason = "saved as a draft to publish" + inactiveNote(inactive);
        Optional<byte[]> deployed = workflowDeployment.findDeployedXml(tenantId, entry.key());
        if (deployed.isEmpty()) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.CREATE, draftReason);
        }
        return sameXml(deployed.get(), entry.content())
                ? new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "")
                : new PlanEntry(entry.type(), entry.key(), PlanAction.UPDATE, draftReason);
    }

    /**
     * TODO §93 (revue L2, D-DRAFT-REF-STATUS) — une ressource référencée présente mais inactive n'empêche pas le brouillon ;
     * le plan le dit, puisque la publication sera refusée tant qu'elle n'est pas activée.
     */
    private static String inactiveNote(List<InactiveBlockReference> inactive) {
        if (inactive.isEmpty()) {
            return "";
        }
        return cap("; publishing it will be refused until these referenced resources, which exist but are not active, "
                + "are activated: " + inactive.stream()
                        .map(r -> r.source() + " '" + r.value() + "'")
                        .distinct()
                        .collect(java.util.stream.Collectors.joining(", ")));
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
        Map<String, ImportOutcome> outcomes = new java.util.HashMap<>();
        for (PlanEntry planned : plan.entries()) {
            BundleEntry entry = bundle.find(planned.type(), planned.key()).orElseThrow();
            int before = report.size();
            switch (planned.action()) {
                case UNCHANGED -> report.add(new ReportEntry(planned.type(), planned.key(), ImportOutcome.UNCHANGED, ""));
                case CONFLICT, BLOCKED -> report.add(new ReportEntry(planned.type(), planned.key(), ImportOutcome.SKIPPED,
                        planned.reason()));
                case CREATE, UPDATE -> report.add(channel == Channel.MCP && !mcpMayApply(planned)
                        ? new ReportEntry(planned.type(), planned.key(), ImportOutcome.SKIPPED,
                                "not applied through MCP (only BPMN and DMN drafts are): use the admin import screen")
                        : applyOne(actor, planned, entry, bundle, plan, params, providedSecrets, outcomes));
            }
            outcomes.put(planned.type() + "/" + planned.key(), report.get(before).outcome());
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
            Map<String, Map<String, String>> secrets,
            Map<String, ImportOutcome> outcomes) {
        try {
            String message = switch (entry.type()) {
                case DOSSIER_TYPE -> applyDossierType(actor, entry, planned.action());
                case TASK_QUESTION_SCHEMA -> applySchema(actor, entry, planned.action());
                case CONNECTOR_INSTANCE -> applyConnector(actor, entry, planned.action(), params, secrets, connectorNames(bundle));
                case DMN -> applyDmn(actor, entry, bundle);
                case BPMN -> applyBpmn(actor, entry, bundle);
                case WORKFLOW_CONFIG -> applyWorkflowConfig(actor, entry, plan, outcomes);
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
                    d.retentionNever(), d.slaTargetHours(), d.documentTemplateKeys(), d.pieceTypes()), actor);
            return "created";
        }
        updateType.update(new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(tenantId, d.code(), d.label(), d.workflowKey(),
                d.active(), d.allowUnknownFields(), d.fieldDefinitions(), d.retentionDays(), d.retentionNever(),
                d.slaTargetHours(), d.documentTemplateKeys(), d.pieceTypes()), actor);
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
            Map<String, Map<String, String>> secrets,
            Set<String> bundleNames) {
        ConnectorDoc doc = substitute(decode(entry, ConnectorDoc.class), params);
        Optional<ConnectorInstanceData> current = connectors.listByTenantId(tenantId).stream()
                .filter(c -> c.name().equals(doc.name()))
                .findFirst();
        if (action == PlanAction.CREATE) {
            Map<String, Object> config = new LinkedHashMap<>(doc.config());
            secretsFor(secrets, doc.name(), entry.key(), bundleNames).forEach(config::put);
            // désactivé : ses URLs viennent d'un autre environnement (D-PROMO-CONNECTOR-DISABLED)
            manageConnector.create(new ManageConnectorInstanceUseCase.CreateCommand(
                    tenantId, doc.name(), doc.connectorType(), false, config), actor);
            return "created disabled — review its endpoints, then enable it";
        }
        ConnectorInstanceData target = current.orElseThrow();
        manageConnector.update(new ManageConnectorInstanceUseCase.UpdateCommand(
                target.id(), tenantId, null, null, keepNestedSecrets(target.config(), doc.config()), false), actor);
        return "configuration updated (enabled state and secrets kept)";
    }

    /**
     * La mise à jour fusionne au premier niveau seulement : une table imbriquée du bundle REMPLACE celle de la cible. On y
     * réinjecte les champs secrets imbriqués de la cible, sinon « secrets conservés » serait faux (revue D4 M-A).
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> keepNestedSecrets(Map<String, Object> current, Map<String, Object> incoming) {
        Map<String, Object> out = new LinkedHashMap<>(incoming);
        for (Map.Entry<String, Object> e : incoming.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> in && current.get(e.getKey()) instanceof Map<?, ?> cur) {
                Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) in);
                for (Map.Entry<String, Object> c : ((Map<String, Object>) cur).entrySet()) {
                    if (be.myrmacta.domain.connector.usecase.ManageConnectorInstanceUseCaseImpl.isSecretKey(c.getKey())
                            && !merged.containsKey(c.getKey())) {
                        merged.put(c.getKey(), c.getValue());
                    }
                }
                out.put(e.getKey(), keepNestedSecrets((Map<String, Object>) cur, merged));
            }
        }
        return out;
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

    private String applyWorkflowConfig(
            AccessContext.User actor, BundleEntry entry, ImportPlan plan, Map<String, ImportOutcome> outcomes) {
        PlanEntry planned = plan.find(entry.type(), entry.key()).orElseThrow();
        if (planned.reason().startsWith("joined to the BPMN draft")) {
            // Revue D4 L3 : « appliquée » seulement si le brouillon BPMN qui la porte a bien été enregistré
            if (outcomes.get(ArtifactType.BPMN + "/" + entry.key()) != ImportOutcome.APPLIED) {
                throw new IllegalStateException("its BPMN draft was not saved, so the configuration was not applied");
            }
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
            if (e.getValue() != null) {
                out.put(e.getKey(), ExportConfigurationUseCaseImpl.normalizeValue(substituteValue(e.getValue(), parameters, doc.name())));
            }
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
