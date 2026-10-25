package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.CallbackAmbiguousCorrelationException;
import be.myrmacta.domain.connector.model.CallbackNoMatchingInstanceException;
import java.util.List;
import java.util.Map;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableWorkflowEngineAdapterCorrelateMessageTest {

    private RuntimeService runtimeService;
    private ExecutionQuery executionQuery;
    private FlowableWorkflowEngineAdapter adapter;

    @BeforeEach
    void setUp() {
        runtimeService = mock(RuntimeService.class);
        executionQuery = mock(ExecutionQuery.class, RETURNS_SELF);
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        adapter = new FlowableWorkflowEngineAdapter(runtimeService, mock(TaskService.class));
    }

    @Test
    void singleMatch_resumesExecution() {
        Execution execution = mock(Execution.class);
        when(execution.getId()).thenReturn("exec-1");
        when(executionQuery.list()).thenReturn(List.of(execution));

        Map<String, Object> variables = Map.of("payload", "value");
        adapter.correlateMessage("callbackReceived", "correlationKey", "42", variables);

        verify(executionQuery).messageEventSubscriptionName("callbackReceived");
        verify(executionQuery).processVariableValueEquals("correlationKey", "42");
        verify(runtimeService).messageEventReceived("callbackReceived", "exec-1", variables);
    }

    @Test
    void noMatch_throwsNoMatchingInstance() {
        when(executionQuery.list()).thenReturn(List.of());

        assertThatThrownBy(() -> adapter.correlateMessage("callbackReceived", "correlationKey", "42", Map.of()))
                .isInstanceOf(CallbackNoMatchingInstanceException.class)
                .satisfies(ex -> {
                    var typed = (CallbackNoMatchingInstanceException) ex;
                    assertThat(typed.messageName()).isEqualTo("callbackReceived");
                    assertThat(typed.correlationKey()).isEqualTo("correlationKey");
                    assertThat(typed.correlationValue()).isEqualTo("42");
                });
    }

    @Test
    void concurrentRaceOnMessageEventReceived_treatedAsNoMatchingInstance_neverPropagatesRawFlowableException() {
        // D-CALLBACK-RACE (design NFR, écart Phase 7) : the execution matched by list() can be
        // resumed by a concurrent callback before this call reaches the engine.
        Execution execution = mock(Execution.class);
        when(execution.getId()).thenReturn("exec-1");
        when(executionQuery.list()).thenReturn(List.of(execution));
        doThrow(new FlowableException("execution has not subscribed to the signal"))
                .when(runtimeService)
                .messageEventReceived("callbackReceived", "exec-1", Map.of());

        assertThatThrownBy(() -> adapter.correlateMessage("callbackReceived", "correlationKey", "42", Map.of()))
                .isInstanceOf(CallbackNoMatchingInstanceException.class);
    }

    @Test
    void multipleMatches_throwsAmbiguousCorrelation() {
        when(executionQuery.list()).thenReturn(List.of(mock(Execution.class), mock(Execution.class)));

        assertThatThrownBy(() -> adapter.correlateMessage("callbackReceived", "correlationKey", "42", Map.of()))
                .isInstanceOf(CallbackAmbiguousCorrelationException.class)
                .satisfies(ex -> assertThat(((CallbackAmbiguousCorrelationException) ex).matchCount()).isEqualTo(2));
    }
}
