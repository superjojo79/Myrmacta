package com.flowforge.domain.workflow.model;

import java.util.List;

public record DecisionTestReport(String decisionKey, int version, List<DecisionTestCaseResult> results) {}
