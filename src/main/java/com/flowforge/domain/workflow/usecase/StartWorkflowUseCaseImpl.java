package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.workflow.model.StartWorkflowCommand;
import com.flowforge.domain.workflow.model.WorkflowInstance;
import com.flowforge.domain.workflow.model.WorkflowStartedEvent;
import com.flowforge.domain.workflow.port.in.StartWorkflowUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowEventPublisher;
import com.flowforge.domain.workflow.port.out.WorkflowRepository;
import java.time.Instant;
import java.util.Objects;

public class StartWorkflowUseCaseImpl implements StartWorkflowUseCase {

    private final WorkflowRepository repository;
    private final WorkflowEventPublisher publisher;

    public StartWorkflowUseCaseImpl(WorkflowRepository repository, WorkflowEventPublisher publisher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public WorkflowInstance start(StartWorkflowCommand cmd) {
        WorkflowInstance instance = WorkflowInstance.create(cmd);
        repository.save(instance);
        publisher.publish(
                new WorkflowStartedEvent(instance.id(), Instant.now(), instance.processKey()));
        return instance;
    }
}
