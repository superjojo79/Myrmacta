package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.model.WorkflowEvent;
import com.flowforge.domain.workflow.port.out.WorkflowEventPublisher;
import org.springframework.stereotype.Component;

@Component
class NoOpWorkflowEventPublisher implements WorkflowEventPublisher {

    @Override
    public void publish(WorkflowEvent event) {
        // Notifications are delivered by a later feature; publishing is a no-op in V1.
    }
}
