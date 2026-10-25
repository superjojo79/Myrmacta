package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.model.WorkflowAlreadyRunningException;
import be.myrmacta.domain.dossier.port.in.AttachWorkflowUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.workflow.model.OutboxEvent;
import be.myrmacta.domain.workflow.port.out.OutboxRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.config.WorkflowProperties;
import be.myrmacta.infrastructure.observability.MdcScope;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OutboxRelayScheduler {

    static final String SYSTEM_OUTBOX_STARTED_BY = "system:outbox";

    private final OutboxRepository outboxRepository;
    private final AttachWorkflowUseCase attachWorkflow;
    private final WorkflowEnginePort workflowEnginePort;
    private final DossierRepository dossierRepository;
    private final WorkflowProperties properties;
    private final TenantProperties tenantProperties;

    OutboxRelayScheduler(
            OutboxRepository outboxRepository,
            AttachWorkflowUseCase attachWorkflow,
            WorkflowEnginePort workflowEnginePort,
            DossierRepository dossierRepository,
            WorkflowProperties properties,
            TenantProperties tenantProperties) {
        this.outboxRepository = outboxRepository;
        this.attachWorkflow = attachWorkflow;
        this.workflowEnginePort = workflowEnginePort;
        this.dossierRepository = dossierRepository;
        this.properties = properties;
        this.tenantProperties = tenantProperties;
    }

    @Scheduled(fixedDelayString = "${app.workflow.outbox.poll-delay:1s}")
    void relayPendingEvents() {
        try (MdcScope ignored = MdcScope.tenant(tenantProperties.id())) {
            for (OutboxEvent event : outboxRepository.lockNextPending(properties.outbox().batchSize())) {
                processOne(event);
            }
        }
    }

    void processOne(OutboxEvent event) {
        try (MdcScope ignored = MdcScope.tenant(tenantProperties.id()).dossier(event.payload().dossierId())) {
            processOneInternal(event);
        }
    }

    private void processOneInternal(OutboxEvent event) {
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
                    event.payload().triggerRef(),
                    SYSTEM_OUTBOX_STARTED_BY);
            if (link.isActive()) {
                markProcessedWithProcessInstance(event, link.processInstanceId());
                return;
            }

            var existing = workflowEnginePort.findProcessInstanceIdByBusinessKey(link.id().toString());
            if (existing.isPresent()) {
                confirmAndMarkProcessed(event, link.id(), existing.get());
                return;
            }

            Dossier dossier = dossierRepository.findById(event.payload().dossierId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Dossier not found: " + event.payload().dossierId()));
            Map<String, Object> variables = WorkflowVariables.withPlatformVariables(
                    event.payload().variables(), link, dossier.fields());
            String processInstanceId = workflowEnginePort.start(
                    link.processDefinitionKey(), link.tenantId(), link.id().toString(), variables);
            confirmAndMarkProcessed(event, link.id(), processInstanceId);
        } catch (WorkflowAlreadyRunningException ex) {
            outboxRepository.markDead(event.eventId(), ex.getMessage());
        } catch (RuntimeException ex) {
            outboxRepository.recordFailure(event.eventId(), ex.getMessage(), properties.outbox().maxAttempts());
        }
    }

    private void confirmAndMarkProcessed(OutboxEvent event, UUID linkId, String processInstanceId) {
        try (MdcScope ignored = itemScope(event, processInstanceId)) {
            attachWorkflow.confirmStarted(linkId, processInstanceId);
            outboxRepository.markProcessed(event.eventId());
        }
    }

    private void markProcessedWithProcessInstance(OutboxEvent event, String processInstanceId) {
        try (MdcScope ignored = itemScope(event, processInstanceId)) {
            outboxRepository.markProcessed(event.eventId());
        }
    }

    private MdcScope itemScope(OutboxEvent event, String processInstanceId) {
        return MdcScope.tenant(tenantProperties.id())
                .dossier(event.payload().dossierId())
                .processInstance(processInstanceId);
    }
}
