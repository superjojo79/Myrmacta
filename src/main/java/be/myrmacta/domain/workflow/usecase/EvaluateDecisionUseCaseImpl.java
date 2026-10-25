package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.DecisionEvaluationResult;
import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import be.myrmacta.domain.workflow.model.UnknownDecisionKeyException;
import be.myrmacta.domain.workflow.port.in.EvaluateDecisionUseCase;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.DmnEvaluationPort;
import java.util.Map;
import java.util.Objects;

public final class EvaluateDecisionUseCaseImpl implements EvaluateDecisionUseCase {

    private final String tenantId;
    private final DmnDeploymentPort deploymentPort;
    private final DmnEvaluationPort evaluationPort;
    private final TenantAdminGuard guard;

    public EvaluateDecisionUseCaseImpl(
            String tenantId,
            DmnDeploymentPort deploymentPort,
            DmnEvaluationPort evaluationPort,
            TenantAdminGuard guard) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
        this.evaluationPort = Objects.requireNonNull(evaluationPort, "evaluationPort");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @Override
    public DecisionEvaluationResult evaluate(
            AccessContext.User actor, String decisionKey, Integer version, Map<String, Object> inputs) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        String key = requireDecisionKey(decisionKey);
        int resolved = resolveVersion(key, version);
        Map<String, Object> safeInputs = inputs == null ? Map.of() : inputs;
        return evaluationPort.evaluate(tenantId, key, resolved, safeInputs);
    }

    private int resolveVersion(String decisionKey, Integer version) {
        DecisionTableDeploymentInfo latest = deploymentPort
                .findDeploymentInfo(tenantId, decisionKey)
                .orElseThrow(() -> new UnknownDecisionKeyException(
                        "No deployed DMN decision table found for key: " + decisionKey));
        if (version == null) {
            return latest.version();
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        return version;
    }

    static String requireDecisionKey(String decisionKey) {
        if (decisionKey == null || decisionKey.isBlank()) {
            throw new IllegalArgumentException("decisionKey is required");
        }
        return decisionKey.trim();
    }
}
