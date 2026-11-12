package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.DmnXmlVersions;
import be.myrmacta.domain.workflow.model.DecisionTableDeploymentResult;
import be.myrmacta.domain.workflow.model.DecisionTableVersionInfo;
import be.myrmacta.domain.workflow.model.DecisionVersionAlreadyCurrentException;
import be.myrmacta.domain.workflow.model.UnknownDecisionKeyException;
import be.myrmacta.domain.workflow.model.UnknownDecisionVersionException;
import be.myrmacta.domain.workflow.port.in.RedeployDecisionTableUseCase;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import java.util.Objects;

public final class RedeployDecisionTableUseCaseImpl implements RedeployDecisionTableUseCase {

    private final String tenantId;
    private final DmnDeploymentPort deploymentPort;
    private final DmnParserPort dmnParserPort;
    private final TenantAdminGuard guard;

    public RedeployDecisionTableUseCaseImpl(
            String tenantId, DmnDeploymentPort deploymentPort, DmnParserPort dmnParserPort, TenantAdminGuard guard) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
        this.dmnParserPort = Objects.requireNonNull(dmnParserPort, "dmnParserPort");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @Override
    public DecisionTableDeploymentResult redeploy(AccessContext.User actor, String decisionKey, int version) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        String key = EvaluateDecisionUseCaseImpl.requireDecisionKey(decisionKey);
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        ListSnapshot snapshot = load(key);
        if (version == snapshot.latest) {
            throw new DecisionVersionAlreadyCurrentException(
                    "Version " + version + " is already the current version of " + key);
        }
        boolean known = snapshot.versions.stream().anyMatch(info -> info.version() == version);
        if (!known) {
            throw new UnknownDecisionVersionException(
                    "No deployed version " + version + " for decision key: " + key);
        }
        byte[] xml = deploymentPort
                .findDeployedXml(tenantId, key, version)
                .orElseThrow(() -> new UnknownDecisionVersionException(
                        "No deployed version " + version + " for decision key: " + key));
        // TODO §130 (D-DMN-OPEN-CONVERT) : une ancienne version 1.1 devient 1.3 à la republication.
        byte[] normalized = DmnXmlVersions.to13(xml);
        String extracted = dmnParserPort.extractDecisionKey(normalized);
        if (!extracted.equals(key)) {
            throw new IllegalArgumentException(
                    "decisionKey (" + key + ") does not match the DMN decision id (" + extracted + ")");
        }
        return deploymentPort.deploy(tenantId, key + ".dmn", normalized);
    }

    private ListSnapshot load(String decisionKey) {
        var versions = deploymentPort.findAllVersions(tenantId, decisionKey);
        if (versions.isEmpty()) {
            throw new UnknownDecisionKeyException("No deployed DMN decision table found for key: " + decisionKey);
        }
        int latest = versions.stream().mapToInt(DecisionTableVersionInfo::version).max().orElseThrow();
        return new ListSnapshot(versions, latest);
    }

    private record ListSnapshot(java.util.List<DecisionTableVersionInfo> versions, int latest) {}
}
