package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.DecisionTableVersionInfo;
import be.myrmacta.domain.workflow.model.UnknownDecisionKeyException;
import be.myrmacta.domain.workflow.port.in.ListDecisionTableVersionsUseCase;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ListDecisionTableVersionsUseCaseImpl implements ListDecisionTableVersionsUseCase {

    private final String tenantId;
    private final DmnDeploymentPort deploymentPort;
    private final TenantAdminGuard guard;

    public ListDecisionTableVersionsUseCaseImpl(
            String tenantId, DmnDeploymentPort deploymentPort, TenantAdminGuard guard) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @Override
    public List<DecisionTableVersionInfo> list(AccessContext.User actor, String decisionKey) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        String key = EvaluateDecisionUseCaseImpl.requireDecisionKey(decisionKey);
        List<DecisionTableVersionInfo> versions = deploymentPort.findAllVersions(tenantId, key);
        if (versions.isEmpty()) {
            throw new UnknownDecisionKeyException("No deployed DMN decision table found for key: " + key);
        }
        return versions.stream()
                .sorted(Comparator.comparingInt(DecisionTableVersionInfo::version))
                .toList();
    }
}
