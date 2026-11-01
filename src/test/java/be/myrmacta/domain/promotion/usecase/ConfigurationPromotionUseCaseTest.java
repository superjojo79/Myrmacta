package be.myrmacta.domain.promotion.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import be.myrmacta.domain.dossier.port.in.CreateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.promotion.artifact.AdditionalArtifacts;
import be.myrmacta.domain.promotion.artifact.GroupReferenceCheck;
import be.myrmacta.domain.promotion.artifact.UserGroupArtifacts;
import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.ConfigImportRecord;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.ImportOutcome;
import be.myrmacta.domain.promotion.model.InvalidBundleException;
import be.myrmacta.domain.promotion.model.PlanAction;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.ConnectorDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.DossierTypeDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.TaskQuestionSchemaDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.UserGroupDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.WorkflowConfigDoc;
import be.myrmacta.domain.promotion.port.in.ImportConfigurationUseCase.Channel;
import be.myrmacta.domain.promotion.port.out.ConfigImportLogRepository;
import be.myrmacta.domain.promotion.port.out.GroupReferenceExtractor;
import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.model.StatusOutcome;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase;
import be.myrmacta.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import be.myrmacta.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.infrastructure.promotion.JsonArtifactCodec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §83 (D4) — règles du plan et de l'application d'un bundle, avec des ports simulés. */
class ConfigurationPromotionUseCaseTest {

    private static final String TENANT = "tenant_dev";
    private static final AccessContext.User ADMIN = AccessContext.user("alice", Set.of("TENANT_ADMIN"));
    private static final AccessContext.User VIEWER = AccessContext.user("bob", Set.of("DOSSIER_VIEWER"));
    private static final JsonArtifactCodec CODEC = new JsonArtifactCodec();
    private static final WorkflowConfigDoc WF = new WorkflowConfigDoc("wf", "Flow",
            List.of(new WorkflowStatusDefinition("S", "S", true, StatusOutcome.NONE)), List.of(DossierStatus.OUVERT), null, List.of());

    private DossierTypeRepository types;
    private CreateDossierTypeUseCase createType;
    private UpdateDossierTypeUseCase updateType;
    private CheckDossierTypeSchemaCompatibilityUseCase compat;
    private TaskQuestionSchemaRepository schemas;
    private SaveTaskQuestionSchemaUseCase saveSchema;
    private ConnectorInstanceRepository connectors;
    private ManageConnectorInstanceUseCase manageConnector;
    private ConnectorCatalog catalog;
    private DmnDeploymentPort dmnDeployment;
    private WorkflowDeploymentPort workflowDeployment;
    private BpmnParserPort bpmnParser;
    private WorkflowTypeConfigRepository workflowConfigs;
    private SaveWorkflowTypeConfigUseCase saveWorkflowConfig;
    private ProposeDraftUseCase propose;
    private final List<ConfigImportRecord> journal = new ArrayList<>();
    private ConfigImportLogRepository journalRepo;
    private ImportConfigurationUseCaseImpl useCase;
    private UserGroupsUseCase userGroups;
    private final List<UserGroup> groupsHere = new ArrayList<>();
    private GroupReferenceExtractor groupRefs;

    @BeforeEach
    void setUp() {
        types = mock(DossierTypeRepository.class);
        createType = mock(CreateDossierTypeUseCase.class);
        updateType = mock(UpdateDossierTypeUseCase.class);
        compat = mock(CheckDossierTypeSchemaCompatibilityUseCase.class);
        schemas = mock(TaskQuestionSchemaRepository.class);
        saveSchema = mock(SaveTaskQuestionSchemaUseCase.class);
        connectors = mock(ConnectorInstanceRepository.class);
        manageConnector = mock(ManageConnectorInstanceUseCase.class);
        catalog = mock(ConnectorCatalog.class);
        ConnectorDefinition definition = mock(ConnectorDefinition.class);
        when(catalog.get(any())).thenReturn(definition);
        when(catalog.isPasswordField(any(), any())).thenCallRealMethod();
        dmnDeployment = mock(DmnDeploymentPort.class);
        workflowDeployment = mock(WorkflowDeploymentPort.class);
        bpmnParser = mock(BpmnParserPort.class);
        workflowConfigs = mock(WorkflowTypeConfigRepository.class);
        saveWorkflowConfig = mock(SaveWorkflowTypeConfigUseCase.class);
        propose = mock(ProposeDraftUseCase.class);
        journalRepo = mock(ConfigImportLogRepository.class);
        when(journalRepo.save(any())).thenAnswer(inv -> {
            journal.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(connectors.listByTenantId(TENANT)).thenReturn(List.of());
        when(types.findByCode(any(), any())).thenReturn(Optional.empty());
        when(schemas.findByKey(any(), any())).thenReturn(Optional.empty());
        when(workflowConfigs.findByWorkflowKey(any(), any())).thenReturn(Optional.empty());
        when(dmnDeployment.findDeployedXml(any(), any())).thenReturn(Optional.empty());
        when(workflowDeployment.findDeployedXml(any(), any())).thenReturn(Optional.empty());
        when(bpmnParser.extractReferencedDecisionKeys(any())).thenReturn(Set.of());
        userGroups = mock(UserGroupsUseCase.class);
        when(userGroups.list()).thenAnswer(inv -> List.copyOf(groupsHere));
        when(userGroups.get(any())).thenAnswer(inv -> groupsHere.stream()
                .filter(g -> g.code().equals(inv.getArgument(0))).findFirst().orElseThrow());
        groupRefs = mock(GroupReferenceExtractor.class);
        when(groupRefs.fromBpmn(any())).thenReturn(Set.of());
        when(groupRefs.fromDmn(any())).thenReturn(Set.of());
        useCase = new ImportConfigurationUseCaseImpl(TENANT, Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC),
                new TenantAdminGuard(), CODEC, types, createType, updateType, compat, schemas, saveSchema, connectors,
                manageConnector, catalog, dmnDeployment, mock(DmnParserPort.class), workflowDeployment, bpmnParser, workflowConfigs,
                saveWorkflowConfig, propose, journalRepo,
                new AdditionalArtifacts(new UserGroupArtifacts(userGroups, CODEC), new GroupReferenceCheck(groupRefs)));
    }

    private static BundleEntry json(ArtifactType type, String key, Object doc) {
        return new BundleEntry(type, key, CODEC.encode(doc));
    }

    private static ConfigurationBundle bundle(BundleEntry... entries) {
        return new ConfigurationBundle(2, Instant.parse("2026-10-01T09:00:00Z"), "recette", "t", List.of(entries));
    }

    private static DossierTypeDoc typeDoc(String code) {
        return new DossierTypeDoc(code, "L", "wf", true, false, List.of(), null, false, null, List.of());
    }

    @Test
    void aTypeThatWouldBreakExistingDossiersIsAConflictAndNeverForced() {
        when(types.findByCode(TENANT, "T1")).thenReturn(Optional.of(new be.myrmacta.domain.dossier.model.DossierType(
                java.util.UUID.randomUUID(), TENANT, "T1", "Old", "wf", true, false, List.of(), null, false, null, "t", "t")));
        when(compat.check(any())).thenReturn(new CheckDossierTypeSchemaCompatibilityUseCase.Result(3, List.of("DOS-1")));
        var b = bundle(json(ArtifactType.DOSSIER_TYPE, "T1", typeDoc("T1")));

        assertThat(useCase.plan(ADMIN, b, Map.of()).entries().getFirst().action()).isEqualTo(PlanAction.CONFLICT);
        var report = useCase.apply(ADMIN, b, Map.of(), Map.of(), b.sha256(), Channel.ADMIN);

        assertThat(report.entries().getFirst().outcome()).isEqualTo(ImportOutcome.SKIPPED);
        verify(updateType, never()).update(any(), any());
    }

    @Test
    void anIdenticalTypeIsUnchangedSoReplayingDoesNothing() {
        var doc = typeDoc("T1");
        when(types.findByCode(TENANT, "T1")).thenReturn(Optional.of(new be.myrmacta.domain.dossier.model.DossierType(
                java.util.UUID.randomUUID(), TENANT, "T1", "L", "wf", true, false, List.of(), null, false, null, "t", "t")));
        var b = bundle(json(ArtifactType.DOSSIER_TYPE, "T1", doc));

        assertThat(useCase.plan(ADMIN, b, Map.of()).entries().getFirst().action()).isEqualTo(PlanAction.UNCHANGED);
        useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);
        verify(createType, never()).create(any(), any());
        verify(updateType, never()).update(any(), any());
    }

    @Test
    void aFileWhoseNameDisagreesWithItsContentIsRefused() {
        var b = bundle(json(ArtifactType.DOSSIER_TYPE, "OTHER", typeDoc("T1")));
        assertThatThrownBy(() -> useCase.plan(ADMIN, b, Map.of())).isInstanceOf(InvalidBundleException.class)
                .hasMessageContaining("agree");
    }

    @Test
    void aBpmnDependingOnAnUnpublishedDmnDraftIsBlockedWithTheOrderToFollow() {
        when(bpmnParser.extractReferencedDecisionKeys(any())).thenReturn(Set.of("grid"));
        when(dmnDeployment.findDeploymentInfo(TENANT, "grid")).thenReturn(Optional.empty());
        var b = bundle(
                new BundleEntry(ArtifactType.DMN, "grid", "<dmn/>".getBytes(StandardCharsets.UTF_8)),
                new BundleEntry(ArtifactType.BPMN, "wf", "<bpmn/>".getBytes(StandardCharsets.UTF_8)),
                json(ArtifactType.WORKFLOW_CONFIG, "wf", WF));

        var plan = useCase.plan(ADMIN, b, Map.of());

        var bpmn = plan.find(ArtifactType.BPMN, "wf").orElseThrow();
        assertThat(bpmn.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(bpmn.reason()).contains("grid").contains("publish it first");
        // la configuration n'est jamais appliquée seule sur l'ancien diagramme
        assertThat(plan.find(ArtifactType.WORKFLOW_CONFIG, "wf").orElseThrow().action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(plan.find(ArtifactType.DMN, "grid").orElseThrow().action()).isEqualTo(PlanAction.CREATE);
    }

    @Test
    void aBpmnWithoutAnyWorkflowConfigurationIsBlocked() {
        var b = bundle(new BundleEntry(ArtifactType.BPMN, "wf", "<bpmn/>".getBytes(StandardCharsets.UTF_8)));
        var entry = useCase.plan(ADMIN, b, Map.of()).entries().getFirst();
        assertThat(entry.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(entry.reason()).contains("no workflow configuration");
    }

    /** Revue adversariale TODO §93 (L2) : le plan annonce qu'une ressource référencée est encore inactive. */
    @Test
    void thePlanOfABpmnReferencingAnInactiveResourceSaysPublicationWaitsForItsActivation() {
        when(propose.validateBpmn(any(), org.mockito.ArgumentMatchers.eq("wf"), any(), any())).thenReturn(List.of(
                new be.myrmacta.domain.workflow.palette.InactiveBlockReference(
                        "generer", "documentType", "documentTemplates", "decision")));
        var b = bundle(new BundleEntry(ArtifactType.BPMN, "wf", "<bpmn/>".getBytes(StandardCharsets.UTF_8)),
                json(ArtifactType.WORKFLOW_CONFIG, "wf", WF));

        var bpmn = useCase.plan(ADMIN, b, Map.of()).find(ArtifactType.BPMN, "wf").orElseThrow();

        assertThat(bpmn.action()).isEqualTo(PlanAction.CREATE);
        assertThat(bpmn.reason()).contains("saved as a draft to publish").contains("decision").contains("not active");
    }

    @Test
    void aBpmnDraftCarriesItsConfigurationAndTheConfigIsNotAppliedTwice() {
        var b = bundle(new BundleEntry(ArtifactType.BPMN, "wf", "<bpmn/>".getBytes(StandardCharsets.UTF_8)),
                json(ArtifactType.WORKFLOW_CONFIG, "wf", WF));

        var report = useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);

        verify(propose).proposeBpmn(any(), org.mockito.ArgumentMatchers.eq(be.myrmacta.domain.workflow.model.Provenance.IMPORT),
                org.mockito.ArgumentMatchers.eq("wf"), any(), org.mockito.ArgumentMatchers.argThat(c -> c != null && "Flow".equals(c.label())),
                anyBoolean());
        verify(saveWorkflowConfig, never()).create(any(), any());
        assertThat(report.entries()).extracting(e -> e.outcome()).containsOnly(ImportOutcome.APPLIED);
    }

    @Test
    void aStandaloneConfigurationOnAnAlreadyDeployedDiagramIsAppliedDirectly() {
        when(workflowDeployment.findDeployedXml(TENANT, "wf")).thenReturn(Optional.of("<bpmn/>".getBytes(StandardCharsets.UTF_8)));
        when(workflowConfigs.findByWorkflowKey(TENANT, "wf")).thenReturn(Optional.empty());
        var b = bundle(new BundleEntry(ArtifactType.BPMN, "wf", "<bpmn/>".getBytes(StandardCharsets.UTF_8)),
                json(ArtifactType.WORKFLOW_CONFIG, "wf", WF));

        var plan = useCase.plan(ADMIN, b, Map.of());
        assertThat(plan.find(ArtifactType.BPMN, "wf").orElseThrow().action()).isEqualTo(PlanAction.UNCHANGED);
        assertThat(plan.find(ArtifactType.WORKFLOW_CONFIG, "wf").orElseThrow().action()).isEqualTo(PlanAction.CREATE);

        useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);
        verify(saveWorkflowConfig).create(any(), any());
        verify(propose, never()).proposeBpmn(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void aFailureOnOneArtifactDoesNotStopTheOthersAndIsReported() {
        when(createType.create(any(), any())).thenThrow(new IllegalStateException("workflowKey unknown"));
        var b = bundle(json(ArtifactType.DOSSIER_TYPE, "T1", typeDoc("T1")),
                json(ArtifactType.TASK_QUESTION_SCHEMA, "q1", new TaskQuestionSchemaDoc("q1", "Q",
                        List.of(new be.myrmacta.domain.dossier.model.FieldDefinition("x", "X",
                                be.myrmacta.domain.dossier.model.FieldType.STRING, false, null, null, null, false)))));

        var report = useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);

        assertThat(report.entries().get(0).outcome()).isEqualTo(ImportOutcome.FAILED);
        assertThat(report.entries().get(0).message()).contains("workflowKey unknown");
        assertThat(report.entries().get(1).outcome()).isEqualTo(ImportOutcome.APPLIED);
        verify(saveSchema).save(any(), any());
        assertThat(journal).hasSize(1);
        assertThat(journal.getFirst().failed()).isEqualTo(1);
        assertThat(journal.getFirst().applied()).isEqualTo(1);
    }

    @Test
    void aMissingEnvironmentParameterBlocksTheConnectorAndASecretNeverReachesTheJournal() {
        var doc = new ConnectorDoc("C", "http-rest", true, Map.of("processKey", "{{pk}}"), List.of("webhookSecret"));
        var b = bundle(json(ArtifactType.CONNECTOR_INSTANCE, "c", doc));

        var blocked = useCase.plan(ADMIN, b, Map.of()).entries().getFirst();
        assertThat(blocked.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(blocked.reason()).contains("pk");
        assertThat(blocked.secretsRequired()).containsExactly("webhookSecret");

        var report = useCase.apply(ADMIN, b, Map.of("C", Map.of("webhookSecret", "TOP-SECRET-VALUE")), Map.of("pk", "k1"), null,
                Channel.ADMIN);
        assertThat(report.entries().getFirst().outcome()).isEqualTo(ImportOutcome.APPLIED);
        var captor = org.mockito.ArgumentCaptor.forClass(ManageConnectorInstanceUseCase.CreateCommand.class);
        verify(manageConnector).create(captor.capture(), any());
        assertThat(captor.getValue().enabled()).as("créé désactivé").isFalse();
        assertThat(captor.getValue().config()).containsEntry("processKey", "k1").containsEntry("webhookSecret", "TOP-SECRET-VALUE");
        assertThat(journal.getFirst().reportJson()).doesNotContain("TOP-SECRET-VALUE");
    }

    @Test
    void aJournalFailureNeverCancelsAnImportThatHappened() {
        when(journalRepo.save(any())).thenThrow(new IllegalStateException("db down"));
        var b = bundle(json(ArtifactType.DOSSIER_TYPE, "T1", typeDoc("T1")));
        var report = useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);
        assertThat(report.entries().getFirst().outcome()).isEqualTo(ImportOutcome.APPLIED);
    }

    @Test
    void onlyAnAdministratorMayPlanApplyOrReadTheJournal() {
        var b = bundle(json(ArtifactType.DOSSIER_TYPE, "T1", typeDoc("T1")));
        assertThatThrownBy(() -> useCase.plan(VIEWER, b, Map.of())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> useCase.apply(VIEWER, b, Map.of(), Map.of(), null, Channel.ADMIN)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> useCase.recentImports(VIEWER, 10)).isInstanceOf(AccessDeniedException.class);
        verify(createType, never()).create(any(), any());
    }

    @Test
    void bundleInvariantsAreEnforced() {
        assertThatThrownBy(() -> new ConfigurationBundle(3, Instant.now(), "", "", List.of())).hasMessageContaining("unsupported");
        assertThatThrownBy(() -> new ConfigurationBundle(0, Instant.now(), "", "", List.of())).hasMessageContaining("unsupported");
        // TODO §117 (D-PROMO-FORMAT2) — un bundle de format 1 ne porte que les artefacts de la première tranche
        assertThat(new ConfigurationBundle(2, Instant.now(), "", "", List.of()).formatVersion()).isEqualTo(2);
        assertThatThrownBy(() -> new ConfigurationBundle(1, Instant.now(), "", "", List.of(json(ArtifactType.USER_GROUP, "COMPTA",
                new UserGroupDoc("COMPTA", "Comptabilité", null))))).hasMessageContaining("formatVersion 1 cannot contain USER_GROUP");
        assertThatThrownBy(() -> bundle(json(ArtifactType.DOSSIER_TYPE, "T1", typeDoc("T1")), json(ArtifactType.DOSSIER_TYPE, "T1", typeDoc("T1"))))
                .hasMessageContaining("duplicate");
        for (String bad : List.of("../x", "a/b", ".hidden", "a..b", "", "x".repeat(200), "a b")) {
            assertThatThrownBy(() -> new BundleEntry(ArtifactType.DMN, bad, new byte[0])).isInstanceOf(InvalidBundleException.class);
        }
    }

    // ---- corrections de la revue adversariale D4 ----

    private static be.myrmacta.domain.connector.model.ConnectorInstanceData existing(String name, boolean enabled, Map<String, Object> config) {
        return new be.myrmacta.domain.connector.model.ConnectorInstanceData(java.util.UUID.randomUUID(), TENANT, name, "http-rest", enabled, config);
    }

    @Test
    void secretsAreIndexedByTheExactConnectorNameAndTheFileKeyIsAcceptedToo_reviewD4H1() {
        var doc = new ConnectorDoc("ERP Prod", "http-rest", true, Map.of("processKey", "p"), List.of("webhookSecret"));
        var b = bundle(json(ArtifactType.CONNECTOR_INSTANCE, "erp-prod", doc));

        var planned = useCase.plan(ADMIN, b, Map.of()).entries().getFirst();
        assertThat(planned.connectorName()).isEqualTo("ERP Prod");
        assertThat(planned.key()).isEqualTo("erp-prod");

        useCase.apply(ADMIN, b, Map.of("ERP Prod", Map.of("webhookSecret", "s1")), Map.of(), null, Channel.ADMIN);
        var byName = org.mockito.ArgumentCaptor.forClass(ManageConnectorInstanceUseCase.CreateCommand.class);
        verify(manageConnector).create(byName.capture(), any());
        assertThat(byName.getValue().config()).containsEntry("webhookSecret", "s1");

        org.mockito.Mockito.clearInvocations(manageConnector);
        useCase.apply(ADMIN, b, Map.of("erp-prod", Map.of("webhookSecret", "s2")), Map.of(), null, Channel.ADMIN);
        var byKey = org.mockito.ArgumentCaptor.forClass(ManageConnectorInstanceUseCase.CreateCommand.class);
        verify(manageConnector).create(byKey.capture(), any());
        assertThat(byKey.getValue().config()).containsEntry("webhookSecret", "s2");
    }

    @Test
    void aNullConfigurationValueNeverBreaksTheDocument_reviewD4M1() {
        var config = new java.util.HashMap<String, Object>();
        config.put("url", null);
        config.put("processKey", "p");
        var doc = ExportConfigurationUseCaseImpl.connectorDoc("n", "http-rest", true, config, catalog);
        assertThat(doc.config()).containsOnlyKeys("processKey");
        assertThat(new ConnectorDoc("n", "http-rest", true, config, List.of()).config()).containsOnlyKeys("processKey");
    }

    @Test
    void duplicateConnectorNamesInABundleOrOnTheTargetAreRefusedOrConflicting_reviewD4M2() {
        var a = new ConnectorDoc("Same", "http-rest", true, Map.of("processKey", "a"), List.of());
        var b = new ConnectorDoc("same ", "http-rest", true, Map.of("processKey", "b"), List.of());
        assertThatThrownBy(() -> useCase.plan(ADMIN, bundle(json(ArtifactType.CONNECTOR_INSTANCE, "a", a),
                json(ArtifactType.CONNECTOR_INSTANCE, "b", b)), Map.of()))
                .isInstanceOf(InvalidBundleException.class).hasMessageContaining("unique");

        when(connectors.listByTenantId(TENANT)).thenReturn(List.of(existing("Same", false, Map.of()), existing("Same", true, Map.of())));
        var entry = useCase.plan(ADMIN, bundle(json(ArtifactType.CONNECTOR_INSTANCE, "a", a)), Map.of()).entries().getFirst();
        assertThat(entry.action()).isEqualTo(PlanAction.CONFLICT);
        assertThat(entry.reason()).contains("2 connectors are named");
    }

    @Test
    void anUpdatePlanSaysWhatChangesAndWarnsWhenAnEnabledConnectorsAddressMoves_reviewD4M3() {
        when(connectors.listByTenantId(TENANT)).thenReturn(List.of(existing("ERP", true,
                Map.of("processKey", "p", "baseUrl", "https://recette.example"))));
        var doc = new ConnectorDoc("ERP", "http-rest", true, Map.of("processKey", "p", "baseUrl", "https://evil.example"), List.of());

        var entry = useCase.plan(ADMIN, bundle(json(ArtifactType.CONNECTOR_INSTANCE, "erp", doc)), Map.of()).entries().getFirst();

        assertThat(entry.action()).isEqualTo(PlanAction.UPDATE);
        assertThat(entry.reason()).contains("https://recette.example → https://evil.example").contains("WARNING").contains("secrets");
    }

    @Test
    void aTargetKeyAbsentFromTheBundleDoesNotMakeAConnectorPermanentlyModified_reviewD4M4() {
        when(connectors.listByTenantId(TENANT)).thenReturn(List.of(existing("ERP", true,
                Map.of("processKey", "p", "legacy", "x"))));
        var doc = new ConnectorDoc("ERP", "http-rest", true, Map.of("processKey", "p"), List.of());
        assertThat(useCase.plan(ADMIN, bundle(json(ArtifactType.CONNECTOR_INSTANCE, "erp", doc)), Map.of()).entries().getFirst().action())
                .isEqualTo(PlanAction.UNCHANGED);
    }

    @Test
    void nestedSecretsAndCredentialsInUrlsAreRefusedOnImportAndStrippedOnExport_reviewD4L1() {
        var nested = new ConnectorDoc("N", "http-rest", true, Map.of("headers", Map.of("password", "x")), List.of());
        assertThatThrownBy(() -> useCase.plan(ADMIN, bundle(json(ArtifactType.CONNECTOR_INSTANCE, "n", nested)), Map.of()))
                .isInstanceOf(InvalidBundleException.class).hasMessageContaining("never contains secrets");
        var userinfo = new ConnectorDoc("U", "http-rest", true, Map.of("baseUrl", "https://user:pa55@host.example/x"), List.of());
        assertThatThrownBy(() -> useCase.plan(ADMIN, bundle(json(ArtifactType.CONNECTOR_INSTANCE, "u", userinfo)), Map.of()))
                .isInstanceOf(InvalidBundleException.class);

        var exported = ExportConfigurationUseCaseImpl.connectorDoc("U", "http-rest", true, Map.of(
                "baseUrl", "https://user:pa55@host.example/x",
                "headers", Map.of("password", "x", "accept", "json", "list", List.of("a", Map.of("secret", "z")))), catalog);
        assertThat(exported.config().toString()).doesNotContain("pa55").doesNotContain("password").doesNotContain("secret=z");
        assertThat(exported.config()).containsEntry("baseUrl", "https://host.example/x");
    }

    @Test
    void aConfigurationJoinedToAFailedBpmnDraftIsNotReportedAsApplied_reviewD4L3() {
        when(propose.proposeBpmn(any(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new be.myrmacta.domain.workflow.model.DraftConflictException("a human is editing it"));
        var b = bundle(new BundleEntry(ArtifactType.BPMN, "wf", "<bpmn/>".getBytes(StandardCharsets.UTF_8)),
                json(ArtifactType.WORKFLOW_CONFIG, "wf", WF));

        var report = useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);

        assertThat(report.entries().get(0).outcome()).isEqualTo(ImportOutcome.FAILED);
        assertThat(report.entries().get(1).type()).isEqualTo(ArtifactType.WORKFLOW_CONFIG);
        assertThat(report.entries().get(1).outcome()).isEqualTo(ImportOutcome.FAILED);
        assertThat(report.entries().get(1).message()).contains("not applied");
    }

    // ---- seconde passe de la revue D4 ----

    @Test
    void anUpdateKeepsTheNestedSecretsOfTheTarget_reviewD4MA() {
        when(connectors.listByTenantId(TENANT)).thenReturn(List.of(existing("ERP", true,
                Map.of("auth", Map.of("user", "x", "clientSecret", "S3CR3T"), "processKey", "p"))));
        var doc = new ConnectorDoc("ERP", "http-rest", true, Map.of("auth", Map.of("user", "y"), "processKey", "p"), List.of());
        var b = bundle(json(ArtifactType.CONNECTOR_INSTANCE, "erp", doc));

        useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);

        var captor = org.mockito.ArgumentCaptor.forClass(ManageConnectorInstanceUseCase.UpdateCommand.class);
        verify(manageConnector).update(captor.capture(), any());
        @SuppressWarnings("unchecked")
        var auth = (Map<String, Object>) captor.getValue().config().get("auth");
        assertThat(auth).containsEntry("user", "y").containsEntry("clientSecret", "S3CR3T");
    }

    @Test
    void theWarningAboutAnEnabledConnectorsAddressSurvivesTruncation_reviewD4MB() {
        String longValue = "https://recette.example/" + "x".repeat(300);
        when(connectors.listByTenantId(TENANT)).thenReturn(List.of(existing("ERP", true, Map.of("baseUrl", longValue))));
        var doc = new ConnectorDoc("ERP", "http-rest", true, Map.of("baseUrl", "https://prod.example/" + "y".repeat(300)), List.of());

        var entry = useCase.plan(ADMIN, bundle(json(ArtifactType.CONNECTOR_INSTANCE, "erp", doc)), Map.of()).entries().getFirst();

        assertThat(entry.reason()).startsWith("WARNING");
        assertThat(entry.reason().length()).isLessThan(900);
    }

    @Test
    void onlyRealUrlCredentialsAreStrippedOrRefused_reviewD4MC() {
        for (String fine : List.of("https://host.example?email=a@b.fr", "https://host.example/path#a@b", "ssh://git@github.com/org/repo",
                "contact@example.com", "https://host.example/a@b")) {
            var exported = ExportConfigurationUseCaseImpl.connectorDoc("U", "http-rest", true, Map.of("baseUrl", fine), catalog);
            assertThat(exported.config()).as(fine).containsEntry("baseUrl", fine);
            var b = bundle(json(ArtifactType.CONNECTOR_INSTANCE, "u", new ConnectorDoc("U", "http-rest", true, Map.of("baseUrl", fine), List.of())));
            useCase.plan(ADMIN, b, Map.of()); // accepté
        }
        for (String leaky : List.of("https://user:pa55@host.example/x", "ftp://:pa55@host.example")) {
            var b = bundle(json(ArtifactType.CONNECTOR_INSTANCE, "u", new ConnectorDoc("U", "http-rest", true, Map.of("baseUrl", leaky), List.of())));
            assertThatThrownBy(() -> useCase.plan(ADMIN, b, Map.of())).as(leaky).isInstanceOf(InvalidBundleException.class);
        }
    }

    @Test
    void theFileKeyFallbackNeverSendsSecretsToAnotherConnectorsName_reviewD4L1() {
        var alpha = new ConnectorDoc("Alpha Bot", "http-rest", true, Map.of("processKey", "a"), List.of("webhookSecret"));
        var beta = new ConnectorDoc("alpha-bot", "http-rest", true, Map.of("processKey", "b"), List.of("webhookSecret"));
        var b = bundle(json(ArtifactType.CONNECTOR_INSTANCE, "alpha-bot", alpha), json(ArtifactType.CONNECTOR_INSTANCE, "alpha-bot-2", beta));

        useCase.apply(ADMIN, b, Map.of("alpha-bot", Map.of("webhookSecret", "FOR-BETA")), Map.of(), null, Channel.ADMIN);

        var captor = org.mockito.ArgumentCaptor.forClass(ManageConnectorInstanceUseCase.CreateCommand.class);
        verify(manageConnector, org.mockito.Mockito.atLeastOnce()).create(captor.capture(), any());
        var forAlpha = captor.getAllValues().stream().filter(c -> c.name().equals("Alpha Bot")).toList();
        var forBeta = captor.getAllValues().stream().filter(c -> c.name().equals("alpha-bot")).toList();
        assertThat(forBeta).hasSize(1);
        assertThat(forBeta.getFirst().config()).containsEntry("webhookSecret", "FOR-BETA");
        assertThat(forAlpha).as("Alpha Bot ne reçoit jamais le secret destiné à un autre nom")
                .allSatisfy(c -> assertThat(c.config()).doesNotContainKey("webhookSecret"));
    }

    @Test
    void urlCredentialsNeverAppearInAPlanReason_reviewD4L4() {
        when(connectors.listByTenantId(TENANT)).thenReturn(List.of(existing("ERP", false, Map.of("baseUrl", "https://old.example"))));
        var doc = new ConnectorDoc("ERP", "http-rest", true, Map.of("baseUrl", "{{u}}"), List.of());
        var entry = useCase.plan(ADMIN, bundle(json(ArtifactType.CONNECTOR_INSTANCE, "erp", doc)),
                Map.of("u", "https://bob:hunter2@new.example")).entries().getFirst();
        assertThat(entry.reason()).doesNotContain("hunter2");
    }

    @Test
    void aForgedConnectorFileWithoutANameIsRefused_reviewD4L4() {
        var forged = new BundleEntry(ArtifactType.CONNECTOR_INSTANCE, "x",
                "{\"config\":{},\"connectorType\":\"http-rest\",\"enabled\":true,\"secretFields\":[]}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> useCase.plan(ADMIN, bundle(forged), Map.of())).isInstanceOf(InvalidBundleException.class);
    }

    // ---- TODO §117 (D-PROMO-GROUP, D-PROMO-ORPHAN) — groupes d'utilisateurs ----

    private static BundleEntry group(String code, String label, String idpGroup) {
        return json(ArtifactType.USER_GROUP, code, new UserGroupDoc(code, label, idpGroup));
    }

    @Test
    void aGroupIsCreatedWithoutMembersAndItsIdentityProviderGroupComesFromAParameter() {
        var b = bundle(group("COMPTA", "Comptabilité", "{{groupe_compta}}"));

        var blocked = useCase.plan(ADMIN, b, Map.of()).entries().getFirst();
        assertThat(blocked.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(blocked.reason()).contains("user group 'COMPTA'").contains("groupe_compta");

        var report = useCase.apply(ADMIN, b, Map.of(), Map.of("groupe_compta", "sg-compta-prod"), b.sha256(), Channel.ADMIN);

        assertThat(report.entries().getFirst().outcome()).isEqualTo(ImportOutcome.APPLIED);
        verify(userGroups).create("COMPTA", "Comptabilité", "sg-compta-prod");
    }

    @Test
    void anExistingGroupOnlyChangesItsLabel_itsIdentityProviderGroupAndMembersAreKept() {
        groupsHere.add(new UserGroup(TENANT, "COMPTA", "Compta", "sg-compta-prod", Set.of("u1")));
        var b = bundle(group("COMPTA", "Comptabilité", "sg-compta-recette"));

        var entry = useCase.plan(ADMIN, b, Map.of()).entries().getFirst();
        assertThat(entry.action()).isEqualTo(PlanAction.UPDATE);
        assertThat(entry.reason()).contains("'sg-compta-prod' here").contains("'sg-compta-recette' in the bundle");

        useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);
        verify(userGroups).update("COMPTA", "Comptabilité", "sg-compta-prod");
        verify(userGroups, never()).create(any(), any(), any());
    }

    @Test
    void aGroupWithTheSameLabelIsUnchanged_andAnInvalidOneIsBlocked() {
        groupsHere.add(new UserGroup(TENANT, "COMPTA", "Comptabilité", null, Set.of()));
        var plan = useCase.plan(ADMIN, bundle(group("COMPTA", "Comptabilité", null), group("RH", " ", null)), Map.of());

        assertThat(plan.find(ArtifactType.USER_GROUP, "COMPTA").orElseThrow().action()).isEqualTo(PlanAction.UNCHANGED);
        assertThat(plan.find(ArtifactType.USER_GROUP, "RH").orElseThrow().action()).isEqualTo(PlanAction.BLOCKED);
    }

    @Test
    void groupsArePlannedFirstAndTheMcpChannelNeverAppliesThem() {
        var b = bundle(json(ArtifactType.DOSSIER_TYPE, "T1", typeDoc("T1")), group("COMPTA", "Comptabilité", null));

        assertThat(useCase.plan(ADMIN, b, Map.of()).entries().getFirst().type()).isEqualTo(ArtifactType.USER_GROUP);
        var report = useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.MCP);

        assertThat(report.entries().getFirst().outcome()).isEqualTo(ImportOutcome.SKIPPED);
        verify(userGroups, never()).create(any(), any(), any());
    }

    @Test
    void aProcessOrATableNamingAGroupUnknownHereAndAbsentFromTheBundleIsAppliedWithAWarning() {
        groupsHere.add(new UserGroup(TENANT, "COURRIER", "Courrier", null, Set.of()));
        when(groupRefs.fromBpmn(any())).thenReturn(Set.of("COURRIER", "JURIDIQUE", "COMPTA"));
        when(groupRefs.fromDmn(any())).thenReturn(Set.of("COMPTA", "RH"));
        var b = bundle(group("COMPTA", "Comptabilité", null),
                new BundleEntry(ArtifactType.DMN, "routage", "<dmn/>".getBytes(StandardCharsets.UTF_8)),
                new BundleEntry(ArtifactType.BPMN, "wf", "<bpmn/>".getBytes(StandardCharsets.UTF_8)),
                json(ArtifactType.WORKFLOW_CONFIG, "wf", WF));

        var plan = useCase.plan(ADMIN, b, Map.of());

        var dmn = plan.find(ArtifactType.DMN, "routage").orElseThrow();
        assertThat(dmn.action()).isEqualTo(PlanAction.CREATE);
        assertThat(dmn.reason()).startsWith("WARNING: ").contains("[RH]").contains("sorting queue").contains("saved as a draft");
        var bpmn = plan.find(ArtifactType.BPMN, "wf").orElseThrow();
        assertThat(bpmn.action()).isEqualTo(PlanAction.CREATE);
        assertThat(bpmn.reason()).startsWith("WARNING: ").contains("[JURIDIQUE]").doesNotContain("COURRIER").doesNotContain("COMPTA");
    }

    @Test
    void aGroupBlockedInTheBundleDoesNotCountAsKnown() {
        when(groupRefs.fromDmn(any())).thenReturn(Set.of("COMPTA"));
        var b = bundle(group("COMPTA", "Comptabilité", "{{absent}}"),
                new BundleEntry(ArtifactType.DMN, "routage", "<dmn/>".getBytes(StandardCharsets.UTF_8)));

        assertThat(useCase.plan(ADMIN, b, Map.of()).find(ArtifactType.DMN, "routage").orElseThrow().reason())
                .startsWith("WARNING: ").contains("[COMPTA]");
    }
}
