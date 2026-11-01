package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.application.workflow.ManualStartOrchestrator;
import be.myrmacta.domain.connector.port.in.AttachInboundFilesUseCase;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.in.CreateDossierRelationUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierStatusUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import be.myrmacta.domain.mailroom.port.out.MailMessageIndex;
import be.myrmacta.domain.mailroom.port.out.ReplyWorkflowSettings;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.workflow.model.ProcessDeploymentInfo;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.dossier.ChildDossierRelationConfirmer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §113 (D-MAILROOM-THREAD) — sans fil sûr, le courrier suit le routage ordinaire, sans aucune écriture sur un autre dossier. */
class AttachToThreadDelegateTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

    private final MailMessageIndex index = mock(MailMessageIndex.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final DossierRelationRepository relations = mock(DossierRelationRepository.class);
    private final CreateDossierRelationUseCase createRelation = mock(CreateDossierRelationUseCase.class);
    private final DossierElementRepository elements = mock(DossierElementRepository.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final AttachInboundFilesUseCase attachFiles = mock(AttachInboundFilesUseCase.class);
    private final DossierWorkflowLinkRepository links = mock(DossierWorkflowLinkRepository.class);
    private final WorkflowParticipantPort participants = mock(WorkflowParticipantPort.class);
    private final SendNotificationUseCase notifications = mock(SendNotificationUseCase.class);
    private final WorkflowEnginePort engine = mock(WorkflowEnginePort.class);
    private final ReplyWorkflowSettings replySettings = mock(ReplyWorkflowSettings.class);
    private final WorkflowDeploymentPort deployments = mock(WorkflowDeploymentPort.class);
    private final WorkflowTypeConfigRepository workflowConfigs = mock(WorkflowTypeConfigRepository.class);
    private final DossierTypeRepository types = mock(DossierTypeRepository.class);
    private final ChildDossierRelationConfirmer childConfirmer = mock(ChildDossierRelationConfirmer.class);
    private final ManualStartOrchestrator orchestrator = mock(ManualStartOrchestrator.class);
    private final UpdateDossierStatusUseCase updateStatus = mock(UpdateDossierStatusUseCase.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, Object> written = new HashMap<>();
    private final UUID mailId = UUID.randomUUID();
    private final UUID threadId = UUID.randomUUID();
    private AttachToThreadDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new AttachToThreadDelegate(index, dossiers, relations, createRelation, elements, storage, attachFiles, links,
                participants, notifications, engine, new TenantProperties(TENANT), replySettings, deployments, workflowConfigs, types,
                childConfirmer, orchestrator, updateStatus);
        variables.put("dossierId", mailId.toString());
        variables.put("mailFrom", "compta@fournisseur.example");
        variables.put("mailInReplyTo", "<sent-1@myrmacta.example>");
        variables.put("mailSenderAuthenticated", true);
        when(execution.getVariable(anyString())).thenAnswer(call -> variables.get(call.<String>getArgument(0)));
        org.mockito.Mockito.doAnswer(call -> written.put(call.getArgument(0), call.getArgument(1)))
                .when(execution).setVariable(anyString(), any());
        when(dossiers.findById(mailId)).thenReturn(Optional.of(dossier(mailId, "DOS-COURRIER-1", DossierStatus.EN_COURS)));
        when(index.find(TENANT, MailIndexEntry.Kind.MESSAGE_ID, "<sent-1@myrmacta.example>")).thenReturn(Optional.of(
                new MailIndexEntry(TENANT, MailIndexEntry.Kind.MESSAGE_ID, "<sent-1@myrmacta.example>", MailIndexEntry.Direction.OUT,
                        threadId, "compta@fournisseur.example", NOW)));
    }

    @Test
    void aMailOutsideAnyKnownThreadIsNew() {
        variables.remove("mailInReplyTo");

        delegate.execute(execution);

        assertNotThreaded("hors fil");
    }

    @Test
    void anUnattestedSenderNeverJoinsTheThread() {
        variables.put("mailSenderAuthenticated", false);

        delegate.execute(execution);

        assertNotThreaded("expéditeur non attesté");
    }

    @Test
    void aClosedThreadDossierIsNotReopenedByAReply() {
        when(dossiers.findById(threadId)).thenReturn(Optional.of(dossier(threadId, "DOS-FACTURE-7", DossierStatus.CLOTURE)));

        delegate.execute(execution);

        assertNotThreaded("dossier du fil clôturé : DOS-FACTURE-7");
    }

    /** Mode 3, SUR_DOSSIER (D-MAILROOM-REPLY-WORKFLOW) : jamais un second process sur un dossier qui en a déjà un. */
    @Test
    void theReplyWorkflowStartsOnTheThreadDossierOnlyWhenNoProcessIsActive() {
        attachable();
        when(replySettings.find(TENANT, "FACTURE")).thenReturn(Optional.of(new ReplyWorkflowSetting(TENANT, "FACTURE",
                ReplyWorkflowSetting.Mode.SUR_DOSSIER, "relancerFacture", null)));
        when(deployments.findDeploymentInfo(TENANT, "relancerFacture"))
                .thenReturn(Optional.of(new ProcessDeploymentInfo("relancerFacture", 1, 0)));
        WorkflowTypeConfig config = mock(WorkflowTypeConfig.class);
        when(config.allowsDossierStatus(any())).thenReturn(true);
        when(workflowConfigs.findByWorkflowKey(TENANT, "relancerFacture")).thenReturn(Optional.of(config));
        when(links.findActiveByDossierId(threadId)).thenReturn(Optional.of(mock(DossierWorkflowLink.class)));

        delegate.execute(execution);

        assertThat(written).containsEntry("filResultat", "RATTACHE").containsEntry("filReponseWorkflow", "NON")
                .containsEntry("filReponseMotif", "un process est déjà actif sur DOS-FACTURE-7");
        verifyNoInteractions(orchestrator);

        written.clear();
        when(links.findActiveByDossierId(threadId)).thenReturn(Optional.empty());

        delegate.execute(execution);

        assertThat(written).containsEntry("filReponseWorkflow", "DEMARRE_SUR_DOSSIER");
        org.mockito.Mockito.verify(orchestrator).start(org.mockito.ArgumentMatchers.eq(threadId),
                org.mockito.ArgumentMatchers.eq("relancerFacture"),
                org.mockito.ArgumentMatchers.eq(Map.of("courrierId", mailId.toString(), "courrierReference", "DOS-COURRIER-1")),
                any(), org.mockito.ArgumentMatchers.eq("workflow"));
    }

    @Test
    void anUndeployedReplyWorkflowIsNotStarted_butTheReplyStaysAttached() {
        attachable();
        when(replySettings.find(TENANT, "FACTURE")).thenReturn(Optional.of(new ReplyWorkflowSetting(TENANT, "FACTURE",
                ReplyWorkflowSetting.Mode.DOSSIER_ENFANT, "traiterReponse", "REPONSE_FACTURE")));
        when(deployments.findDeploymentInfo(TENANT, "traiterReponse")).thenReturn(Optional.empty());

        delegate.execute(execution);

        assertThat(written).containsEntry("filResultat", "RATTACHE").containsEntry("filReponseWorkflow", "NON")
                .containsEntry("filReponseMotif", "process non déployé : traiterReponse");
        verifyNoInteractions(orchestrator, childConfirmer);
    }

    @Test
    void withoutAReplyWorkflowTheTriageStillReadsAnExplicitValue() {
        attachable();
        when(replySettings.find(TENANT, "FACTURE")).thenReturn(Optional.empty());

        delegate.execute(execution);

        assertThat(written).containsEntry("filResultat", "RATTACHE").containsEntry("filReponseWorkflow", "AUCUN")
                .doesNotContainKey("filReponseMotif");
        verifyNoInteractions(orchestrator, childConfirmer);
    }

    /** DOSSIER_ENFANT : un type enfant qu'on ne saurait pas remplir est refusé avant toute écriture. */
    @Test
    void aChildTypeWithAnUnfillableRequiredFieldIsRefusedBeforeAnyWrite() {
        attachable();
        when(replySettings.find(TENANT, "FACTURE")).thenReturn(Optional.of(new ReplyWorkflowSetting(TENANT, "FACTURE",
                ReplyWorkflowSetting.Mode.DOSSIER_ENFANT, "traiterReponse", "REPONSE_FACTURE")));
        when(deployments.findDeploymentInfo(TENANT, "traiterReponse"))
                .thenReturn(Optional.of(new ProcessDeploymentInfo("traiterReponse", 1, 0)));
        WorkflowTypeConfig config = mock(WorkflowTypeConfig.class);
        when(config.allowsDossierStatus(any())).thenReturn(true);
        when(workflowConfigs.findByWorkflowKey(TENANT, "traiterReponse")).thenReturn(Optional.of(config));
        FieldDefinition montant = new FieldDefinition("montant", "Montant", FieldType.STRING, true, null, java.util.List.of(), null,
                null, null, null, null, null, null, null, null, null, null, null);
        when(types.findByCode(TENANT, "REPONSE_FACTURE")).thenReturn(Optional.of(new DossierType(UUID.randomUUID(), TENANT,
                "REPONSE_FACTURE", "Réponse", "traiterReponse", true, false, java.util.List.of(montant), null, false, null,
                java.util.List.of(), java.util.List.of(), "system", "system")));

        delegate.execute(execution);

        assertThat(written).containsEntry("filResultat", "RATTACHE").containsEntry("filReponseWorkflow", "NON")
                .containsEntry("filReponseMotif", "champ obligatoire du type enfant : montant");
        verifyNoInteractions(orchestrator, childConfirmer, updateStatus);
    }

    /**
     * D-MAILROOM-THREAD, 3e moyen (lot 7) : la référence du dossier dans l'objet, en secours, seulement si ce dossier a déjà
     * échangé un e-mail avec le domaine de l'expéditeur (les références sont séquentielles, donc devinables).
     */
    @Test
    void aReferenceInTheSubjectJoinsTheThread_onlyWhenTheDossierAlreadyExchangedWithTheSenderDomain() {
        attachable();
        variables.remove("mailInReplyTo");
        variables.put("mailSubject", "RE: votre dossier DOS-FACTURE-2026-000007 (rappel)");
        Dossier thread = dossiers.findById(threadId).orElseThrow();
        when(dossiers.findByReference(TENANT, "DOS-FACTURE-2026-000007")).thenReturn(Optional.of(thread));
        when(index.findByDossier(eq(TENANT), eq(threadId), anyInt())).thenReturn(java.util.List.of(new MailIndexEntry(TENANT,
                MailIndexEntry.Kind.MESSAGE_ID, "<f7@fournisseur.example>", MailIndexEntry.Direction.IN, threadId,
                "compta@fournisseur.example", NOW)));

        delegate.execute(execution);

        assertThat(written).containsEntry("filResultat", "RATTACHE").containsEntry("filDossierId", threadId.toString());
    }

    @Test
    void aGuessedReferenceFromADomainNeverExchangedWithIsANewMail() {
        attachable();
        variables.remove("mailInReplyTo");
        variables.put("mailFrom", "escroc@autre.example");
        variables.put("mailSubject", "RE: DOS-FACTURE-2026-000007");
        Dossier thread = dossiers.findById(threadId).orElseThrow();
        when(dossiers.findByReference(TENANT, "DOS-FACTURE-2026-000007")).thenReturn(Optional.of(thread));
        when(index.findByDossier(eq(TENANT), eq(threadId), anyInt())).thenReturn(java.util.List.of(new MailIndexEntry(TENANT,
                MailIndexEntry.Kind.MESSAGE_ID, "<f7@fournisseur.example>", MailIndexEntry.Direction.IN, threadId,
                "compta@fournisseur.example", NOW)));

        delegate.execute(execution);

        assertThat(written).containsEntry("filResultat", "NOUVEAU").containsEntry("filMotif", "hors fil");
        verifyNoInteractions(createRelation, attachFiles, engine);
    }

    /** Un fil rattachable : dossier « facture » ouvert, sans pièce à copier ni participant. */
    private void attachable() {
        when(dossiers.findById(threadId)).thenReturn(Optional.of(new Dossier(threadId, TENANT, "DOS-FACTURE-7", "FACTURE", "Facture",
                DossierStatus.EN_COURS, Map.of(), null, 0L, "workflow", NOW, "workflow", NOW, null, null, null)));
        when(relations.findByIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());
        when(elements.findByDossierIdOrderByAddedAtAsc(mailId)).thenReturn(java.util.List.of());
        when(execution.getProcessInstanceId()).thenReturn("pi-1");
        when(execution.getCurrentActivityId()).thenReturn("rattacher");
    }

    private void assertNotThreaded(String reason) {
        assertThat(written).containsEntry("filResultat", "NOUVEAU").containsEntry("filMotif", reason);
        verifyNoInteractions(createRelation, attachFiles, notifications, engine, storage, orchestrator, childConfirmer);
    }

    private static Dossier dossier(UUID id, String reference, DossierStatus status) {
        return new Dossier(id, TENANT, reference, "COURRIER", "Courrier", status, Map.of(), null, 0L, "trigger", NOW, "trigger", NOW,
                null, null, null);
    }
}
