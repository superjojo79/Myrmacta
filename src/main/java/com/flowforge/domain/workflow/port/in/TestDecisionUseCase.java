package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.DecisionTestCase;
import com.flowforge.domain.workflow.model.DecisionTestReport;
import java.util.List;

public interface TestDecisionUseCase {

    /**
     * Runs {@code cases} against a deployed decision. At most 100 cases.
     * {@code coerceScalars} is true for CSV (literal cells compared as numbers/booleans)
     * and false for JSON (native types kept).
     */
    DecisionTestReport test(
            AccessContext.User actor,
            String decisionKey,
            Integer version,
            List<DecisionTestCase> cases,
            boolean coerceScalars);
}
