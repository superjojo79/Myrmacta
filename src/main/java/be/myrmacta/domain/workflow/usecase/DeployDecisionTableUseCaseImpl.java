package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.workflow.DmnXmlVersions;
import be.myrmacta.domain.workflow.model.DecisionTableDeploymentResult;
import be.myrmacta.domain.workflow.port.in.DeployDecisionTableUseCase;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.DmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import java.util.Objects;

public final class DeployDecisionTableUseCaseImpl implements DeployDecisionTableUseCase {

    private final String tenantId;
    private final DmnDeploymentPort deploymentPort;
    private final DmnParserPort dmnParserPort;
    private final DmnDraftRepository draftRepository;

    public DeployDecisionTableUseCaseImpl(
            String tenantId,
            DmnDeploymentPort deploymentPort,
            DmnParserPort dmnParserPort,
            DmnDraftRepository draftRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
        this.dmnParserPort = Objects.requireNonNull(dmnParserPort, "dmnParserPort");
        this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
    }

    @Override
    public DecisionTableDeploymentResult deploy(String filename, byte[] dmnXml) {
        // TODO §130 (D-DMN-OPEN-CONVERT) : stocker en 1.3 pour que l'éditeur réouvre la table.
        byte[] normalized = DmnXmlVersions.to13(dmnXml);
        String decisionKey = dmnParserPort.extractDecisionKey(normalized);
        DecisionTableDeploymentResult result = deploymentPort.deploy(tenantId, filename, normalized);
        // Mirrors D-DESIGN-DRAFT-LIFECYCLE (BpmnDraft) -- a successful deployment retires the
        // draft for this key, avoiding a stale draft silently diverging from what is deployed.
        draftRepository.deleteByTenantIdAndDecisionKey(tenantId, decisionKey);
        return result;
    }
}
