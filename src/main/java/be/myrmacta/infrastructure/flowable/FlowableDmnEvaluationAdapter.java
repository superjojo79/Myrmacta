package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.workflow.model.DecisionEvaluationException;
import be.myrmacta.domain.workflow.model.DecisionEvaluationResult;
import be.myrmacta.domain.workflow.model.UnknownDecisionVersionException;
import be.myrmacta.domain.workflow.port.out.DmnEvaluationPort;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class FlowableDmnEvaluationAdapter implements DmnEvaluationPort {

    private static final Logger log = LoggerFactory.getLogger(FlowableDmnEvaluationAdapter.class);

    private final DmnRepositoryService dmnRepositoryService;
    private final DmnDecisionService dmnDecisionService;

    FlowableDmnEvaluationAdapter(DmnRepositoryService dmnRepositoryService, DmnDecisionService dmnDecisionService) {
        this.dmnRepositoryService = Objects.requireNonNull(dmnRepositoryService, "dmnRepositoryService");
        this.dmnDecisionService = Objects.requireNonNull(dmnDecisionService, "dmnDecisionService");
    }

    @Override
    public DecisionEvaluationResult evaluate(
            String tenantId, String decisionKey, int version, Map<String, Object> inputs) {
        log.info("dmn.evaluate decisionKey={} version={}", decisionKey, version);
        DmnDecision decision = dmnRepositoryService.createDecisionQuery()
                .decisionKey(decisionKey)
                .decisionVersion(version)
                .decisionTenantId(tenantId)
                .singleResult();
        if (decision == null) {
            throw new UnknownDecisionVersionException(
                    "No deployed version " + version + " for decision key: " + decisionKey);
        }
        try {
            List<Map<String, Object>> rows = dmnDecisionService.createExecuteDecisionBuilder()
                    .decisionKey(decisionKey)
                    .tenantId(tenantId)
                    .parentDeploymentId(decision.getDeploymentId())
                    .disableHistory()
                    .variables(inputs == null ? Map.of() : inputs)
                    .execute();
            return new DecisionEvaluationResult(decisionKey, version, rows == null ? List.of() : List.copyOf(rows));
        } catch (RuntimeException ex) {
            String detail = ex.getMessage();
            String message = "Decision evaluation failed for " + decisionKey + " version " + version;
            if (detail != null && !detail.isBlank()) {
                message = message + ": " + detail;
            }
            throw new DecisionEvaluationException(message, ex);
        }
    }
}
