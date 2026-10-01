package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.workflow.model.BpmnDeploymentResult;
import com.flowforge.domain.workflow.model.BpmnDraft;
import com.flowforge.domain.workflow.model.ConfigPublication;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.model.DecisionTableVersionInfo;
import com.flowforge.domain.workflow.model.DirectPublicationNotAllowedException;
import com.flowforge.domain.workflow.model.DmnDraft;
import com.flowforge.domain.workflow.model.DraftArtifactType;
import com.flowforge.domain.workflow.model.DraftChangedException;
import com.flowforge.domain.workflow.model.DraftConflictException;
import com.flowforge.domain.workflow.model.DraftNotFoundException;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.model.PendingDraft;
import com.flowforge.domain.workflow.model.ProcessDeploymentInfo;
import com.flowforge.domain.workflow.model.Provenance;
import com.flowforge.domain.workflow.model.ProposedWorkflowConfig;
import com.flowforge.domain.workflow.model.PublicationMode;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.in.DeployBpmnProcessUseCase;
import com.flowforge.domain.workflow.port.in.DeployDecisionTableUseCase;
import com.flowforge.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.out.BpmnDraftRepository;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.port.out.ConfigPublicationRepository;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.DmnDraftRepository;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** TODO §82 (D6) — proposition, relecture et publication des brouillons BPMN / DMN. */
class DraftUseCasesTest {

    private static final String TENANT = "tenant_dev";
    private static final String KEY = "claim";
    private static final AccessContext.User AGENT = AccessContext.user("agent-1", Set.of("API_SERVICE"));
    private static final AccessContext.User OTHER_AGENT = AccessContext.user("agent-2", Set.of("API_SERVICE"));
    private static final AccessContext.User HUMAN = AccessContext.user("alice", Set.of("TENANT_ADMIN"));
    private static final AccessContext.User VIEWER = AccessContext.user("bob", Set.of("DOSSIER_VIEWER"));
    private static final ProposedWorkflowConfig CONFIG = new ProposedWorkflowConfig("Claim",
            List.of(new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE)),
            List.of(DossierStatus.OUVERT), null, List.of());

    private final Map<String, BpmnDraft> bpmnStore = new HashMap<>();
    private final Map<String, DmnDraft> dmnStore = new HashMap<>();
    private final List<ConfigPublication> journal = new ArrayList<>();
    private BpmnDraftRepository bpmnDrafts;
    private DmnDraftRepository dmnDrafts;
    private BpmnParserPort bpmnParser;
    private DmnParserPort dmnParser;
    private DeployBpmnProcessUseCase deployBpmn;
    private DeployDecisionTableUseCase deployDmn;
    private SaveWorkflowTypeConfigUseCase saveConfig;
    private WorkflowTypeConfigRepository configRepository;
    private WorkflowDeploymentPort workflowDeployment;
    private DmnDeploymentPort dmnDeployment;
    private ConfigPublicationRepository publications;
    private boolean direct;
    private ProposeDraftUseCaseImpl propose;
    private ManageDraftsUseCaseImpl manage;

    @BeforeEach
    void setUp() {
        bpmnDrafts = new BpmnDraftRepository() {
            public Optional<BpmnDraft> findByTenantIdAndProcessDefinitionKey(String t, String k) {
                return Optional.ofNullable(bpmnStore.get(k));
            }
            public BpmnDraft save(BpmnDraft d) {
                bpmnStore.put(d.processDefinitionKey(), d);
                return d;
            }
            public void deleteByTenantIdAndProcessDefinitionKey(String t, String k) {
                bpmnStore.remove(k);
            }
            public List<BpmnDraft> findAllByTenantId(String t) {
                return List.copyOf(bpmnStore.values());
            }
            public int deleteAutomatedOlderThan(String t, Instant cutoff) {
                return 0;
            }
        };
        dmnDrafts = new DmnDraftRepository() {
            public Optional<DmnDraft> findByTenantIdAndDecisionKey(String t, String k) {
                return Optional.ofNullable(dmnStore.get(k));
            }
            public DmnDraft save(DmnDraft d) {
                dmnStore.put(d.decisionKey(), d);
                return d;
            }
            public void deleteByTenantIdAndDecisionKey(String t, String k) {
                dmnStore.remove(k);
            }
            public List<DmnDraft> findAllByTenantId(String t) {
                return List.copyOf(dmnStore.values());
            }
            public int deleteAutomatedOlderThan(String t, Instant cutoff) {
                return 0;
            }
        };
        bpmnParser = mock(BpmnParserPort.class);
        when(bpmnParser.extractProcessDefinitionKey(any())).thenReturn(KEY);
        dmnParser = mock(DmnParserPort.class);
        when(dmnParser.extractDecisionKey(any())).thenReturn("table");
        deployBpmn = mock(DeployBpmnProcessUseCase.class);
        when(deployBpmn.deploy(any(), any())).thenReturn(new BpmnDeploymentResult(KEY, 4, "dep"));
        deployDmn = mock(DeployDecisionTableUseCase.class);
        when(deployDmn.deploy(any(), any())).thenReturn(new DecisionTableDeploymentResult("table", 7, "dep"));
        saveConfig = mock(SaveWorkflowTypeConfigUseCase.class);
        configRepository = mock(WorkflowTypeConfigRepository.class);
        when(configRepository.findByWorkflowKey(TENANT, KEY)).thenReturn(Optional.empty());
        workflowDeployment = mock(WorkflowDeploymentPort.class);
        dmnDeployment = mock(DmnDeploymentPort.class);
        publications = mock(ConfigPublicationRepository.class);
        when(publications.save(any())).thenAnswer(inv -> {
            journal.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        direct = false;
        TenantAdminGuard guard = new TenantAdminGuard();
        propose = new ProposeDraftUseCaseImpl(TENANT, guard, bpmnDrafts, dmnDrafts, bpmnParser, dmnParser, deployBpmn,
                configRepository, dmnDeployment);
        manage = new ManageDraftsUseCaseImpl(TENANT, guard, bpmnDrafts, dmnDrafts, deployBpmn, deployDmn, saveConfig,
                configRepository, workflowDeployment, dmnDeployment, publications, () -> direct);
    }

    // ---- proposition ----

    @Test
    void aProposalWritesADraftOnlyAndDeploysNothing() {
        var proposal = propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<bpmn/>", CONFIG, false);

        assertThat(proposal.replacedExistingDraft()).isFalse();
        BpmnDraft saved = bpmnStore.get(KEY);
        assertThat(saved.provenance()).isEqualTo(Provenance.MCP);
        assertThat(saved.updatedBy()).isEqualTo("agent-1");
        assertThat(saved.proposedConfig()).isEqualTo(CONFIG);
        verify(deployBpmn).validate(any(), any());
        verify(deployBpmn, never()).deploy(any(), any());
        verifyNoInteractions(saveConfig);
    }

    @Test
    void theProposalIsValidatedAgainstTheSubmittedConfigurationBeforeAnyWrite() {
        org.mockito.Mockito.doThrow(new InvalidBpmnException("unknown reference")).when(deployBpmn).validate(any(), any());

        assertThatThrownBy(() -> propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<bpmn/>", CONFIG, false))
                .isInstanceOf(InvalidBpmnException.class);
        assertThat(bpmnStore).isEmpty();
    }

    @Test
    void aForeignDraftIsNeverOverwrittenSilently() {
        bpmnStore.put(KEY, new BpmnDraft(TENANT, KEY, "<human wip/>", Instant.parse("2026-10-01T09:00:00Z"), "alice"));

        assertThatThrownBy(() -> propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<bpmn/>", CONFIG, false))
                .isInstanceOf(DraftConflictException.class)
                .hasMessageContaining("alice")
                .hasMessageContaining("human")
                .hasMessageContaining("replaceExistingDraft");
        assertThat(bpmnStore.get(KEY).xml()).isEqualTo("<human wip/>");

        // revue D6 M3 : le drapeau ne suffit jamais à écraser le travail d'un humain
        assertThatThrownBy(() -> propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<bpmn/>", CONFIG, true))
                .isInstanceOf(DraftConflictException.class)
                .hasMessageContaining("never replaces");
        assertThat(bpmnStore.get(KEY).xml()).isEqualTo("<human wip/>");
    }

    @Test
    void replaceExistingDraftOverwritesAnotherAutomatedDraftOnly() {
        propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<v1/>", CONFIG, false);
        propose.proposeBpmn(OTHER_AGENT, Provenance.MCP, KEY, "<v2/>", CONFIG, true);
        assertThat(bpmnStore.get(KEY).xml()).isEqualTo("<v2/>");
        assertThat(bpmnStore.get(KEY).updatedBy()).isEqualTo("agent-2");
    }

    @Test
    void anotherAgentsDraftIsProtectedButTheSameWriterMayIterate() {
        propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<v1/>", CONFIG, false);
        var again = propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<v2/>", CONFIG, false);
        assertThat(again.replacedExistingDraft()).isTrue();
        assertThat(bpmnStore.get(KEY).xml()).isEqualTo("<v2/>");

        assertThatThrownBy(() -> propose.proposeBpmn(OTHER_AGENT, Provenance.MCP, KEY, "<v3/>", CONFIG, false))
                .isInstanceOf(DraftConflictException.class);
        // même acteur mais provenance différente (l'assistant écrase un brouillon MCP) : protégé aussi
        assertThatThrownBy(() -> propose.proposeBpmn(AGENT, Provenance.ASSISTANT, KEY, "<v3/>", CONFIG, false))
                .isInstanceOf(DraftConflictException.class);
    }

    @Test
    void theEditorProvenanceCannotBeUsedToProposeAndANonAdminIsRefused() {
        assertThatThrownBy(() -> propose.proposeBpmn(AGENT, Provenance.UI, KEY, "<bpmn/>", CONFIG, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> propose.proposeBpmn(VIEWER, Provenance.MCP, KEY, "<bpmn/>", CONFIG, false))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(bpmnStore).isEmpty();
    }

    @Test
    void dmnRedeployFromAVersionLoadsItsXmlIntoADraftWithANote() {
        when(dmnDeployment.findAllVersions(TENANT, "table")).thenReturn(List.of(
                new DecisionTableVersionInfo("table", 1, "d1", Instant.EPOCH), new DecisionTableVersionInfo("table", 3, "d3", Instant.EPOCH)));
        when(dmnDeployment.findDeployedXml(TENANT, "table", 1)).thenReturn(Optional.of("<old/>".getBytes(StandardCharsets.UTF_8)));

        propose.proposeDmnFromVersion(AGENT, Provenance.MCP, "table", 1, false);

        assertThat(dmnStore.get("table").xml()).isEqualTo("<old/>");
        assertThat(dmnStore.get("table").note()).contains("version 1");
        assertThatThrownBy(() -> propose.proposeDmnFromVersion(AGENT, Provenance.MCP, "table", 3, true))
                .isInstanceOf(com.flowforge.domain.workflow.model.DecisionVersionAlreadyCurrentException.class);
        verify(deployDmn, never()).deploy(any(), any());
    }

    // ---- publication ----

    @Test
    void aHumanPublishesExactlyTheReviewedDraftConfigurationFirstThenTheDiagram() {
        propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<bpmn/>", CONFIG, false);
        Instant reviewed = bpmnStore.get(KEY).updatedAt();
        org.mockito.Mockito.clearInvocations(deployBpmn);

        ConfigPublication p = manage.publish(HUMAN, DraftArtifactType.BPMN, KEY, reviewed, PublicationMode.MANUAL);

        InOrder order = inOrder(deployBpmn, saveConfig);
        order.verify(deployBpmn).validate(any(), any());
        order.verify(saveConfig).create(any(), eq(HUMAN));
        order.verify(deployBpmn).deploy(eq(KEY + ".bpmn20.xml"), any());
        assertThat(p.version()).isEqualTo(4);
        assertThat(p.configProvenance()).isEqualTo(Provenance.MCP);
        assertThat(p.configAuthor()).isEqualTo("agent-1");
        assertThat(p.draftProvenance()).isEqualTo(Provenance.MCP);
        assertThat(p.draftAuthor()).isEqualTo("agent-1");
        assertThat(p.publishedBy()).isEqualTo("alice");
        assertThat(p.mode()).isEqualTo(PublicationMode.MANUAL);
        assertThat(journal).containsExactly(p);
    }

    @Test
    void aDraftChangedSinceReviewIsRefusedAndNothingIsDeployed() {
        propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<v1/>", CONFIG, false);
        Instant reviewed = bpmnStore.get(KEY).updatedAt();
        bpmnStore.put(KEY, new BpmnDraft(TENANT, KEY, "<v2/>", reviewed.plusSeconds(5), "agent-1", Provenance.MCP, CONFIG, null));

        assertThatThrownBy(() -> manage.publish(HUMAN, DraftArtifactType.BPMN, KEY, reviewed, PublicationMode.MANUAL))
                .isInstanceOf(DraftChangedException.class);
        verify(deployBpmn, never()).deploy(any(), any());
        verifyNoInteractions(saveConfig);
        assertThat(journal).isEmpty();
    }

    @Test
    void aManualPublicationRequiresTheReviewedTimestamp() {
        propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<bpmn/>", CONFIG, false);
        assertThatThrownBy(() -> manage.publish(HUMAN, DraftArtifactType.BPMN, KEY, null, PublicationMode.MANUAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAgentCannotPublishUnlessTheInstanceAllowsDirectPublication() {
        propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<bpmn/>", CONFIG, false);
        Instant stamp = bpmnStore.get(KEY).updatedAt();

        assertThatThrownBy(() -> manage.publish(AGENT, DraftArtifactType.BPMN, KEY, stamp, PublicationMode.DIRECT))
                .isInstanceOf(DirectPublicationNotAllowedException.class)
                .hasMessageContaining("human");
        // un agent ne peut pas se faire passer pour une publication humaine
        assertThatThrownBy(() -> manage.publish(AGENT, DraftArtifactType.BPMN, KEY, stamp, PublicationMode.MANUAL))
                .isInstanceOf(DirectPublicationNotAllowedException.class);
        // et un humain ne peut pas non plus forcer le mode direct si la politique l'interdit
        assertThatThrownBy(() -> manage.publish(HUMAN, DraftArtifactType.BPMN, KEY, stamp, PublicationMode.DIRECT))
                .isInstanceOf(DirectPublicationNotAllowedException.class);
        verify(deployBpmn, never()).deploy(any(), any());

        direct = true;
        ConfigPublication p = manage.publish(AGENT, DraftArtifactType.BPMN, KEY, stamp, PublicationMode.DIRECT);
        assertThat(p.mode()).isEqualTo(PublicationMode.DIRECT);
        verify(deployBpmn).deploy(any(), any());
    }

    @Test
    void aRefusedValidationLeavesNoConfigurationWrittenWithoutADeployedDiagram() {
        propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<bpmn/>", CONFIG, false);
        Instant stamp = bpmnStore.get(KEY).updatedAt();
        org.mockito.Mockito.doThrow(new InvalidBpmnException("reference vanished")).when(deployBpmn).validate(any(), any());

        assertThatThrownBy(() -> manage.publish(HUMAN, DraftArtifactType.BPMN, KEY, stamp, PublicationMode.MANUAL))
                .isInstanceOf(InvalidBpmnException.class);
        verifyNoInteractions(saveConfig);
        verify(deployBpmn, never()).deploy(any(), any());
        assertThat(bpmnStore).containsKey(KEY); // le brouillon reste, à corriger
    }

    @Test
    void anExistingConfigurationIsUpdatedNotRecreated() {
        WorkflowTypeConfig existing = new WorkflowTypeConfig(UUID.randomUUID(), TENANT, KEY, "Old", CONFIG.statusDefinitions(),
                List.of(DossierStatus.OUVERT), null, List.of(), "creator", "creator");
        when(configRepository.findByWorkflowKey(TENANT, KEY)).thenReturn(Optional.of(existing));
        propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<bpmn/>", CONFIG, false);

        manage.publish(HUMAN, DraftArtifactType.BPMN, KEY, bpmnStore.get(KEY).updatedAt(), PublicationMode.MANUAL);

        var captor = org.mockito.ArgumentCaptor.forClass(WorkflowTypeConfig.class);
        verify(saveConfig).update(captor.capture(), eq(HUMAN));
        assertThat(captor.getValue().id()).isEqualTo(existing.id());
        assertThat(captor.getValue().createdBy()).isEqualTo("creator");
        assertThat(captor.getValue().label()).isEqualTo("Claim");
    }

    @Test
    void anEditorDraftWithoutProposedConfigIsPublishedWithoutTouchingTheConfiguration() {
        bpmnStore.put(KEY, new BpmnDraft(TENANT, KEY, "<ui/>", Instant.parse("2026-10-01T09:00:00Z"), "alice"));

        ConfigPublication p = manage.publish(HUMAN, DraftArtifactType.BPMN, KEY, Instant.parse("2026-10-01T09:00:00Z"), PublicationMode.MANUAL);

        verifyNoInteractions(saveConfig);
        assertThat(p.draftProvenance()).isEqualTo(Provenance.UI);
    }

    @Test
    void aDmnDraftIsPublishedAndJournaled() {
        propose.proposeDmn(AGENT, Provenance.MCP, "table", "<dmn/>", "note", false);
        Instant stamp = dmnStore.get("table").updatedAt();

        ConfigPublication p = manage.publish(HUMAN, DraftArtifactType.DMN, "table", stamp, PublicationMode.MANUAL);

        verify(deployDmn).deploy(eq("table.dmn"), any());
        assertThat(p.version()).isEqualTo(7);
        assertThat(p.artifactType()).isEqualTo(DraftArtifactType.DMN);
    }

    @Test
    void publishingOrDiscardingAMissingDraftIs404Material() {
        assertThatThrownBy(() -> manage.publish(HUMAN, DraftArtifactType.BPMN, KEY, Instant.now(), PublicationMode.MANUAL))
                .isInstanceOf(DraftNotFoundException.class);
        assertThatThrownBy(() -> manage.discard(HUMAN, DraftArtifactType.DMN, "table", null)).isInstanceOf(DraftNotFoundException.class);
    }

    @Test
    void discardRemovesTheDraft() {
        propose.proposeDmn(AGENT, Provenance.MCP, "table", "<dmn/>", null, false);
        manage.discard(HUMAN, DraftArtifactType.DMN, "table", dmnStore.get("table").updatedAt());
        assertThat(dmnStore).isEmpty();
    }

    @Test
    void theListShowsProvenanceAuthorPublishedVersionAndAnIndicativeDiff() {
        propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<a>\n<b/>\n<c/>\n</a>", CONFIG, false);
        when(workflowDeployment.findDeployedXml(TENANT, KEY)).thenReturn(Optional.of("<a>\n<b/>\n<d/>\n</a>".getBytes(StandardCharsets.UTF_8)));
        when(workflowDeployment.findDeploymentInfo(TENANT, KEY)).thenReturn(Optional.of(new ProcessDeploymentInfo(KEY, 3, 0)));
        propose.proposeDmn(AGENT, Provenance.MCP, "table", "<dmn/>", "n", false);
        when(dmnDeployment.findDeploymentInfo(any(), any())).thenReturn(Optional.empty());

        List<PendingDraft> list = manage.listPending(HUMAN);

        assertThat(list).hasSize(2);
        PendingDraft bpmn = list.stream().filter(d -> d.type() == DraftArtifactType.BPMN).findFirst().orElseThrow();
        assertThat(bpmn.provenance()).isEqualTo(Provenance.MCP);
        assertThat(bpmn.author()).isEqualTo("agent-1");
        assertThat(bpmn.hasProposedConfig()).isTrue();
        assertThat(bpmn.publishedVersion()).isEqualTo(3);
        assertThat(bpmn.addedLines()).isEqualTo(1);
        assertThat(bpmn.removedLines()).isEqualTo(1);
        PendingDraft dmn = list.stream().filter(d -> d.type() == DraftArtifactType.DMN).findFirst().orElseThrow();
        assertThat(dmn.publishedVersion()).isNull();
        assertThat(dmn.note()).isEqualTo("n");
    }

    @Test
    void aNonAdminCannotListPublishOrDiscard() {
        assertThatThrownBy(() -> manage.listPending(VIEWER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> manage.publish(VIEWER, DraftArtifactType.BPMN, KEY, Instant.now(), PublicationMode.MANUAL))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> manage.discard(VIEWER, DraftArtifactType.BPMN, KEY, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aHumanTakeoverKeepsTheOriginOfTheProposedConfigurationInTheJournal_reviewD6M1() {
        propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<bpmn/>", CONFIG, false);
        // reprise humaine dans l'éditeur (SaveBpmnDraftUseCaseImpl conserve config + origine)
        BpmnDraft agent = bpmnStore.get(KEY);
        Instant later = agent.updatedAt().plusSeconds(60);
        bpmnStore.put(KEY, new BpmnDraft(TENANT, KEY, "<edited/>", later, "alice", Provenance.UI, agent.proposedConfig(), null,
                agent.configProvenance(), agent.configAuthor()));

        ConfigPublication p = manage.publish(HUMAN, DraftArtifactType.BPMN, KEY, later, PublicationMode.MANUAL);

        assertThat(p.draftProvenance()).isEqualTo(Provenance.UI);
        assertThat(p.draftAuthor()).isEqualTo("alice");
        assertThat(p.configProvenance()).as("la config reste attribuée à l'agent").isEqualTo(Provenance.MCP);
        assertThat(p.configAuthor()).isEqualTo("agent-1");
    }

    @Test
    void thePendingListExposesTheProposedConfigurationAndItsOrigin_reviewD6M1() {
        propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<bpmn/>", CONFIG, false);
        when(workflowDeployment.findDeployedXml(any(), any())).thenReturn(Optional.empty());
        when(workflowDeployment.findDeploymentInfo(any(), any())).thenReturn(Optional.empty());
        PendingDraft shown = manage.listPending(HUMAN).getFirst();
        assertThat(shown.proposedConfig()).isEqualTo(CONFIG);
        assertThat(shown.configProvenance()).isEqualTo(Provenance.MCP);
        assertThat(shown.configAuthor()).isEqualTo("agent-1");
    }

    @Test
    void aFailedDeploymentRestoresThePreviousConfigurationAndKeepsTheDraft_reviewD6M2() {
        WorkflowTypeConfig existing = new WorkflowTypeConfig(UUID.randomUUID(), TENANT, KEY, "Old", CONFIG.statusDefinitions(),
                List.of(DossierStatus.OUVERT), null, List.of(), "creator", "creator");
        when(configRepository.findByWorkflowKey(TENANT, KEY)).thenReturn(Optional.of(existing));
        propose.proposeBpmn(AGENT, Provenance.MCP, KEY, "<bpmn/>", CONFIG, false);
        when(deployBpmn.deploy(any(), any())).thenThrow(new IllegalStateException("engine down"));

        assertThatThrownBy(() -> manage.publish(HUMAN, DraftArtifactType.BPMN, KEY, bpmnStore.get(KEY).updatedAt(), PublicationMode.MANUAL))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("engine down");

        var captor = org.mockito.ArgumentCaptor.forClass(WorkflowTypeConfig.class);
        verify(saveConfig, org.mockito.Mockito.times(2)).update(captor.capture(), eq(HUMAN));
        assertThat(captor.getAllValues().get(0).label()).isEqualTo("Claim");
        assertThat(captor.getAllValues().get(1)).as("l'ancienne configuration est remise").isSameAs(existing);
        assertThat(bpmnStore).containsKey(KEY);
        assertThat(journal).isEmpty();
    }

    @Test
    void aJournalFailureAfterDeploymentDoesNotReportAFailure_reviewD6L4() {
        propose.proposeDmn(AGENT, Provenance.MCP, "table", "<dmn/>", null, false);
        when(publications.save(any())).thenThrow(new IllegalStateException("db down"));

        ConfigPublication p = manage.publish(HUMAN, DraftArtifactType.DMN, "table", dmnStore.get("table").updatedAt(), PublicationMode.MANUAL);

        assertThat(p.version()).isEqualTo(7);
        verify(deployDmn).deploy(any(), any());
    }

    @Test
    void aDirectPublicationWithoutTimestampIsAllowedOnlyForTheAgentsOwnAutomatedDraft_reviewD6L3() {
        direct = true;
        bpmnStore.put(KEY, new BpmnDraft(TENANT, KEY, "<human/>", Instant.parse("2026-10-01T09:00:00Z"), "alice"));
        assertThatThrownBy(() -> manage.publish(AGENT, DraftArtifactType.BPMN, KEY, null, PublicationMode.DIRECT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expectedUpdatedAt");
        propose.proposeDmn(AGENT, Provenance.MCP, "table", "<dmn/>", null, false);
        assertThat(manage.publish(AGENT, DraftArtifactType.DMN, "table", null, PublicationMode.DIRECT).mode()).isEqualTo(PublicationMode.DIRECT);
        when(dmnParser.extractDecisionKey(any())).thenReturn("other");
        propose.proposeDmn(OTHER_AGENT, Provenance.MCP, "other", "<dmn/>", null, false);
        assertThatThrownBy(() -> manage.publish(AGENT, DraftArtifactType.DMN, "other", null, PublicationMode.DIRECT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void discardRefusesADraftReplacedSinceItWasSeen_reviewD6L2() {
        propose.proposeDmn(AGENT, Provenance.MCP, "table", "<dmn/>", null, false);
        Instant seen = dmnStore.get("table").updatedAt();
        dmnStore.put("table", new DmnDraft(TENANT, "table", "<new/>", seen.plusSeconds(1), "agent-1", Provenance.MCP, null, null));
        assertThatThrownBy(() -> manage.discard(HUMAN, DraftArtifactType.DMN, "table", seen)).isInstanceOf(DraftChangedException.class);
        assertThat(dmnStore).containsKey("table");
    }
}
