package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.precheck.PrecheckDeploymentRules;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition;
import be.myrmacta.domain.signature.model.SignatureBlockDefinition;
import be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalAvailabilityPort;
import java.util.List;

import be.myrmacta.domain.workflow.model.BpmnDeploymentResult;
import be.myrmacta.domain.workflow.model.UnknownDecisionKeyException;
import be.myrmacta.domain.workflow.model.UnknownWorkflowKeyException;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.palette.BlockReferenceVerifier;
import be.myrmacta.domain.workflow.palette.InactiveBlockReference;
import be.myrmacta.domain.workflow.port.in.DeployBpmnProcessUseCase;
import be.myrmacta.domain.workflow.port.out.BpmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.util.Objects;
import java.util.Optional;

public final class DeployBpmnProcessUseCaseImpl implements DeployBpmnProcessUseCase {

    private final String tenantId;
    private final WorkflowDeploymentPort deploymentPort;
    private final WorkflowTypeConfigRepository configRepository;
    private final BpmnParserPort bpmnParserPort;
    private final BpmnDraftRepository draftRepository;
    private final DmnDeploymentPort dmnDeploymentPort;
    private final PortalAvailabilityPort portalAvailability;
    private final SignatureAvailabilityPort signatureAvailability;
    private final PrecheckDeploymentRules precheckRules;
    private final BlockReferenceVerifier blockReferenceVerifier;

    public DeployBpmnProcessUseCaseImpl(
            String tenantId,
            WorkflowDeploymentPort deploymentPort,
            WorkflowTypeConfigRepository configRepository,
            BpmnParserPort bpmnParserPort,
            BpmnDraftRepository draftRepository,
            DmnDeploymentPort dmnDeploymentPort,
            PortalAvailabilityPort portalAvailability,
            SignatureAvailabilityPort signatureAvailability,
            BlockReferenceVerifier blockReferenceVerifier,
            PrecheckDeploymentRules precheckRules) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.bpmnParserPort = Objects.requireNonNull(bpmnParserPort, "bpmnParserPort");
        this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
        this.dmnDeploymentPort = Objects.requireNonNull(dmnDeploymentPort, "dmnDeploymentPort");
        this.portalAvailability = Objects.requireNonNull(portalAvailability, "portalAvailability");
        this.signatureAvailability = Objects.requireNonNull(signatureAvailability, "signatureAvailability");
        this.blockReferenceVerifier = Objects.requireNonNull(blockReferenceVerifier, "blockReferenceVerifier");
        this.precheckRules = Objects.requireNonNull(precheckRules, "precheckRules");
    }

    @Override
    public BpmnDeploymentResult deploy(String filename, byte[] bpmnXml) {
        String processDefinitionKey = bpmnParserPort.extractProcessDefinitionKey(bpmnXml);
        WorkflowTypeConfig config = configRepository.findByWorkflowKey(tenantId, processDefinitionKey)
                .orElseThrow(() -> new UnknownWorkflowKeyException(
                        "No WorkflowTypeConfig found for key: " + processDefinitionKey));
        validate(bpmnXml, Optional.of(config));
        BpmnDeploymentResult result = deploymentPort.deploy(tenantId, filename, bpmnXml);
        // D-DESIGN-DRAFT-LIFECYCLE: a successful deployment retires the draft for this key --
        // avoids a stale draft silently diverging from what is actually deployed.
        draftRepository.deleteByTenantIdAndProcessDefinitionKey(tenantId, processDefinitionKey);
        return result;
    }

    @Override
    public void validate(byte[] bpmnXml, Optional<WorkflowTypeConfig> effectiveConfig) {
        validate(bpmnXml, effectiveConfig, BlockReferenceVerifier.Mode.STRICT);
    }

    @Override
    public List<InactiveBlockReference> validateDraft(byte[] bpmnXml, Optional<WorkflowTypeConfig> effectiveConfig) {
        return validate(bpmnXml, effectiveConfig, BlockReferenceVerifier.Mode.DRAFT);
    }

    private List<InactiveBlockReference> validate(
            byte[] bpmnXml, Optional<WorkflowTypeConfig> effectiveConfig, BlockReferenceVerifier.Mode mode) {
        String processDefinitionKey = bpmnParserPort.extractProcessDefinitionKey(bpmnXml);
        // TODO §61 D1 -- a serviceTask flowable:type="dmn" is only accepted by the BPMN parser in
        // shape (literal decisionTableReferenceKey); the parser alone cannot know which decision
        // tables are actually deployed, so that check happens here.
        for (String decisionKey : bpmnParserPort.extractReferencedDecisionKeys(bpmnXml)) {
            dmnDeploymentPort
                    .findDeploymentInfo(tenantId, decisionKey)
                    .orElseThrow(() -> new UnknownDecisionKeyException(
                            "No deployed DMN decision table found for key: " + decisionKey));
        }
        assertPortalTasksDeployable(bpmnParserPort.extractPortalTasks(bpmnXml));
        assertSignatureBlocksDeployable(bpmnParserPort.extractSignatureBlocks(bpmnXml));
        // TODO §106 (D-PRECHECK-BLOCK) : fonction activée, champs et pièces désignés contrôlés contre les types liés.
        precheckRules.verify(processDefinitionKey, bpmnParserPort.extractPrecheckBlocks(bpmnXml));
        // TODO §73 D-PALETTE-DEPLOY-REFERENCES : existence des ressources référencées par les attributs de brique.
        return blockReferenceVerifier.verify(
                processDefinitionKey, effectiveConfig, bpmnParserPort.extractBlockReferences(bpmnXml), mode);
    }

    /**
     * TODO §72 D-SIGNATURE-BPMN-MARKER / D-SIGNATURE-EXTERNAL-NEEDS-PORTAL — un bloc signature exige un
     * fournisseur actif ({@code app.signature.provider} autre que {@code none}) ; un signataire externe
     * exige le portail ; nombre de signataires et échéance restent sous les plafonds tenant.
     */
    private void assertSignatureBlocksDeployable(List<SignatureBlockDefinition> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        if (!signatureAvailability.providerActive()) {
            throw new InvalidBpmnException("BPMN signature block requires a signature provider "
                    + "(app.signature.provider): " + blocks.getFirst().activityId());
        }
        for (SignatureBlockDefinition block : blocks) {
            if (block.hasExternalSigner() && !signatureAvailability.portalEnabled()) {
                throw new InvalidBpmnException("BPMN signature block with an EXTERNAL signer requires the portal "
                        + "to be enabled (APP_PORTAL_ENABLED=true): " + block.activityId());
            }
            if (block.signers().size() > signatureAvailability.maxSigners()) {
                throw new InvalidBpmnException("BPMN signature block exceeds the maximum of "
                        + signatureAvailability.maxSigners() + " signers: " + block.activityId());
            }
            if (block.deadlineDays() != null && block.deadlineDays() > signatureAvailability.maxDeadlineDays()) {
                throw new InvalidBpmnException("BPMN signatureDeadlineDays exceeds the tenant ceiling of "
                        + signatureAvailability.maxDeadlineDays() + " days: " + block.activityId());
            }
        }
    }

    /**
     * TODO §68 D-PORTAL-ACTIVATION — une tâche externe exposerait une route publique : refusée tant
     * que le portail est désactivé sur l'instance. Même règle pour l'écran admin et MCP
     * {@code deploy_workflow}, qui passent tous deux par ce use case. Le plafond tenant de durée de
     * vie (D-PORTAL-TTL) est vérifié ici, le parseur ne connaissant pas la configuration.
     */
    private void assertPortalTasksDeployable(List<PortalTaskDefinition> portalTasks) {
        if (portalTasks.isEmpty()) {
            return;
        }
        if (!portalAvailability.isPortalEnabled()) {
            throw new InvalidBpmnException("BPMN external portal task requires the portal to be enabled "
                    + "(APP_PORTAL_ENABLED=true): " + portalTasks.getFirst().taskDefinitionKey());
        }
        int ceiling = portalAvailability.maxLinkTtlDays();
        for (PortalTaskDefinition task : portalTasks) {
            if (task.ttlDays() != null && task.ttlDays() > ceiling) {
                throw new InvalidBpmnException("BPMN portalLinkTtlDays exceeds the tenant ceiling of " + ceiling
                        + " days: " + task.taskDefinitionKey());
            }
            // TODO §104 (E2) — même règle que le portail éteint : pas de tâche qu'aucun tiers ne pourrait ouvrir.
            if (task.requiresStrongAuthentication() && !portalAvailability.isStrongAuthenticationEnabled()) {
                throw new InvalidBpmnException("BPMN portalIdentityField requires strong authentication of third "
                        + "parties to be enabled (APP_PORTAL_STRONG_AUTH_ENABLED=true): " + task.taskDefinitionKey());
            }
        }
    }
}
