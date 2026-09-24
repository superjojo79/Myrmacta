package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.DecisionEvaluationResult;
import com.flowforge.domain.workflow.model.DecisionTestCase;
import com.flowforge.domain.workflow.model.DecisionTestCaseResult;
import com.flowforge.domain.workflow.model.DecisionTestReport;
import com.flowforge.domain.workflow.port.in.EvaluateDecisionUseCase;
import com.flowforge.domain.workflow.port.in.TestDecisionUseCase;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TestDecisionUseCaseImpl implements TestDecisionUseCase {

    static final int MAX_CASES = 100;

    private final EvaluateDecisionUseCase evaluateDecisionUseCase;
    private final TenantAdminGuard guard;

    public TestDecisionUseCaseImpl(EvaluateDecisionUseCase evaluateDecisionUseCase, TenantAdminGuard guard) {
        this.evaluateDecisionUseCase = Objects.requireNonNull(evaluateDecisionUseCase, "evaluateDecisionUseCase");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @Override
    public DecisionTestReport test(
            AccessContext.User actor,
            String decisionKey,
            Integer version,
            List<DecisionTestCase> cases,
            boolean coerceScalars) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        String key = EvaluateDecisionUseCaseImpl.requireDecisionKey(decisionKey);
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("cases must not be empty");
        }
        if (cases.size() > MAX_CASES) {
            throw new IllegalArgumentException("at most " + MAX_CASES + " cases are allowed");
        }
        List<DecisionTestCaseResult> results = new ArrayList<>();
        int resolvedVersion = 0;
        for (int i = 0; i < cases.size(); i++) {
            DecisionTestCase testCase = cases.get(i);
            if (testCase == null) {
                throw new IllegalArgumentException("cases must not contain null");
            }
            Map<String, Object> inputs = testCase.inputs() == null ? Map.of() : testCase.inputs();
            DecisionEvaluationResult evaluation = evaluateDecisionUseCase.evaluate(actor, key, version, inputs);
            resolvedVersion = evaluation.version();
            String mismatch = mismatch(testCase.expectedRows(), evaluation.rows(), coerceScalars);
            results.add(new DecisionTestCaseResult(i, mismatch == null, evaluation.rows(), mismatch));
        }
        return new DecisionTestReport(key, resolvedVersion, List.copyOf(results));
    }

    private static String mismatch(
            List<Map<String, Object>> expected, List<Map<String, Object>> actual, boolean coerceScalars) {
        List<Map<String, Object>> expectedRows = expected == null ? List.of() : expected;
        List<Map<String, Object>> actualRows = actual == null ? List.of() : actual;
        if (expectedRows.size() != actualRows.size()) {
            return "row count mismatch: expected " + expectedRows.size() + ", actual " + actualRows.size();
        }
        for (int i = 0; i < expectedRows.size(); i++) {
            String rowMismatch = rowMismatch(expectedRows.get(i), actualRows.get(i), coerceScalars, i);
            if (rowMismatch != null) {
                return rowMismatch;
            }
        }
        return null;
    }

    private static String rowMismatch(
            Map<String, Object> expected, Map<String, Object> actual, boolean coerceScalars, int index) {
        Map<String, Object> expectedRow = expected == null ? Map.of() : expected;
        Map<String, Object> actualRow = actual == null ? Map.of() : actual;
        for (Map.Entry<String, Object> entry : expectedRow.entrySet()) {
            if (!actualRow.containsKey(entry.getKey())) {
                return "row " + index + ", missing key " + entry.getKey();
            }
            Object actualValue = actualRow.get(entry.getKey());
            if (!scalarEquals(entry.getValue(), actualValue, coerceScalars)) {
                return "row " + index + ", key " + entry.getKey() + ": expected " + entry.getValue() + ", actual "
                        + actualValue;
            }
        }
        return null;
    }

    private static boolean scalarEquals(Object expected, Object actual, boolean coerceScalars) {
        if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
            return toBigDecimal(expectedNumber).compareTo(toBigDecimal(actualNumber)) == 0;
        }
        if (coerceScalars && expected instanceof String literal) {
            Object coerced = coerceLiteral(literal);
            if (coerced != literal && scalarEquals(coerced, actual, false)) {
                return true;
            }
        }
        return Objects.equals(expected, actual);
    }

    private static Object coerceLiteral(String literal) {
        String trimmed = literal.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return Boolean.FALSE;
        }
        if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
            try {
                return new BigDecimal(trimmed);
            } catch (NumberFormatException ex) {
                return literal;
            }
        }
        return literal;
    }

    private static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (number instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        if (number instanceof Double || number instanceof Float) {
            return new BigDecimal(number.toString());
        }
        return BigDecimal.valueOf(number.longValue());
    }
}
