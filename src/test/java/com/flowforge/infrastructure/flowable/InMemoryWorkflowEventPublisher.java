package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.model.WorkflowEvent;
import com.flowforge.domain.workflow.port.out.WorkflowEventPublisher;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryWorkflowEventPublisher implements WorkflowEventPublisher {

    private final CopyOnWriteArrayList<WorkflowEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void publish(WorkflowEvent event) {
        events.add(Objects.requireNonNull(event, "event"));
    }

    public List<WorkflowEvent> published() {
        return List.copyOf(events);
    }
}
