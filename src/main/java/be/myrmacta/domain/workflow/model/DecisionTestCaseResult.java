package be.myrmacta.domain.workflow.model;

import java.util.List;
import java.util.Map;

public record DecisionTestCaseResult(
        int index, boolean passed, List<Map<String, Object>> actualRows, String failureMessage) {}
