package com.flowforge.domain.signature.usecase;

import com.flowforge.domain.connector.model.CallbackAmbiguousCorrelationException;
import com.flowforge.domain.connector.model.CallbackNoMatchingInstanceException;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.port.in.DispatchSignatureOutcomesUseCase;
import com.flowforge.domain.signature.port.out.SignatureMetricsPort;
import com.flowforge.domain.signature.port.out.SignatureRequestRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * TODO §72 D-SIGNATURE-DISPATCH — pose {@code signatureOutcome} et {@code signatureRequestId} sur le
 * process en attente du message {@code signatureCompleted}. Une issue produite avant l'abonnement du
 * process (échec à l'émission) lève {@link CallbackNoMatchingInstanceException} : la demande reste non
 * répartie et est reprise au passage suivant, jusqu'à {@code maxAttempts} (alerte, puis abandon
 * consultable). {@code outcome_dispatched_at} n'est posé qu'une fois : reprise idempotente.
 */
public final class DispatchSignatureOutcomesUseCaseImpl implements DispatchSignatureOutcomesUseCase {

    private final SignatureRequestRepository repository;
    private final WorkflowEnginePort workflowEngine;
    private final SignatureMetricsPort metrics;
    private final Clock clock;
    private final int maxAttempts;
    private final int batchSize;

    public DispatchSignatureOutcomesUseCaseImpl(
            SignatureRequestRepository repository,
            WorkflowEnginePort workflowEngine,
            SignatureMetricsPort metrics,
            Clock clock,
            int maxAttempts,
            int batchSize) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.workflowEngine = Objects.requireNonNull(workflowEngine, "workflowEngine");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
    }

    @Override
    public boolean dispatch(UUID requestId) {
        Optional<SignatureRequest> loaded = repository.findById(requestId);
        if (loaded.isEmpty()) {
            return false;
        }
        if (loaded.get().outcomeDispatchedAt() != null) {
            return true;
        }
        return loaded.get().isDispatchPending() && dispatchOne(loaded.get());
    }

    @Override
    public int dispatchPending() {
        int resumed = 0;
        for (SignatureRequest request : repository.findTerminalNotDispatched(maxAttempts, batchSize)) {
            if (dispatchOne(request)) {
                resumed++;
            }
        }
        return resumed;
    }

    private boolean dispatchOne(SignatureRequest request) {
        try {
            workflowEngine.correlateMessageOnExecution(
                    MESSAGE_NAME,
                    CORRELATION_VARIABLE,
                    request.id().toString(),
                    Map.of(
                            OUTCOME_VARIABLE, request.status().outcome().name(),
                            CORRELATION_VARIABLE, request.id().toString()));
        } catch (CallbackNoMatchingInstanceException | CallbackAmbiguousCorrelationException ex) {
            // TODO §79 (D5) : « aucune instance » peut simplement signifier qu'un autre exécutant vient de reprendre le process
            // (la souscription au message n'est consommée qu'une fois). On le relit avant de compter une tentative, sinon une
            // fausse alerte « dispatchGaveUp » apparaît après maxAttempts passages.
            Optional<SignatureRequest> fresh = repository.findById(request.id());
            if (fresh.isPresent() && fresh.get().outcomeDispatchedAt() != null) {
                return false;
            }
            SignatureRequest retried = request.withDispatchAttempts(request.dispatchAttempts() + 1);
            if (repository.updateRequest(retried)) {
                metrics.dispatchRetry();
                if (retried.dispatchAttempts() >= maxAttempts) {
                    metrics.dispatchGaveUp();
                }
            }
            return false;
        }
        return repository.updateRequest(request.dispatched(clock.instant()));
    }
}
