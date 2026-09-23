package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.DecisionEvaluationResult;
import java.util.Map;

public interface DmnEvaluationPort {

    /**
     * Evaluates an already deployed decision at {@code version}. {@code inputs} may be empty.
     * A table that matches no rule returns an empty row list. Engine failures are
     * {@link com.flowforge.domain.workflow.model.DecisionEvaluationException}.
     */
    DecisionEvaluationResult evaluate(String tenantId, String decisionKey, int version, Map<String, Object> inputs);
}
