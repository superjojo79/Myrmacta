package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.flowforge.domain.workflow.model.StartWorkflowCommand;
import com.flowforge.domain.workflow.model.WorkflowInstance;
import com.flowforge.domain.workflow.model.WorkflowStartedEvent;
import com.flowforge.domain.workflow.model.WorkflowStatus;
import com.flowforge.domain.workflow.port.in.StartWorkflowUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowEventPublisher;
import com.flowforge.domain.workflow.port.out.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StartWorkflowUseCaseImplTest {

    private WorkflowRepository repository;
    private WorkflowEventPublisher publisher;
    private StartWorkflowUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(WorkflowRepository.class);
        publisher = mock(WorkflowEventPublisher.class);
        useCase = new StartWorkflowUseCaseImpl(repository, publisher);
    }

    @Test
    void start_persistsRunningInstanceAndPublishesEvent() {
        var cmd = new StartWorkflowCommand("demo-process", "tenant-a");

        WorkflowInstance instance = useCase.start(cmd);

        assertThat(instance.status()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(instance.processKey()).isEqualTo("demo-process");
        assertThat(instance.tenantId()).isEqualTo("tenant-a");

        ArgumentCaptor<WorkflowInstance> saved = ArgumentCaptor.forClass(WorkflowInstance.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(instance);

        ArgumentCaptor<WorkflowStartedEvent> event = ArgumentCaptor.forClass(WorkflowStartedEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().instance()).isEqualTo(instance);
    }

    @Test
    void start_doesNotPublishWhenSaveFails() {
        var cmd = new StartWorkflowCommand("demo-process", "tenant-a");
        doThrow(new RuntimeException("db down")).when(repository).save(any());

        assertThatThrownBy(() -> useCase.start(cmd))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");

        verify(publisher, never()).publish(any());
    }
}
