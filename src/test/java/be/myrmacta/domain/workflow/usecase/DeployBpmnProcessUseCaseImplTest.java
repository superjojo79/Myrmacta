package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.workflow.model.BpmnDeploymentResult;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.model.StatusOutcome;
import be.myrmacta.domain.workflow.model.UnknownWorkflowKeyException;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.model.UnknownDecisionKeyException;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.out.BpmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeployBpmnProcessUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final byte[] BPMN_BYTES = "<bpmn/>".getBytes(StandardCharsets.UTF_8);

    private final WorkflowDeploymentPort deploymentPort = mock(WorkflowDeploymentPort.class);
    private final WorkflowTypeConfigRepository configRepository = mock(WorkflowTypeConfigRepository.class);
    private final BpmnParserPort bpmnParserPort = mock(BpmnParserPort.class);
    private final BpmnDraftRepository draftRepository = mock(BpmnDraftRepository.class);
    private final DmnDeploymentPort dmnDeploymentPort = mock(DmnDeploymentPort.class);
    private final be.myrmacta.domain.workflow.portal.port.out.PortalAvailabilityPort portalAvailability =
            mock(be.myrmacta.domain.workflow.portal.port.out.PortalAvailabilityPort.class);
    private final be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort signatureAvailability =
            mock(be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort.class);
    private final be.myrmacta.domain.workflow.palette.port.out.BlockReferenceLookupPort lookup =
            mock(be.myrmacta.domain.workflow.palette.port.out.BlockReferenceLookupPort.class);
    private final DeployBpmnProcessUseCaseImpl useCase = new DeployBpmnProcessUseCaseImpl(
            TENANT, deploymentPort, configRepository, bpmnParserPort, draftRepository, dmnDeploymentPort,
            portalAvailability, signatureAvailability,
            new be.myrmacta.domain.workflow.palette.BlockReferenceVerifier(TENANT, lookup),
            new be.myrmacta.domain.precheck.PrecheckDeploymentRules(TENANT, () -> false,
                    org.mockito.Mockito.mock(be.myrmacta.domain.dossier.port.out.DossierTypeRepository.class)));

    @Test
    void deploy_validBpmnWithKnownKey_delegatesToDeploymentPort() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of());
        BpmnDeploymentResult expected = new BpmnDeploymentResult("testProcess", 1, "deployment-1");
        when(deploymentPort.deploy(TENANT, "diagram.bpmn20.xml", BPMN_BYTES)).thenReturn(expected);

        BpmnDeploymentResult result = useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void deploy_blockReferenceToAMissingResource_isRefusedBeforeAnyDeployment() {
        // S-REFCHK-2 : D-PALETTE-DEPLOY-REFERENCES
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractBlockReferences(BPMN_BYTES)).thenReturn(java.util.List.of(
                new be.myrmacta.domain.workflow.palette.BlockParameterUsage(
                        "gen", "generate-document", "documentType", "documentTemplates", "absent")));
        when(lookup.status(TENANT, "documentTemplates", "absent"))
                .thenReturn(be.myrmacta.domain.workflow.palette.port.out.ReferenceStatus.MISSING);

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(be.myrmacta.domain.workflow.model.UnknownBlockReferenceException.class);

        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    private static final be.myrmacta.domain.workflow.palette.BlockParameterUsage DRAFT_TEMPLATE =
            new be.myrmacta.domain.workflow.palette.BlockParameterUsage(
                    "gen", "generate-document", "documentType", "documentTemplates", "nouveau");

    private void givenAReferenceToADraftTemplate() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of());
        when(bpmnParserPort.extractBlockReferences(BPMN_BYTES)).thenReturn(List.of(DRAFT_TEMPLATE));
        when(lookup.status(TENANT, "documentTemplates", "nouveau"))
                .thenReturn(be.myrmacta.domain.workflow.palette.port.out.ReferenceStatus.INACTIVE);
    }

    @Test
    void deployAndValidate_stillRefuseAnInactiveResource() {
        // TODO §93 D-DRAFT-REF-STATUS : déploiement, dryRun de l'écran et publication restent stricts.
        givenAReferenceToADraftTemplate();

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(be.myrmacta.domain.workflow.model.UnknownBlockReferenceException.class);
        assertThatThrownBy(() -> useCase.validate(BPMN_BYTES, Optional.of(smokeConfig())))
                .isInstanceOf(be.myrmacta.domain.workflow.model.UnknownBlockReferenceException.class);
        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void validateDraft_acceptsAnInactiveResourceAndReturnsIt() {
        givenAReferenceToADraftTemplate();

        assertThat(useCase.validateDraft(BPMN_BYTES, Optional.of(smokeConfig()))).containsExactly(
                new be.myrmacta.domain.workflow.palette.InactiveBlockReference(
                        "gen", "documentType", "documentTemplates", "nouveau"));
        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void validateDraft_keepsEveryOtherDeploymentCheck() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of("eligibilite"));
        when(dmnDeploymentPort.findDeploymentInfo(TENANT, "eligibilite")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.validateDraft(BPMN_BYTES, Optional.of(smokeConfig())))
                .isInstanceOf(UnknownDecisionKeyException.class);
    }

    @Test
    void validate_playsEveryDeploymentCheckWithTheSubmittedConfigAndDeploysNothing() {
        // S-DRY-1, S-DRY-2
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of("eligibilite"));
        when(dmnDeploymentPort.findDeploymentInfo(TENANT, "eligibilite")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.validate(BPMN_BYTES, Optional.of(smokeConfig())))
                .isInstanceOf(UnknownDecisionKeyException.class);

        verify(deploymentPort, never()).deploy(any(), any(), any());
        verify(configRepository, never()).findByWorkflowKey(any(), any());
        verify(draftRepository, never()).deleteByTenantIdAndProcessDefinitionKey(any(), any());
    }

    @Test
    void validate_acceptsAValidDocumentWithoutAStoredConfig() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of());

        useCase.validate(BPMN_BYTES, Optional.empty());

        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void deploy_referencedDecisionKeyNotDeployed_throwsUnknownDecisionKeyAndNeverCallsDeploymentPort() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of("eligibilite"));
        when(dmnDeploymentPort.findDeploymentInfo(TENANT, "eligibilite")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(UnknownDecisionKeyException.class);

        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void deploy_referencedDecisionKeyDeployed_delegatesToDeploymentPort() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of("eligibilite"));
        when(dmnDeploymentPort.findDeploymentInfo(TENANT, "eligibilite"))
                .thenReturn(Optional.of(new be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo(
                        "eligibilite", 1)));
        BpmnDeploymentResult expected = new BpmnDeploymentResult("testProcess", 1, "deployment-1");
        when(deploymentPort.deploy(TENANT, "diagram.bpmn20.xml", BPMN_BYTES)).thenReturn(expected);

        BpmnDeploymentResult result = useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void deploy_invalidBpmn_propagatesParserExceptionAndNeverCallsDeploymentPort() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES))
                .thenThrow(new InvalidBpmnException("not valid BPMN"));

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(InvalidBpmnException.class);

        verify(deploymentPort, never()).deploy(any(), any(), any());
        verify(configRepository, never()).findByWorkflowKey(any(), any());
    }

    @Test
    void deploy_unknownWorkflowKey_throwsUnknownWorkflowKeyAndNeverCallsDeploymentPort() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(UnknownWorkflowKeyException.class);

        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void deploy_success_deletesExistingDraftForDeployedKey() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of());
        when(deploymentPort.deploy(TENANT, "diagram.bpmn20.xml", BPMN_BYTES))
                .thenReturn(new BpmnDeploymentResult("testProcess", 1, "deployment-1"));

        useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES);

        verify(draftRepository).deleteByTenantIdAndProcessDefinitionKey(TENANT, "testProcess");
    }

    @Test
    void deploy_failure_keepsExistingDraftForKey() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES))
                .thenThrow(new InvalidBpmnException("not valid BPMN"));

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(InvalidBpmnException.class);

        verify(draftRepository, never()).deleteByTenantIdAndProcessDefinitionKey(any(), any());
    }

    private static WorkflowTypeConfig smokeConfig() {
        return new WorkflowTypeConfig(
                UUID.randomUUID(),
                TENANT,
                "testProcess",
                "Test",
                List.of(new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE)),
                List.of(DossierStatus.OUVERT),
                null,
                List.of());
    }

    private static be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition externalTask(Integer ttlDays) {
        return new be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition(
                "tiers", "avis",
                new be.myrmacta.domain.workflow.portal.model.PortalRecipientSource(
                        be.myrmacta.domain.workflow.portal.model.PortalRecipientSource.Kind.DOSSIER_FIELD, "email"),
                ttlDays);
    }

    @Test
    void deploy_externalTaskWhilePortalDisabled_isRejectedAndNeverDeployed() {
        // T-DEPLOY-1 (TODO §68, D-PORTAL-ACTIVATION)
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractPortalTasks(BPMN_BYTES)).thenReturn(java.util.List.of(externalTask(null)));
        when(portalAvailability.isPortalEnabled()).thenReturn(false);

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(be.myrmacta.domain.workflow.model.InvalidBpmnException.class)
                .hasMessageContaining("portal");
        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void deploy_externalTaskWhilePortalEnabled_isDeployed() {
        // T-DEPLOY-2
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of());
        when(bpmnParserPort.extractPortalTasks(BPMN_BYTES)).thenReturn(java.util.List.of(externalTask(30)));
        when(portalAvailability.isPortalEnabled()).thenReturn(true);
        when(portalAvailability.maxLinkTtlDays()).thenReturn(30);
        BpmnDeploymentResult expected = new BpmnDeploymentResult("testProcess", 1, "deployment-1");
        when(deploymentPort.deploy(TENANT, "diagram.bpmn20.xml", BPMN_BYTES)).thenReturn(expected);

        assertThat(useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES)).isEqualTo(expected);
    }

    @Test
    void deploy_externalTaskTtlAboveTenantCeiling_isRejected() {
        // T-PARSE-4 (plafond tenant, vérifié au déploiement : le parseur ne connaît pas la config)
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractPortalTasks(BPMN_BYTES)).thenReturn(java.util.List.of(externalTask(31)));
        when(portalAvailability.isPortalEnabled()).thenReturn(true);
        when(portalAvailability.maxLinkTtlDays()).thenReturn(30);

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(be.myrmacta.domain.workflow.model.InvalidBpmnException.class)
                .hasMessageContaining("portalLinkTtlDays");
        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    /** TODO §104 (E2, D-BEAUTH-ACTIVATION) — une tâche à authentification forte exige le réglage d'instance. */
    @Test
    void deploy_strongAuthenticationTaskWhileStrongAuthDisabled_isRejected() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractPortalTasks(BPMN_BYTES)).thenReturn(java.util.List.of(strongAuthTask()));
        when(portalAvailability.isPortalEnabled()).thenReturn(true);
        when(portalAvailability.maxLinkTtlDays()).thenReturn(30);
        when(portalAvailability.isStrongAuthenticationEnabled()).thenReturn(false);

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(be.myrmacta.domain.workflow.model.InvalidBpmnException.class)
                .hasMessageContaining("portalIdentityField")
                .hasMessageContaining("APP_PORTAL_STRONG_AUTH_ENABLED");
        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void deploy_strongAuthenticationTaskWhileStrongAuthEnabled_isDeployed() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of());
        when(bpmnParserPort.extractPortalTasks(BPMN_BYTES)).thenReturn(java.util.List.of(strongAuthTask()));
        when(portalAvailability.isPortalEnabled()).thenReturn(true);
        when(portalAvailability.maxLinkTtlDays()).thenReturn(30);
        when(portalAvailability.isStrongAuthenticationEnabled()).thenReturn(true);
        BpmnDeploymentResult expected = new BpmnDeploymentResult("testProcess", 1, "deployment-1");
        when(deploymentPort.deploy(TENANT, "diagram.bpmn20.xml", BPMN_BYTES)).thenReturn(expected);

        assertThat(useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES)).isEqualTo(expected);
    }

    private static be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition strongAuthTask() {
        return new be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition("tiers", "avis",
                be.myrmacta.domain.workflow.portal.model.PortalRecipientSource.parse("dossierField:email").orElseThrow(),
                null, new be.myrmacta.domain.workflow.portal.model.PortalIdentityField("nrnDemandeur"));
    }

    // ---- TODO §72 : blocs signature (S-PARSE-2 partie déploiement, S-STUB-7, S-PW-8)

    private be.myrmacta.domain.signature.model.SignatureBlockDefinition block(
            be.myrmacta.domain.signature.model.SignerKind kind, Integer deadlineDays, int signers) {
        var doc = be.myrmacta.domain.workflow.portal.model.PortalRecipientSource.parse("variable:doc").orElseThrow();
        var list = new java.util.ArrayList<be.myrmacta.domain.signature.model.SignatureBlockDefinition.SignerDefinition>();
        for (int i = 0; i < signers; i++) {
            list.add(new be.myrmacta.domain.signature.model.SignatureBlockDefinition.SignerDefinition(
                    be.myrmacta.domain.workflow.portal.model.PortalRecipientSource.parse("variable:s" + i)
                            .orElseThrow(),
                    kind, true, "S" + i));
        }
        return new be.myrmacta.domain.signature.model.SignatureBlockDefinition(
                "sign", doc, be.myrmacta.domain.signature.model.SignatureMode.PARALLEL, deadlineDays, list);
    }

    private void givenDeployableWithSignatureBlocks(
            java.util.List<be.myrmacta.domain.signature.model.SignatureBlockDefinition> blocks) {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of());
        when(bpmnParserPort.extractSignatureBlocks(BPMN_BYTES)).thenReturn(blocks);
        when(signatureAvailability.providerActive()).thenReturn(true);
        when(signatureAvailability.portalEnabled()).thenReturn(true);
        when(signatureAvailability.maxSigners()).thenReturn(3);
        when(signatureAvailability.maxDeadlineDays()).thenReturn(30);
    }

    @Test
    void deploy_signatureBlockWithoutProvider_isRejected() {
        givenDeployableWithSignatureBlocks(java.util.List.of(block(
                be.myrmacta.domain.signature.model.SignerKind.INTERNAL, null, 1)));
        when(signatureAvailability.providerActive()).thenReturn(false);

        assertThatThrownBy(() -> useCase.deploy("d.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(be.myrmacta.domain.workflow.model.InvalidBpmnException.class)
                .hasMessageContaining("signature provider");
        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void deploy_externalSignerWithoutPortal_isRejectedButInternalOnlyIsAccepted() {
        givenDeployableWithSignatureBlocks(java.util.List.of(block(
                be.myrmacta.domain.signature.model.SignerKind.EXTERNAL, null, 1)));
        when(signatureAvailability.portalEnabled()).thenReturn(false);
        assertThatThrownBy(() -> useCase.deploy("d.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(be.myrmacta.domain.workflow.model.InvalidBpmnException.class)
                .hasMessageContaining("portal");

        when(bpmnParserPort.extractSignatureBlocks(BPMN_BYTES)).thenReturn(java.util.List.of(block(
                be.myrmacta.domain.signature.model.SignerKind.INTERNAL, null, 1)));
        when(deploymentPort.deploy(TENANT, "d.bpmn20.xml", BPMN_BYTES))
                .thenReturn(new BpmnDeploymentResult("testProcess", 1, "dep"));
        assertThat(useCase.deploy("d.bpmn20.xml", BPMN_BYTES).processDefinitionKey()).isEqualTo("testProcess");
    }

    @Test
    void deploy_tooManySignersOrTooLongDeadline_isRejected() {
        givenDeployableWithSignatureBlocks(java.util.List.of(block(
                be.myrmacta.domain.signature.model.SignerKind.INTERNAL, null, 4)));
        assertThatThrownBy(() -> useCase.deploy("d.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(be.myrmacta.domain.workflow.model.InvalidBpmnException.class)
                .hasMessageContaining("maximum of 3");

        when(bpmnParserPort.extractSignatureBlocks(BPMN_BYTES)).thenReturn(java.util.List.of(block(
                be.myrmacta.domain.signature.model.SignerKind.INTERNAL, 31, 1)));
        assertThatThrownBy(() -> useCase.deploy("d.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(be.myrmacta.domain.workflow.model.InvalidBpmnException.class)
                .hasMessageContaining("ceiling of 30");
    }
}
