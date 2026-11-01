package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.application.workflow.ManualStartOrchestrator;
import be.myrmacta.domain.deadline.port.out.DeadlineSettings;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.in.UpdateDossierFieldsUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierStatusUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.model.UserGroupNotFoundException;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.model.ProcessDeploymentInfo;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.dossier.ChildDossierRelationConfirmer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §113 (D-MAILROOM-ROUTE) — toute décision hors catalogue envoie le courrier à la file de tri, sans aucune écriture métier. */
class RouteMailDelegateTest {

    private static final String TENANT = "tenant_dev";

    private final ChildDossierRelationConfirmer confirmer = mock(ChildDossierRelationConfirmer.class);
    private final ManualStartOrchestrator orchestrator = mock(ManualStartOrchestrator.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final DossierTypeRepository types = mock(DossierTypeRepository.class);
    private final WorkflowDeploymentPort deployments = mock(WorkflowDeploymentPort.class);
    private final UserGroupsUseCase groups = mock(UserGroupsUseCase.class);
    private final WorkflowLinkGroupRepository linkGroups = mock(WorkflowLinkGroupRepository.class);
    private final UpdateDossierFieldsUseCase updateFields = mock(UpdateDossierFieldsUseCase.class);
    private final UpdateDossierStatusUseCase updateStatus = mock(UpdateDossierStatusUseCase.class);
    private final DeadlineSettings deadlineSettings = mock(DeadlineSettings.class);
    private final WorkflowTypeConfigRepository workflowConfigs = mock(WorkflowTypeConfigRepository.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, Object> written = new HashMap<>();
    private final UUID mailId = UUID.randomUUID();
    private final UUID linkId = UUID.randomUUID();
    private RouteMailDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new RouteMailDelegate(confirmer, orchestrator, dossiers, types, deployments, groups, linkGroups, updateFields,
                updateStatus, deadlineSettings, workflowConfigs, new TenantProperties(TENANT));
        variables.put("dossierId", mailId.toString());
        variables.put("dossierWorkflowLinkId", linkId.toString());
        variables.put("routageAction", "OUVRIR");
        variables.put("routageService", "COMPTABILITE");
        variables.put("routageTypeDossier", "FACTURE");
        variables.put("routageProcessus", "traiterFacture");
        when(execution.getVariable(anyString())).thenAnswer(call -> variables.get(call.<String>getArgument(0)));
        org.mockito.Mockito.doAnswer(call -> written.put(call.getArgument(0), call.getArgument(1)))
                .when(execution).setVariable(anyString(), any());
        ServiceTask task = new ServiceTask();
        task.addAttribute(attribute("groupeTri", "COURRIER"));
        when(execution.getCurrentFlowElement()).thenReturn(task);
        when(dossiers.findById(mailId)).thenReturn(Optional.of(mail()));
        when(types.findByCode(TENANT, "COURRIER")).thenReturn(Optional.of(type("COURRIER", true, field("service", false))));
        when(types.findByCode(TENANT, "FACTURE")).thenReturn(Optional.of(type("FACTURE", true, field("objet", true))));
        when(groups.get(anyString())).thenAnswer(call -> new UserGroup(TENANT, call.getArgument(0), "Groupe", null, Set.of()));
        when(deployments.findDeploymentInfo(TENANT, "traiterFacture"))
                .thenReturn(Optional.of(new ProcessDeploymentInfo("traiterFacture", 1, 0)));
        WorkflowTypeConfig config = mock(WorkflowTypeConfig.class);
        when(config.allowsDossierStatus(any())).thenReturn(true);
        when(workflowConfigs.findByWorkflowKey(TENANT, "traiterFacture")).thenReturn(Optional.of(config));
    }

    /** Revue de clôture : ce que le démarrage refuserait est refusé avant la création du dossier métier. */
    @Test
    void aProcessWithoutConfig_refusingOpen_orRefusingItsStartVariablesSendsTheMailToSorting() {
        when(workflowConfigs.findByWorkflowKey(TENANT, "traiterFacture")).thenReturn(Optional.empty());
        assertSentToSorting("configuration de workflow absente : traiterFacture");

        WorkflowTypeConfig closedOnly = mock(WorkflowTypeConfig.class);
        when(workflowConfigs.findByWorkflowKey(TENANT, "traiterFacture")).thenReturn(Optional.of(closedOnly));
        written.clear();
        org.mockito.Mockito.clearInvocations(linkGroups, updateFields);
        assertSentToSorting("statut OUVERT refusé par traiterFacture");

        WorkflowTypeConfig open = mock(WorkflowTypeConfig.class);
        when(open.allowsDossierStatus(any())).thenReturn(true);
        when(workflowConfigs.findByWorkflowKey(TENANT, "traiterFacture")).thenReturn(Optional.of(open));
        when(orchestrator.bindStartVariables(eq("traiterFacture"), any()))
                .thenThrow(new IllegalArgumentException("Workflow start variable is required: montant"));
        written.clear();
        org.mockito.Mockito.clearInvocations(linkGroups, updateFields);
        assertSentToSorting("variables de démarrage refusées par traiterFacture : Workflow start variable is required: montant");
    }

    @Test
    void anUnknownGroupSendsTheMailToSorting() {
        when(groups.get("COMPTABILITE")).thenThrow(new UserGroupNotFoundException("COMPTABILITE"));

        assertSentToSorting("groupe inconnu : COMPTABILITE");
    }

    @Test
    void anInactiveTypeSendsTheMailToSorting() {
        when(types.findByCode(TENANT, "FACTURE")).thenReturn(Optional.of(type("FACTURE", false, field("objet", true))));

        assertSentToSorting("type de dossier inconnu ou inactif : FACTURE");
    }

    @Test
    void anUndeployedProcessSendsTheMailToSorting() {
        when(deployments.findDeploymentInfo(TENANT, "traiterFacture")).thenReturn(Optional.empty());

        assertSentToSorting("process non déployé : traiterFacture");
    }

    @Test
    void anInvalidDelaySendsTheMailToSorting() {
        variables.put("routageDelaiJours", "-3");

        assertSentToSorting("délai invalide : -3");
    }

    @Test
    void aRequiredFieldOfTheTargetTypeMissingFromTheMailSendsItToSorting() {
        when(types.findByCode(TENANT, "FACTURE")).thenReturn(Optional.of(type("FACTURE", true, field("montant", true))));

        assertSentToSorting("champ obligatoire du type FACTURE absent du courrier : montant");
    }

    @Test
    void withoutAUsableSortingGroupTheMailStillGoesToSorting_withoutAGroup() {
        variables.put("routageAction", "TRIER");
        when(groups.get("COURRIER")).thenThrow(new UserGroupNotFoundException("COURRIER"));

        delegate.execute(execution);

        assertThat(written).containsEntry("routageResultat", "A_TRIER").containsEntry("routageMotif", "action TRIER");
        verifyNoInteractions(linkGroups, updateFields, confirmer, orchestrator);
    }

    private void assertSentToSorting(String reason) {
        delegate.execute(execution);

        assertThat(written).containsEntry("routageResultat", "A_TRIER").containsEntry("routageMotif", reason);
        verify(linkGroups).assign(TENANT, linkId, "COURRIER");
        verify(updateFields).update(any(), any());
        verifyNoInteractions(confirmer, updateStatus);
        verify(orchestrator, never()).start(any(), any(), any(), any(), any(), any());
        verify(dossiers, never()).save(any());
        verify(linkGroups, never()).assign(eq(TENANT), eq(linkId), eq("COMPTABILITE"));
    }

    private Dossier mail() {
        Instant now = Instant.parse("2026-10-31T08:00:00Z");
        return new Dossier(mailId, TENANT, "DOS-COURRIER-2026-000001", "COURRIER", "trigger:x", DossierStatus.EN_COURS,
                Map.of("objet", "Facture 117", "expediteur", "compta@fournisseur.example"), null, 0L, "trigger", now, "trigger", now,
                null, null, null);
    }

    private static DossierType type(String code, boolean active, FieldDefinition field) {
        return new DossierType(UUID.randomUUID(), TENANT, code, code, "wf", active, false, List.of(field), null, false, null, List.of(),
                List.of(), "system", "system");
    }

    private static FieldDefinition field(String name, boolean required) {
        return new FieldDefinition(name, name, FieldType.STRING, required, null, List.of(), null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    private static org.flowable.bpmn.model.ExtensionAttribute attribute(String name, String value) {
        var attribute = new org.flowable.bpmn.model.ExtensionAttribute();
        attribute.setNamespace(DeployedActivityParameters.FLOWABLE_NAMESPACE);
        attribute.setNamespacePrefix("flowable");
        attribute.setName(name);
        attribute.setValue(value);
        return attribute;
    }
}
