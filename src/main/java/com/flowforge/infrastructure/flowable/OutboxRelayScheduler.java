package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.WorkflowAlreadyRunningException;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.workflow.model.OutboxEvent;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.infrastructure.config.WorkflowProperties;
import java.util.HashMap;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OutboxRelayScheduler {

    private final OutboxRepository outboxRepository;
    private final AttachWorkflowUseCase attachWorkflow;
    private final WorkflowEnginePort workflowEnginePort;
    private final WorkflowProperties properties;

    OutboxRelayScheduler(
            OutboxRepository outboxRepository,
            AttachWorkflowUseCase attachWorkflow,
            WorkflowEnginePort workflowEnginePort,
            WorkflowProperties properties) {
        this.outboxRepository = outboxRepository;
        this.attachWorkflow = attachWorkflow;
        this.workflowEnginePort = workflowEnginePort;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.workflow.outbox.poll-delay:1s}")
    void relayPendingEvents() {
        for (OutboxEvent event : outboxRepository.lockNextPending(properties.outbox().batchSize())) {
            processOne(event);
        }
    }

    void processOne(OutboxEvent event) {
        try {
            try {
                WorkflowVariables.assertNoReservedKeys(event.payload().variables());
            } catch (IllegalArgumentException ex) {
                outboxRepository.markDead(event.eventId(), ex.getMessage());
                return;
            }

            DossierWorkflowLink link = attachWorkflow.reserveOrResume(
                    event.payload().dossierId(),
                    event.payload().processKey(),
                    event.payload().triggerRef());
            if (link.isActive()) {
                outboxRepository.markProcessed(event.eventId());
                return;
            }

            var existing = workflowEnginePort.findProcessInstanceIdByBusinessKey(link.id().toString());
            if (existing.isPresent()) {
                attachWorkflow.confirmStarted(link.id(), existing.get());
                outboxRepository.markProcessed(event.eventId());
                return;
            }

            Map<String, Object> variables = new HashMap<>(event.payload().variables());
            variables.put(WorkflowVariables.DOSSIER_ID, link.dossierId().toString());
            variables.put(WorkflowVariables.DOSSIER_WORKFLOW_LINK_ID, link.id().toString());
            variables.put(WorkflowVariables.TENANT_ID, link.tenantId());
            String processInstanceId = workflowEnginePort.start(
                    link.processDefinitionKey(), link.tenantId(), link.id().toString(), variables);
            attachWorkflow.confirmStarted(link.id(), processInstanceId);
            outboxRepository.markProcessed(event.eventId());
        } catch (WorkflowAlreadyRunningException ex) {
            outboxRepository.markDead(event.eventId(), ex.getMessage());
        } catch (RuntimeException ex) {
            outboxRepository.recordFailure(event.eventId(), ex.getMessage(), properties.outbox().maxAttempts());
        }
    }
}
