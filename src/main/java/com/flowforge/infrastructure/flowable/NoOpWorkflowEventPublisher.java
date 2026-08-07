package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.model.WorkflowEvent;
import com.flowforge.domain.workflow.port.out.WorkflowEventPublisher;
import org.springframework.stereotype.Component;

@Component
class NoOpWorkflowEventPublisher implements WorkflowEventPublisher {

    @Override
    public void publish(WorkflowEvent event) {
        // Intentional no-op (D-PUBLISHER): notifications are dispatched only via
        // notificationDispatchTask / POST /api/notifications/send, not WorkflowEventPublisher.
    }
}
