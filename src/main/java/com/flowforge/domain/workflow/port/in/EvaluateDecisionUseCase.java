package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.DecisionEvaluationResult;
import java.util.Map;

public interface EvaluateDecisionUseCase {

    /**
     * Evaluates a deployed decision. {@code version} null means the latest deployed version.
     * A table that matches no rule returns an empty row list.
     */
    DecisionEvaluationResult evaluate(
            AccessContext.User actor, String decisionKey, Integer version, Map<String, Object> inputs);
}
