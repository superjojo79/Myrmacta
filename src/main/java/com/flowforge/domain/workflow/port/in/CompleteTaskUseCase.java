package com.flowforge.domain.workflow.port.in;

import java.util.Map;

public interface CompleteTaskUseCase {

    void complete(String taskId, String dossierId, Map<String, Object> variables);
}
