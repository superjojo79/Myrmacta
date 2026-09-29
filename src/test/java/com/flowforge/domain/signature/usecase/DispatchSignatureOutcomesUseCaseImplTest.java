package com.flowforge.domain.signature.usecase;

import static com.flowforge.domain.signature.SignatureFixtures.NOW;
import static com.flowforge.domain.signature.SignatureFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.flowforge.domain.connector.model.CallbackAmbiguousCorrelationException;
import com.flowforge.domain.connector.model.CallbackNoMatchingInstanceException;
import com.flowforge.domain.signature.InMemorySignatureRepository;
import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.SignatureOutcome;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.port.out.SignatureMetricsPort;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** S-DISP-1 à 4, S-OUT-3 — répartiteur d'issue avec reprise (TODO §72, D-SIGNATURE-DISPATCH). */
class DispatchSignatureOutcomesUseCaseImplTest {

    private final InMemorySignatureRepository repository = new InMemorySignatureRepository();
    private final WorkflowEnginePort engine = mock(WorkflowEnginePort.class);
    private final SignatureMetricsPort metrics = mock(SignatureMetricsPort.class);
    private DispatchSignatureOutcomesUseCaseImpl useCase;
    private int seq;

    @BeforeEach
    void setUp() {
        useCase = new DispatchSignatureOutcomesUseCaseImpl(repository, engine, metrics,
                Clock.fixed(NOW, ZoneOffset.UTC), 3, 50);
    }

    private SignatureRequest closed(SignatureOutcome outcome) {
        SignatureRequest r = request(UUID.randomUUID(), SignatureMode.PARALLEL, "proc-" + (++seq));
        repository.insert(r, List.of());
        SignatureRequest terminal = r.closed(outcome, null, NOW);
        repository.updateRequest(terminal);
        return repository.findById(r.id()).orElseThrow();
    }

    @Test
    void resumesTheProcessWithTheOutcomeAndMarksTheRequestDispatchedOnce() {
        SignatureRequest r = closed(SignatureOutcome.SIGNED);

        assertThat(useCase.dispatch(r.id())).isTrue();

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(engine).correlateMessageOnExecution(eq("signatureCompleted"), eq("signatureRequestId"), eq(r.id().toString()),
                vars.capture());
        assertThat(vars.getValue()).containsEntry("signatureOutcome", "SIGNED")
                .containsEntry("signatureRequestId", r.id().toString());
        assertThat(repository.findById(r.id()).orElseThrow().outcomeDispatchedAt()).isEqualTo(NOW);
        // idempotence : un second appel (ou le job) ne reprend pas deux fois
        assertThat(useCase.dispatch(r.id())).isTrue();
        assertThat(useCase.dispatchPending()).isZero();
        verify(engine, times(1)).correlateMessageOnExecution(anyString(), anyString(), any(), any());
    }

    @Test
    void processNotYetSubscribedIsRetriedUntilItSucceeds() {
        // S-DISP-1
        SignatureRequest r = closed(SignatureOutcome.FAILED);
        doThrow(new CallbackNoMatchingInstanceException("signatureCompleted", "signatureRequestId", r.id().toString()))
                .when(engine).correlateMessageOnExecution(anyString(), anyString(), any(), any());

        assertThat(useCase.dispatch(r.id())).isFalse();
        assertThat(repository.findById(r.id()).orElseThrow().dispatchAttempts()).isEqualTo(1);
        assertThat(repository.findById(r.id()).orElseThrow().outcomeDispatchedAt()).isNull();
        verify(metrics).dispatchRetry();

        doNothing().when(engine).correlateMessageOnExecution(anyString(), anyString(), any(), any());
        assertThat(useCase.dispatchPending()).isEqualTo(1);
        assertThat(repository.findById(r.id()).orElseThrow().outcomeDispatchedAt()).isEqualTo(NOW);
    }

    @Test
    void givesUpAtTheCeilingWithAnAlertAndStopsPicking() {
        // S-DISP-3
        SignatureRequest r = closed(SignatureOutcome.EXPIRED);
        doThrow(new CallbackNoMatchingInstanceException("m", "k", "v"))
                .when(engine).correlateMessageOnExecution(anyString(), anyString(), any(), any());

        for (int i = 0; i < 6; i++) {
            useCase.dispatchPending();
        }

        assertThat(repository.findById(r.id()).orElseThrow().dispatchAttempts()).isEqualTo(3);
        verify(metrics, times(3)).dispatchRetry();
        verify(metrics, times(1)).dispatchGaveUp();
        verify(engine, times(3)).correlateMessageOnExecution(anyString(), anyString(), any(), any());
        assertThat(repository.findById(r.id())).isPresent();
    }

    @Test
    void ambiguousCorrelationIsNeverResolvedSilently() {
        SignatureRequest r = closed(SignatureOutcome.DECLINED);
        doThrow(new CallbackAmbiguousCorrelationException("m", "k", "v", 2))
                .when(engine).correlateMessageOnExecution(anyString(), anyString(), any(), any());

        assertThat(useCase.dispatch(r.id())).isFalse();
        assertThat(repository.findById(r.id()).orElseThrow().outcomeDispatchedAt()).isNull();
    }

    @Test
    void severalRequestsAreCorrelatedByTheirOwnId() {
        // S-DISP-4 : la corrélation ne mélange pas les issues
        SignatureRequest a = closed(SignatureOutcome.SIGNED);
        SignatureRequest b = closed(SignatureOutcome.DECLINED);

        assertThat(useCase.dispatchPending()).isEqualTo(2);

        verify(engine).correlateMessageOnExecution(eq("signatureCompleted"), eq("signatureRequestId"), eq(a.id().toString()),
                eq(Map.of("signatureOutcome", "SIGNED", "signatureRequestId", a.id().toString())));
        verify(engine).correlateMessageOnExecution(eq("signatureCompleted"), eq("signatureRequestId"), eq(b.id().toString()),
                eq(Map.of("signatureOutcome", "DECLINED", "signatureRequestId", b.id().toString())));
    }

    @Test
    void pendingOrUnknownRequestsAreNotDispatched() {
        SignatureRequest pending = request(UUID.randomUUID(), SignatureMode.PARALLEL, "proc-pending");
        repository.insert(pending, List.of());

        assertThat(useCase.dispatch(pending.id())).isFalse();
        assertThat(useCase.dispatch(UUID.randomUUID())).isFalse();
        verify(engine, never()).correlateMessageOnExecution(anyString(), anyString(), any(), any());
    }
}
