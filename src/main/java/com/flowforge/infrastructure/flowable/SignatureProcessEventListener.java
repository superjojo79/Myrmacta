package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.signature.port.in.CancelSignatureRequestsForProcessUseCase;
import jakarta.annotation.PostConstruct;
import java.util.Objects;
import java.util.Set;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;

/**
 * TODO §72 D-SIGNATURE-CANCEL-PROPAGATION — listener Flowable global (patron de
 * {@link PortalTaskEventListener}) : aucun listener à déclarer dans le BPMN, donc aucune nouvelle
 * surface dans l'allowlist. Quand un process se termine, est annulé ou supprimé, ses demandes de
 * signature encore ouvertes sont annulées et leurs liens invalidés (US-4, S-ADM-3).
 */
public class SignatureProcessEventListener extends AbstractFlowableEngineEventListener {

    private final RuntimeService runtimeService;
    private final CancelSignatureRequestsForProcessUseCase useCase;

    public SignatureProcessEventListener(RuntimeService runtimeService, CancelSignatureRequestsForProcessUseCase useCase) {
        super(Set.of(
                FlowableEngineEventType.PROCESS_COMPLETED,
                FlowableEngineEventType.PROCESS_CANCELLED,
                FlowableEngineEventType.ACTIVITY_CANCELLED,
                FlowableEngineEventType.ENTITY_DELETED));
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @PostConstruct
    void register() {
        runtimeService.addEventListener(
                this,
                FlowableEngineEventType.PROCESS_COMPLETED,
                FlowableEngineEventType.PROCESS_CANCELLED,
                FlowableEngineEventType.ACTIVITY_CANCELLED,
                FlowableEngineEventType.ENTITY_DELETED);
    }

    /**
     * Revue adversariale n°5 : une frontière ou un sous-processus interrompu supprime l'attente sans terminer le
     * process. La demande, retrouvée par la variable locale de l'exécution annulée, est annulée à son tour.
     */
    @Override
    protected void activityCancelled(org.flowable.engine.delegate.event.FlowableActivityCancelledEvent event) {
        String executionId = event.getExecutionId();
        if (executionId == null) {
            return;
        }
        Object requestId;
        try {
            requestId = runtimeService.getVariableLocal(executionId, SignatureRequestTask.REQUEST_ID_VARIABLE);
        } catch (RuntimeException ex) {
            return; // exécution déjà supprimée : le cas « process disparu » est traité par les autres événements
        }
        if (requestId != null) {
            useCase.cancelInterruptedWait(java.util.UUID.fromString(requestId.toString()));
        }
    }

    @Override
    protected void processCompleted(FlowableEngineEntityEvent event) {
        useCase.cancelForProcess(event.getProcessInstanceId());
    }

    @Override
    protected void processCancelled(org.flowable.engine.delegate.event.FlowableCancelledEvent event) {
        useCase.cancelForProcess(event.getProcessInstanceId());
    }

    @Override
    protected void entityDeleted(FlowableEngineEntityEvent event) {
        if (event.getEntity() instanceof ExecutionEntity execution && execution.isProcessInstanceType()) {
            useCase.cancelForProcess(execution.getProcessInstanceId());
        }
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }
}
