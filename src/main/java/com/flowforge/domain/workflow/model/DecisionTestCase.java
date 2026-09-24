package com.flowforge.domain.workflow.model;

import java.util.List;
import java.util.Map;

public record DecisionTestCase(Map<String, Object> inputs, List<Map<String, Object>> expectedRows) {}
