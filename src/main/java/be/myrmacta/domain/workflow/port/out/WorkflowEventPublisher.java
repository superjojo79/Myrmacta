package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.workflow.model.WorkflowEvent;

public interface WorkflowEventPublisher {

    void publish(WorkflowEvent event);
}
