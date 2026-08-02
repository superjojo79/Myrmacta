package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompleteTaskUseCaseImplTest {

    private WorkflowEnginePort workflowEnginePort;
    private CompleteTaskUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        workflowEnginePort = mock(WorkflowEnginePort.class);
        useCase = new CompleteTaskUseCaseImpl(workflowEnginePort);
    }

    @Test
    void complete_forwardsVariablesToEnginePort() {
        useCase.complete("task-1", Map.of("decision", "OK"));

        verify(workflowEnginePort).completeTask("task-1", Map.of("decision", "OK"));
    }

    @Test
    void complete_rejectsReservedVariableKeys() {
        assertThatThrownBy(() -> useCase.complete("task-1", Map.of("dossierId", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        verifyNoInteractions(workflowEnginePort);
    }
}
