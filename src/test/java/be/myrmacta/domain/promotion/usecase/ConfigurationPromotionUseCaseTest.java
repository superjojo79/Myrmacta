package be.myrmacta.domain.promotion.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
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
import be.myrmacta.domain.promotion.artifact.DocumentTemplateArtifacts;
import be.myrmacta.domain.promotion.artifact.ParcoursArtifacts;
import be.myrmacta.domain.promotion.artifact.TypeSettingsArtifacts;
import be.myrmacta.domain.parcours.model.Parcours;
import be.myrmacta.domain.parcours.model.ParcoursDefinition;
import be.myrmacta.domain.parcours.port.in.ManageParcoursUseCase;
import be.myrmacta.domain.parcours.port.out.ParcoursRepository;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.ParcoursDoc;
import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.InvalidTemplateSyntaxException;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.document.port.in.CreateDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.out.DocumentTemplateRepository;
import be.myrmacta.domain.document.port.out.TemplateSyntaxChecker;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.DocumentTemplateDoc;
import be.myrmacta.domain.promotion.artifact.UserGroupArtifacts;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import be.myrmacta.domain.deadline.port.in.ManageDeadlineUseCase;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import be.myrmacta.domain.mailroom.port.in.ManageReplyWorkflowUseCase;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.DeadlineDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.ReplyWorkflowDoc;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
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
    private ManageReplyWorkflowUseCase replyWorkflows;
    private ManageDeadlineUseCase deadlines;
    private DocumentTemplateRepository templateRepo;
    private CreateDocumentTemplateUseCase createTemplate;
    private TemplateSyntaxChecker templateSyntax;
    private final List<DocumentTemplate> templatesHere = new ArrayList<>();
    private ParcoursRepository parcoursRepo;
    private ManageParcoursUseCase manageParcours;

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
        replyWorkflows = mock(ManageReplyWorkflowUseCase.class);
        when(replyWorkflows.get(any())).thenReturn(Optional.empty());
        deadlines = mock(ManageDeadlineUseCase.class);
        when(deadlines.get(any())).thenReturn(Optional.empty());
        templateRepo = mock(DocumentTemplateRepository.class);
        when(templateRepo.listByTenantId(any(), any())).thenAnswer(inv -> templatesHere.stream()
                .filter(t -> inv.getArgument(1) == null || t.templateKey().equals(inv.getArgument(1))).toList());
        when(templateRepo.findActiveByTemplateKeysIn(any(), any())).thenAnswer(inv -> templatesHere.stream()
                .filter(t -> t.status() == TemplateStatus.ACTIVE && ((List<?>) inv.getArgument(1)).contains(t.templateKey())).toList());
        createTemplate = mock(CreateDocumentTemplateUseCase.class);
        when(createTemplate.create(any())).thenAnswer(inv -> {
            CreateDocumentTemplateUseCase.CreateCommand c = inv.getArgument(0);
            return template(c.templateKey(), c.variantKey(), c.locale(), c.label(), c.htmlContent(), 9, TemplateStatus.DRAFT);
        });
        templateSyntax = mock(TemplateSyntaxChecker.class);
        parcoursRepo = mock(ParcoursRepository.class);
        when(parcoursRepo.findByKey(any(), any())).thenReturn(Optional.empty());
        manageParcours = mock(ManageParcoursUseCase.class);
        when(manageParcours.save(any(), any())).thenAnswer(inv -> {
            ManageParcoursUseCase.SaveCommand c = inv.getArgument(1);
            return parcours(c.key(), c.label(), c.definition(), c.expectedVersion() == null ? 1 : c.expectedVersion() + 1);
        });
        groupRefs = mock(GroupReferenceExtractor.class);
        when(groupRefs.fromBpmn(any())).thenReturn(Set.of());
        when(groupRefs.fromDmn(any())).thenReturn(Set.of());
        useCase = newUseCase(CODEC);
    }

    /** @param templateCodec codec des templates (un codec qui compte les lectures mesure le coût du plan) */
    private ImportConfigurationUseCaseImpl newUseCase(be.myrmacta.domain.promotion.port.out.ArtifactCodec templateCodec) {
        return new ImportConfigurationUseCaseImpl(TENANT, Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC),
                new TenantAdminGuard(), CODEC, types, createType, updateType, compat, schemas, saveSchema, connectors,
                manageConnector, catalog, dmnDeployment, mock(DmnParserPort.class), workflowDeployment, bpmnParser, workflowConfigs,
                saveWorkflowConfig, propose, journalRepo,
                new AdditionalArtifacts(new UserGroupArtifacts(userGroups, CODEC), new GroupReferenceCheck(groupRefs),
                        new TypeSettingsArtifacts(TENANT, CODEC, types, workflowConfigs, replyWorkflows, deadlines),
                        new DocumentTemplateArtifacts(TENANT, templateCodec, templateRepo, createTemplate, templateSyntax, 1000),
                        new ParcoursArtifacts(TENANT, CODEC, parcoursRepo, manageParcours, workflowDeployment)));
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

    // ---- TODO §117 (D-PROMO-TYPE-SETTINGS) — workflow « réponse » et échéance d'un type ----

    private void typeHere(String code, FieldDefinition... fields) {
        when(types.findByCode(TENANT, code)).thenReturn(Optional.of(new DossierType(java.util.UUID.randomUUID(), TENANT, code, "L",
                "wf", true, false, List.of(fields), null, false, null, "t", "t")));
    }

    private void workflowConfigHere(String key) {
        when(workflowConfigs.findByWorkflowKey(TENANT, key)).thenReturn(Optional.of(new WorkflowTypeConfig(java.util.UUID.randomUUID(),
                TENANT, key, "Flow", WF.statusDefinitions(), WF.allowedDossierStatuses(), null, List.of(), "t", "t")));
    }

    private static FieldDefinition field(String name, FieldType type) {
        return new FieldDefinition(name, name, type, false, null, null, null, false);
    }

    private static BundleEntry reply(String type, ReplyWorkflowSetting.Mode mode, String workflowKey, String child) {
        return json(ArtifactType.REPLY_WORKFLOW, type, new ReplyWorkflowDoc(type, mode, workflowKey, child));
    }

    private static BundleEntry deadline(String type, String fieldName, int days, String group) {
        return json(ArtifactType.DEADLINE, type, new DeadlineDoc(type, fieldName, days, group));
    }

    @Test
    void aReplyWorkflowIsCreatedAfterTheWorkflowConfigurations_andAppliedByItsUseCase() {
        typeHere("FACTURE");
        workflowConfigHere("traiterReponse");
        var b = bundle(reply("FACTURE", ReplyWorkflowSetting.Mode.SUR_DOSSIER, "traiterReponse", null),
                json(ArtifactType.DOSSIER_TYPE, "T1", typeDoc("T1")));

        var plan = useCase.plan(ADMIN, b, Map.of());
        assertThat(plan.entries().getLast().type()).isEqualTo(ArtifactType.REPLY_WORKFLOW);
        assertThat(plan.entries().getLast().action()).isEqualTo(PlanAction.CREATE);

        useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);
        verify(replyWorkflows).set("FACTURE", ReplyWorkflowSetting.Mode.SUR_DOSSIER, "traiterReponse", null);
    }

    @Test
    void aReplyWorkflowWhoseConfigurationOnlyComesWithABpmnDraftIsBlockedUntilThatDraftIsPublished() {
        typeHere("FACTURE");
        var b = bundle(reply("FACTURE", ReplyWorkflowSetting.Mode.SUR_DOSSIER, "wf", null),
                new BundleEntry(ArtifactType.BPMN, "wf", "<bpmn/>".getBytes(StandardCharsets.UTF_8)),
                json(ArtifactType.WORKFLOW_CONFIG, "wf", WF));

        var entry = useCase.plan(ADMIN, b, Map.of()).find(ArtifactType.REPLY_WORKFLOW, "FACTURE").orElseThrow();

        assertThat(entry.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(entry.reason()).contains("only exists once its BPMN draft is published");
        useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);
        verify(replyWorkflows, never()).set(any(), any(), any(), any());
    }

    @Test
    void aReplyWorkflowMayRelyOnATypeAndAConfigurationCreatedByTheSameBundle_butNotOnAMissingChildType() {
        var created = bundle(json(ArtifactType.DOSSIER_TYPE, "REPONSE", typeDoc("REPONSE")), json(ArtifactType.WORKFLOW_CONFIG, "wf", WF),
                reply("REPONSE", ReplyWorkflowSetting.Mode.SUR_DOSSIER, "wf", null));
        assertThat(useCase.plan(ADMIN, created, Map.of()).find(ArtifactType.REPLY_WORKFLOW, "REPONSE").orElseThrow().action())
                .isEqualTo(PlanAction.CREATE);

        typeHere("FACTURE");
        workflowConfigHere("wf2");
        var child = useCase.plan(ADMIN, bundle(reply("FACTURE", ReplyWorkflowSetting.Mode.DOSSIER_ENFANT, "wf2", "ABSENT")), Map.of())
                .entries().getFirst();
        assertThat(child.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(child.reason()).contains("child dossier type 'ABSENT'");
        var noType = useCase.plan(ADMIN, bundle(reply("NULLE_PART", ReplyWorkflowSetting.Mode.SUR_DOSSIER, "wf2", null)), Map.of())
                .entries().getFirst();
        assertThat(noType.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(noType.reason()).contains("dossier type 'NULLE_PART'");
    }

    @Test
    void anIdenticalReplyWorkflowIsUnchanged() {
        typeHere("FACTURE");
        workflowConfigHere("wf");
        when(replyWorkflows.get("FACTURE")).thenReturn(Optional.of(
                new ReplyWorkflowSetting(TENANT, "FACTURE", ReplyWorkflowSetting.Mode.SUR_DOSSIER, "wf", null)));

        var plan = useCase.plan(ADMIN, bundle(reply("FACTURE", ReplyWorkflowSetting.Mode.SUR_DOSSIER, "wf", null)), Map.of());

        assertThat(plan.entries().getFirst().action()).isEqualTo(PlanAction.UNCHANGED);
    }

    @Test
    void aDeadlineNeedsADateFieldOfTheTypeAndAnExistingOrImportedEscalationGroup() {
        typeHere("FACTURE", field("echeance", FieldType.DATE), field("montant", FieldType.NUMBER));
        var plan = useCase.plan(ADMIN, bundle(
                deadline("FACTURE", "montant", 5, null)), Map.of()).entries().getFirst();
        assertThat(plan.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(plan.reason()).contains("not a DATE field");

        var unknownGroup = useCase.plan(ADMIN, bundle(deadline("FACTURE", "echeance", 5, "DIRECTION")), Map.of()).entries().getFirst();
        assertThat(unknownGroup.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(unknownGroup.reason()).contains("escalation group 'DIRECTION'");

        var b = bundle(group("DIRECTION", "Direction", null), deadline("FACTURE", "echeance", 5, "DIRECTION"));
        assertThat(useCase.plan(ADMIN, b, Map.of()).find(ArtifactType.DEADLINE, "FACTURE").orElseThrow().action())
                .isEqualTo(PlanAction.CREATE);
        useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);
        verify(deadlines).set("FACTURE", "echeance", 5, "DIRECTION");
    }

    @Test
    void aDeadlineReadsTheFieldsOfTheTypeAsTheBundleWritesThem_andAnIdenticalOneIsUnchanged() {
        var newType = new DossierTypeDoc("NOUVEAU", "N", "wf", true, false, List.of(field("terme", FieldType.DATE)), null, false, null,
                List.of());
        assertThat(useCase.plan(ADMIN, bundle(json(ArtifactType.DOSSIER_TYPE, "NOUVEAU", newType), deadline("NOUVEAU", "terme", 0, null)),
                Map.of()).find(ArtifactType.DEADLINE, "NOUVEAU").orElseThrow().action()).isEqualTo(PlanAction.CREATE);

        typeHere("FACTURE", field("echeance", FieldType.DATE));
        when(deadlines.get("FACTURE")).thenReturn(Optional.of(new DeadlineSetting(TENANT, "FACTURE", "echeance", 5, null)));
        assertThat(useCase.plan(ADMIN, bundle(deadline("FACTURE", "echeance", 5, null)), Map.of()).entries().getFirst().action())
                .isEqualTo(PlanAction.UNCHANGED);
        assertThat(useCase.plan(ADMIN, bundle(deadline("FACTURE", "echeance", 7, null)), Map.of()).entries().getFirst().action())
                .isEqualTo(PlanAction.UPDATE);
    }

    @Test
    void theMcpChannelNeverAppliesTypeSettings() {
        typeHere("FACTURE", field("echeance", FieldType.DATE));
        workflowConfigHere("wf");
        var b = bundle(reply("FACTURE", ReplyWorkflowSetting.Mode.SUR_DOSSIER, "wf", null), deadline("FACTURE", "echeance", 3, null));

        var report = useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.MCP);

        assertThat(report.entries()).extracting(e -> e.outcome()).containsOnly(ImportOutcome.SKIPPED);
        verify(replyWorkflows, never()).set(any(), any(), any(), any());
        verify(deadlines, never()).set(any(), any(), anyInt(), any());
    }

    // ---- TODO §117 (D-PROMO-TEMPLATE, D-PROMO-MCP-TEMPLATE) — templates de documents ----

    private static DocumentTemplate template(String key, String variant, String locale, String label, String html, int version,
            TemplateStatus status) {
        return new DocumentTemplate(java.util.UUID.randomUUID(), key, variant, locale, label, version, status, html, TENANT,
                Instant.EPOCH, Instant.EPOCH, "t");
    }

    private static BundleEntry templateEntry(String key, String variant, String locale, String label, String html) {
        var doc = new DocumentTemplateDoc(key, variant, locale, label, html);
        return json(ArtifactType.DOCUMENT_TEMPLATE, DocumentTemplateArtifacts.entryKey(doc), doc);
    }

    @Test
    void aTemplateIsImportedAsANewDraftVersion_neverActivated() {
        templatesHere.add(template("accuse", null, "fr", "Accusé", "<p>v1</p>", 1, TemplateStatus.ACTIVE));
        var b = bundle(templateEntry("accuse", null, "fr", "Accusé", "<p>v2</p>"), templateEntry("relance", "B2B", null, "Relance", "<p/>"));

        var plan = useCase.plan(ADMIN, b, Map.of());
        var updated = plan.entries().stream().filter(e -> e.key().startsWith("accuse")).findFirst().orElseThrow();
        assertThat(updated.action()).isEqualTo(PlanAction.UPDATE);
        assertThat(updated.reason()).contains("draft").contains("activate it");
        assertThat(plan.entries().stream().filter(e -> e.key().startsWith("relance")).findFirst().orElseThrow().action())
                .isEqualTo(PlanAction.CREATE);

        var report = useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);

        assertThat(report.entries()).extracting(e -> e.outcome()).containsOnly(ImportOutcome.APPLIED);
        verify(createTemplate).create(new CreateDocumentTemplateUseCase.CreateCommand("accuse", null, "fr", "Accusé", "<p>v2</p>", "alice"));
        verify(createTemplate).create(new CreateDocumentTemplateUseCase.CreateCommand("relance", "B2B", null, "Relance", "<p/>", "alice"));
    }

    @Test
    void anIdenticalActiveVersionOrAnAlreadyStagedDraftMakesTheTemplateUnchanged_soReplayingConverges() {
        templatesHere.add(template("accuse", null, null, "Accusé", "<p>v1</p>", 1, TemplateStatus.ACTIVE));
        templatesHere.add(template("relance", null, null, "Relance", "<p>v1</p>", 1, TemplateStatus.ACTIVE));
        templatesHere.add(template("relance", null, null, "Relance", "<p>v2</p>", 2, TemplateStatus.DRAFT));
        var b = bundle(templateEntry("accuse", null, null, "Accusé", "<p>v1</p>"), templateEntry("relance", null, null, "Relance", "<p>v2</p>"));

        var plan = useCase.plan(ADMIN, b, Map.of());

        assertThat(plan.entries()).extracting(e -> e.action()).containsOnly(PlanAction.UNCHANGED);
        assertThat(plan.entries().stream().filter(e -> e.key().startsWith("relance")).findFirst().orElseThrow().reason())
                .contains("draft version 2");
        useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);
        verify(createTemplate, never()).create(any());
    }

    @Test
    void aTemplateTooLargeOrWithARefusedSyntaxIsBlocked() {
        org.mockito.Mockito.doThrow(new InvalidTemplateSyntaxException("th:utext not allowed")).when(templateSyntax).check("<p th:x>");
        var plan = useCase.plan(ADMIN, bundle(templateEntry("gros", null, null, "G", "x".repeat(1001)),
                templateEntry("faux", null, null, "F", "<p th:x>")), Map.of());

        assertThat(plan.entries()).extracting(e -> e.action()).containsOnly(PlanAction.BLOCKED);
        assertThat(plan.entries()).anyMatch(e -> e.reason().contains("exceeds 1000 bytes"));
        assertThat(plan.entries()).anyMatch(e -> e.reason().contains("th:utext not allowed"));
    }

    @Test
    void theTemplateFileNameFollowsItsCombination_andAnUnsafeKeyStaysUnique() {
        assertThat(DocumentTemplateArtifacts.entryKey(new DocumentTemplateDoc("accuse", null, "fr", "", ""))).isEqualTo("accuse._.fr");
        String a = DocumentTemplateArtifacts.entryKey(new DocumentTemplateDoc("avis de passage", "B2B", null, "", ""));
        String b = DocumentTemplateArtifacts.entryKey(new DocumentTemplateDoc("avis-de-passage", "B2B", null, "", ""));
        assertThat(a).startsWith("avis-de-passage.B2B._-").isNotEqualTo(b);
        new BundleEntry(ArtifactType.DOCUMENT_TEMPLATE, a, new byte[0]);
        // le nom de fichier et le contenu doivent concorder
        var forged = json(ArtifactType.DOCUMENT_TEMPLATE, "autre._._", new DocumentTemplateDoc("accuse", null, null, "", "<p/>"));
        assertThatThrownBy(() -> useCase.plan(ADMIN, bundle(forged), Map.of())).hasMessageContaining("agree");
    }

    @Test
    void theMcpChannelStagesTemplateDrafts() {
        var b = bundle(templateEntry("accuse", null, null, "Accusé", "<p/>"), group("COMPTA", "Compta", null));

        var report = useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.MCP);

        assertThat(report.entries()).filteredOn(e -> e.type() == ArtifactType.DOCUMENT_TEMPLATE).singleElement()
                .satisfies(e -> assertThat(e.outcome()).isEqualTo(ImportOutcome.APPLIED));
        assertThat(report.entries()).filteredOn(e -> e.type() == ArtifactType.USER_GROUP).singleElement()
                .satisfies(e -> assertThat(e.outcome()).isEqualTo(ImportOutcome.SKIPPED));
    }

    @Test
    void aTypeNamingATemplateKeyWithoutActiveVersionNorTemplateInTheBundleIsAppliedWithAWarning() {
        templatesHere.add(template("actif", null, null, "A", "<p/>", 1, TemplateStatus.ACTIVE));
        templatesHere.add(template("brouillon", null, null, "B", "<p/>", 1, TemplateStatus.DRAFT));
        var type = new DossierTypeDoc("T1", "L", "wf", true, false, List.of(), null, false, null,
                List.of("actif", "apporte", "brouillon", "absent"));
        var b = bundle(json(ArtifactType.DOSSIER_TYPE, "T1", type), templateEntry("apporte", "x", null, "Ap", "<p/>"));

        var entry = useCase.plan(ADMIN, b, Map.of()).find(ArtifactType.DOSSIER_TYPE, "T1").orElseThrow();

        assertThat(entry.action()).isEqualTo(PlanAction.CREATE);
        assertThat(entry.reason()).startsWith("WARNING: ").contains("[absent, brouillon]").doesNotContain("actif").doesNotContain("apporte");
    }

    @Test
    void aBpmnNeedingATemplateThatOnlyTheBundleBringsIsBlockedWithTheOrderToFollow() {
        when(propose.validateBpmn(any(), org.mockito.ArgumentMatchers.eq("wf"), any(), any())).thenThrow(
                new be.myrmacta.domain.workflow.model.UnknownBlockReferenceException("generer", "documentType", "documentTemplates",
                        "accuse"));
        var b = bundle(templateEntry("accuse", null, null, "Accusé", "<p/>"),
                new BundleEntry(ArtifactType.BPMN, "wf", "<bpmn/>".getBytes(StandardCharsets.UTF_8)),
                json(ArtifactType.WORKFLOW_CONFIG, "wf", WF));

        var bpmn = useCase.plan(ADMIN, b, Map.of()).find(ArtifactType.BPMN, "wf").orElseThrow();

        assertThat(bpmn.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(bpmn.reason()).contains("document template 'accuse'").contains("run the import again");
    }

    // ---- TODO §117 (D-PROMO-PARCOURS) — définitions de parcours ----

    private static ParcoursDefinition definition(String schemaKey) {
        return new ParcoursDefinition(List.of(new WorkflowStatusDefinition("OUVERT", "Ouvert", true, StatusOutcome.NONE)),
                List.of(DossierStatus.OUVERT), null, List.of(new ParcoursDefinition.Step("saisie", "Saisie",
                        ParcoursDefinition.Kind.QUESTION, Map.of("schemaKey", schemaKey), null, null, null)));
    }

    private static Parcours parcours(String key, String label, ParcoursDefinition definition, int version) {
        return new Parcours(java.util.UUID.randomUUID(), TENANT, key, label, definition, version, "t", "t", Instant.EPOCH,
                Instant.EPOCH, null, null);
    }

    private static BundleEntry parcoursEntry(String key, String label, ParcoursDefinition definition, Boolean inSync) {
        return json(ArtifactType.PARCOURS, key, new ParcoursDoc(key, label, definition, inSync));
    }

    @Test
    void aParcoursDefinitionIsSavedWithoutCompilation_andAnExistingOneIsReplacedAtItsCurrentVersion() {
        when(parcoursRepo.findByKey(TENANT, "sinistre")).thenReturn(Optional.of(parcours("sinistre", "Sinistre", definition("avis"), 4)));
        var b = bundle(parcoursEntry("nouveau", "Nouveau", definition("avis"), true),
                parcoursEntry("sinistre", "Sinistre", definition("expertise"), true));

        var plan = useCase.plan(ADMIN, b, Map.of());
        assertThat(plan.find(ArtifactType.PARCOURS, "nouveau").orElseThrow().action()).isEqualTo(PlanAction.CREATE);
        var update = plan.find(ArtifactType.PARCOURS, "sinistre").orElseThrow();
        assertThat(update.action()).isEqualTo(PlanAction.UPDATE);
        assertThat(update.reason()).contains("version 4").contains("not compiled");

        useCase.apply(ADMIN, b, Map.of(), Map.of(), null, Channel.ADMIN);

        verify(manageParcours).save(ADMIN, new ManageParcoursUseCase.SaveCommand("nouveau", "Nouveau", definition("avis"), null));
        verify(manageParcours).save(ADMIN, new ManageParcoursUseCase.SaveCommand("sinistre", "Sinistre", definition("expertise"), 4));
        verify(propose, never()).proposeBpmn(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void anIdenticalDefinitionIsUnchangedWhateverItsSyncMark_butAnOutOfSyncSourceIsFlagged() {
        when(parcoursRepo.findByKey(TENANT, "sinistre")).thenReturn(Optional.of(parcours("sinistre", "Sinistre", definition("avis"), 2)));

        var inSync = useCase.plan(ADMIN, bundle(parcoursEntry("sinistre", "Sinistre", definition("avis"), true)), Map.of())
                .entries().getFirst();
        assertThat(inSync.action()).isEqualTo(PlanAction.UNCHANGED);
        assertThat(inSync.reason()).isEmpty();

        var outOfSync = useCase.plan(ADMIN, bundle(parcoursEntry("sinistre", "Sinistre", definition("avis"), false)), Map.of())
                .entries().getFirst();
        assertThat(outOfSync.action()).isEqualTo(PlanAction.UNCHANGED);
        assertThat(outOfSync.reason()).startsWith("WARNING: ").contains("last compilation");
    }

    @Test
    void anIncoherentParcoursIsBlocked_andTheMcpChannelNeverSavesOne() {
        var broken = new ParcoursDefinition(List.of(new WorkflowStatusDefinition("OUVERT", "Ouvert", true, StatusOutcome.NONE)),
                List.of(DossierStatus.OUVERT), null, List.of(new ParcoursDefinition.Step("saisie", "Saisie",
                        ParcoursDefinition.Kind.QUESTION, Map.of("schemaKey", "avis"), "inconnue", null, null)));
        assertThat(useCase.plan(ADMIN, bundle(parcoursEntry("casse", "Cassé", broken, null)), Map.of()).entries().getFirst().action())
                .isEqualTo(PlanAction.BLOCKED);

        var report = useCase.apply(ADMIN, bundle(parcoursEntry("nouveau", "Nouveau", definition("avis"), true)), Map.of(), Map.of(),
                null, Channel.MCP);
        assertThat(report.entries().getFirst().outcome()).isEqualTo(ImportOutcome.SKIPPED);
        verify(manageParcours, never()).save(any(), any());
    }

    // ---- revue de clôture #279 : tests de régression ----

    /** M1 — les fichiers de template sont lus un nombre de fois proportionnel au bundle, pas une fois par type de dossier. */
    @Test
    void planningManyTypesDoesNotDecodeTheTemplatesOnceForEachType() {
        java.util.concurrent.atomic.AtomicInteger templateReads = new java.util.concurrent.atomic.AtomicInteger();
        var counting = new be.myrmacta.domain.promotion.port.out.ArtifactCodec() {
            @Override
            public byte[] encode(Object doc) {
                return CODEC.encode(doc);
            }

            @Override
            public <T> T decode(byte[] content, Class<T> type) {
                templateReads.incrementAndGet();
                return CODEC.decode(content, type);
            }
        };
        List<BundleEntry> entries = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            entries.add(json(ArtifactType.DOSSIER_TYPE, "T" + i, new DossierTypeDoc("T" + i, "L", "wf", true, false, List.of(), null,
                    false, null, i % 2 == 0 ? List.of("accuse") : List.of())));
        }
        entries.add(templateEntry("accuse", null, null, "A", "<p/>"));

        newUseCase(counting).plan(ADMIN, bundle(entries.toArray(BundleEntry[]::new)), Map.of());

        assertThat(templateReads.get()).isLessThanOrEqualTo(4);
    }

    /** M3 — un template de 5 Mio, même plein de caractères échappés en JSON, tient dans un fichier de bundle. */
    @Test
    void aTemplateAtTheHtmlSizeLimitStillFitsInABundleFile() {
        templatesHere.add(template("lourd", null, null, "L", "\"".repeat(5 * 1024 * 1024), 1, TemplateStatus.ACTIVE));
        var artifacts = new DocumentTemplateArtifacts(TENANT, CODEC, templateRepo, createTemplate, templateSyntax, 5 * 1024 * 1024);

        var exported = artifacts.export();

        assertThat(exported).singleElement().satisfies(e -> assertThat(e.content().length)
                .isGreaterThan(10 * 1024 * 1024).isLessThanOrEqualTo(ConfigurationBundle.MAX_ENTRY_BYTES));
        assertThat(bundle(exported.toArray(BundleEntry[]::new)).entries()).hasSize(1);
    }

    /** B5 — une variante ou une langue littérale « _ » ne prend pas le nom de fichier de la valeur par défaut. */
    @Test
    void aLiteralUnderscoreVariantDoesNotCollideWithTheDefault() {
        String byDefault = DocumentTemplateArtifacts.entryKey(new DocumentTemplateDoc("k", null, "fr", "", ""));
        String literal = DocumentTemplateArtifacts.entryKey(new DocumentTemplateDoc("k", "_", "fr", "", ""));
        assertThat(byDefault).isEqualTo("k._.fr");
        assertThat(literal).startsWith("k._.fr-").isNotEqualTo(byDefault);
    }

    /** B6 — la consigne « relancez » ne vaut que pour un template du bundle, non bloqué, référencé comme template. */
    @Test
    void theRerunHintOnlyCoversAnApplicableTemplateOfTheBundle() {
        org.mockito.Mockito.doThrow(new InvalidTemplateSyntaxException("refusé")).when(templateSyntax).check("<p th:x>");
        when(propose.validateBpmn(any(), org.mockito.ArgumentMatchers.eq("wf"), any(), any())).thenThrow(
                new be.myrmacta.domain.workflow.model.UnknownBlockReferenceException("appel", "connectorInstanceId",
                        "connectorInstances", "accuse"));
        when(propose.validateBpmn(any(), org.mockito.ArgumentMatchers.eq("wf2"), any(), any())).thenThrow(
                new be.myrmacta.domain.workflow.model.UnknownBlockReferenceException("generer", "documentType",
                        "documentTemplates", "casse"));
        var b = bundle(templateEntry("accuse", null, null, "A", "<p/>"), templateEntry("casse", null, null, "C", "<p th:x>"),
                new BundleEntry(ArtifactType.BPMN, "wf", "<bpmn/>".getBytes(StandardCharsets.UTF_8)), json(ArtifactType.WORKFLOW_CONFIG, "wf", WF),
                new BundleEntry(ArtifactType.BPMN, "wf2", "<bpmn/>".getBytes(StandardCharsets.UTF_8)),
                json(ArtifactType.WORKFLOW_CONFIG, "wf2", new WorkflowConfigDoc("wf2", "Flow", WF.statusDefinitions(),
                        WF.allowedDossierStatuses(), null, List.of())));

        var plan = useCase.plan(ADMIN, b, Map.of());

        assertThat(plan.find(ArtifactType.BPMN, "wf").orElseThrow().reason()).contains("does not exist").doesNotContain("run the import again");
        assertThat(plan.find(ArtifactType.BPMN, "wf2").orElseThrow().reason()).contains("does not exist").doesNotContain("run the import again");
    }

    /** B7 — une valeur que l'écriture normalise (blancs) est comparée normalisée : rejouer converge. */
    @Test
    void valuesNormalisedOnWriteCompareNormalised_soReplayingConverges() {
        templatesHere.add(template("accuse", null, null, "A", "<p>v1</p>", 1, TemplateStatus.ACTIVE));
        groupsHere.add(new UserGroup(TENANT, "COMPTA", "Compta", "sg-compta", Set.of()));

        var plan = useCase.plan(ADMIN, bundle(templateEntry("accuse", null, null, "A", "<p>v1</p>\n"),
                group("COMPTA", "  Compta ", "{{idp}}")), Map.of("idp", " sg-compta "));

        assertThat(plan.entries()).extracting(e -> e.action()).containsOnly(PlanAction.UNCHANGED);
        assertThat(plan.entries()).allSatisfy(e -> assertThat(e.reason()).doesNotContain("kept"));
    }

    /** B10 — l'empreinte d'un bundle de format 1 (journalisée par la tranche 1) ne dépend pas des artefacts ajoutés depuis. */
    @Test
    void theChecksumOfAFormat1BundleIsStable() {
        assertThat(List.of(ArtifactType.DOSSIER_TYPE, ArtifactType.TASK_QUESTION_SCHEMA, ArtifactType.CONNECTOR_INSTANCE, ArtifactType.DMN,
                ArtifactType.BPMN, ArtifactType.WORKFLOW_CONFIG)).extracting(Enum::ordinal).containsExactly(0, 1, 2, 3, 4, 5);
        var dmn = new BundleEntry(ArtifactType.DMN, "grid", "<dmn/>".getBytes(StandardCharsets.UTF_8));
        var type = new BundleEntry(ArtifactType.DOSSIER_TYPE, "T1", "{}\n".getBytes(StandardCharsets.UTF_8));
        var v1 = new ConfigurationBundle(1, Instant.parse("2026-10-01T09:00:00Z"), "recette", "t", List.of(dmn, type));
        String canonical = "DOSSIER_TYPE/T1:" + type.sha256() + "\nDMN/grid:" + dmn.sha256() + "\n";
        assertThat(v1.sha256()).isEqualTo(BundleEntry.sha256(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
