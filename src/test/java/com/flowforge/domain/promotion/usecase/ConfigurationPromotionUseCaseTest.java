package com.flowforge.domain.promotion.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import com.flowforge.domain.dossier.port.in.CreateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.promotion.model.ArtifactType;
import com.flowforge.domain.promotion.model.BundleEntry;
import com.flowforge.domain.promotion.model.ConfigImportRecord;
import com.flowforge.domain.promotion.model.ConfigurationBundle;
import com.flowforge.domain.promotion.model.ImportOutcome;
import com.flowforge.domain.promotion.model.InvalidBundleException;
import com.flowforge.domain.promotion.model.PlanAction;
import com.flowforge.domain.promotion.model.doc.ConfigDocs.ConnectorDoc;
import com.flowforge.domain.promotion.model.doc.ConfigDocs.DossierTypeDoc;
import com.flowforge.domain.promotion.model.doc.ConfigDocs.TaskQuestionSchemaDoc;
import com.flowforge.domain.promotion.model.doc.ConfigDocs.WorkflowConfigDoc;
import com.flowforge.domain.promotion.port.in.ImportConfigurationUseCase.Channel;
import com.flowforge.domain.promotion.port.out.ConfigImportLogRepository;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.port.in.ProposeDraftUseCase;
import com.flowforge.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import com.flowforge.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import com.flowforge.infrastructure.promotion.JsonArtifactCodec;
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
        useCase = new ImportConfigurationUseCaseImpl(TENANT, Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC),
                new TenantAdminGuard(), CODEC, types, createType, updateType, compat, schemas, saveSchema, connectors,
                manageConnector, catalog, dmnDeployment, mock(DmnParserPort.class), workflowDeployment, bpmnParser, workflowConfigs,
                saveWorkflowConfig, propose, journalRepo);
    }

    private static BundleEntry json(ArtifactType type, String key, Object doc) {
        return new BundleEntry(type, key, CODEC.encode(doc));
    }

    private static ConfigurationBundle bundle(BundleEntry... entries) {
        return new ConfigurationBundle(1, Instant.parse("2026-10-01T09:00:00Z"), "recette", "t", List.of(entries));
    }

    private static DossierTypeDoc typeDoc(String code) {
        return new DossierTypeDoc(code, "L", "wf", true, false, List.of(), null, false, null, List.of());
    }

    @Test
    void aTypeThatWouldBreakExistingDossiersIsAConflictAndNeverForced() {
        when(types.findByCode(TENANT, "T1")).thenReturn(Optional.of(new com.flowforge.domain.dossier.model.DossierType(
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
        when(types.findByCode(TENANT, "T1")).thenReturn(Optional.of(new com.flowforge.domain.dossier.model.DossierType(
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

    @Test
    void aBpmnDraftCarriesItsConfigurationAndTheConfigIsNotAppliedTwice() {
        var b = bundle(new BundleEntry(ArtifactType.BPMN, "wf", "<bpmn/>".getBytes(StandardCharsets.UTF_8)),
                json(ArtifactType.WORKFLOW_CONFIG, "wf", WF));

        var report = useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);

        verify(propose).proposeBpmn(any(), org.mockito.ArgumentMatchers.eq(com.flowforge.domain.workflow.model.Provenance.IMPORT),
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
                        List.of(new com.flowforge.domain.dossier.model.FieldDefinition("x", "X",
                                com.flowforge.domain.dossier.model.FieldType.STRING, false, null, null, null, false)))));

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
        assertThatThrownBy(() -> new ConfigurationBundle(2, Instant.now(), "", "", List.of())).hasMessageContaining("unsupported");
        assertThatThrownBy(() -> bundle(json(ArtifactType.DOSSIER_TYPE, "T1", typeDoc("T1")), json(ArtifactType.DOSSIER_TYPE, "T1", typeDoc("T1"))))
                .hasMessageContaining("duplicate");
        for (String bad : List.of("../x", "a/b", ".hidden", "a..b", "", "x".repeat(200), "a b")) {
            assertThatThrownBy(() -> new BundleEntry(ArtifactType.DMN, bad, new byte[0])).isInstanceOf(InvalidBundleException.class);
        }
    }
}
