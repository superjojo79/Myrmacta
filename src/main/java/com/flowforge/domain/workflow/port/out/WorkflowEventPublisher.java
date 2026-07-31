package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.WorkflowStartedEvent;

public interface WorkflowEventPublisher {

    void publish(WorkflowStartedEvent event);
}
