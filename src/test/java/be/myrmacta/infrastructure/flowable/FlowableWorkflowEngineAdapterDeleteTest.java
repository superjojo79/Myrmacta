package be.myrmacta.infrastructure.flowable;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableWorkflowEngineAdapterDeleteTest {

    private RuntimeService runtimeService;
    private FlowableWorkflowEngineAdapter adapter;

    @BeforeEach
    void setUp() {
        runtimeService = mock(RuntimeService.class);
        adapter = new FlowableWorkflowEngineAdapter(runtimeService, mock(TaskService.class));
    }

    @Test
    void deleteProcessInstanceBestEffort_callsRuntimeService() {
        adapter.deleteProcessInstanceBestEffort("pi-1");
        verify(runtimeService).deleteProcessInstance("pi-1", "purge");
    }

    @Test
    void deleteProcessInstanceBestEffort_missingPi_isNoOp() {
        doThrow(new org.flowable.common.engine.api.FlowableObjectNotFoundException("gone"))
                .when(runtimeService)
                .deleteProcessInstance("pi-missing", "purge");

        adapter.deleteProcessInstanceBestEffort("pi-missing");
    }

    @Test
    void deleteProcessInstanceBestEffort_blank_skips() {
        adapter.deleteProcessInstanceBestEffort("  ");
        verifyNoInteractions(runtimeService);
    }
}
