package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.WorkflowInstance;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository {

    void save(WorkflowInstance instance);

    Optional<WorkflowInstance> findById(UUID id);
}
