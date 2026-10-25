package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.DecisionEvaluationResult;
import java.util.Map;

public interface EvaluateDecisionUseCase {

    /**
     * Evaluates a deployed decision. {@code version} null means the latest deployed version.
     * A table that matches no rule returns an empty row list.
     */
    DecisionEvaluationResult evaluate(
            AccessContext.User actor, String decisionKey, Integer version, Map<String, Object> inputs);
}
