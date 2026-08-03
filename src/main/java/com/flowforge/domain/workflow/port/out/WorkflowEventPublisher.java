package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.WorkflowEvent;

public interface WorkflowEventPublisher {

    void publish(WorkflowEvent event);
}
