package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.model.DecisionTableVersionInfo;
import com.flowforge.domain.workflow.model.DecisionVersionAlreadyCurrentException;
import com.flowforge.domain.workflow.model.UnknownDecisionKeyException;
import com.flowforge.domain.workflow.model.UnknownDecisionVersionException;
import com.flowforge.domain.workflow.port.in.RedeployDecisionTableUseCase;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
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
        String extracted = dmnParserPort.extractDecisionKey(xml);
        if (!extracted.equals(key)) {
            throw new IllegalArgumentException(
                    "decisionKey (" + key + ") does not match the DMN decision id (" + extracted + ")");
        }
        return deploymentPort.deploy(tenantId, key + ".dmn", xml);
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
