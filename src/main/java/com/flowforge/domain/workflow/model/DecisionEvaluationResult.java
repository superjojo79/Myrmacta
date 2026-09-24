package com.flowforge.domain.workflow.model;

import java.util.List;
import java.util.Map;

public record DecisionEvaluationResult(String decisionKey, int version, List<Map<String, Object>> rows) {}
