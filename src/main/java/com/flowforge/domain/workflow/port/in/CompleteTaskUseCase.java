package com.flowforge.domain.workflow.port.in;

import java.util.Map;

public interface CompleteTaskUseCase {

    void complete(String taskId, Map<String, Object> variables);
}
