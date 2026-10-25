package be.myrmacta.application.workflow;

import be.myrmacta.domain.dossier.model.AbortReservedReason;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.in.AttachWorkflowUseCase;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.workflow.model.WorkflowStartedEvent;
import be.myrmacta.domain.workflow.port.out.IdempotencyStore;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.port.out.WorkflowEventPublisher;
import be.myrmacta.domain.workflow.port.out.WorkflowStartConfirmationPort;
import be.myrmacta.domain.workflow.DocumentTypeAllowList;
import be.myrmacta.domain.workflow.WorkflowVariables;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ManualStartOrchestrator {

    private final DossierRepository dossierRepository;
    private final DossierTypeRepository dossierTypeRepository;
    private final DossierWorkflowLinkRepository linkRepository;
    private final AttachWorkflowUseCase attachWorkflow;
    private final WorkflowEnginePort workflowEnginePort;
    private final IdempotencyStore idempotencyStore;
    private final WorkflowStartConfirmationPort workflowStartConfirmer;
    private final WorkflowEventPublisher eventPublisher;

    public ManualStartOrchestrator(
            DossierRepository dossierRepository,
            DossierTypeRepository dossierTypeRepository,
            DossierWorkflowLinkRepository linkRepository,
            AttachWorkflowUseCase attachWorkflow,
            WorkflowEnginePort workflowEnginePort,
            IdempotencyStore idempotencyStore,
            WorkflowStartConfirmationPort workflowStartConfirmer,
            WorkflowEventPublisher eventPublisher) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.dossierTypeRepository = Objects.requireNonNull(dossierTypeRepository, "dossierTypeRepository");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.attachWorkflow = Objects.requireNonNull(attachWorkflow, "attachWorkflow");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.workflowStartConfirmer = Objects.requireNonNull(workflowStartConfirmer, "workflowStartConfirmer");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public StartResult start(
            UUID dossierId,
            String processDefinitionKey,
            Map<String, Object> variables,
            String idempotencyKey,
            String startedBy) {
        if (startedBy == null || startedBy.isBlank()) {
            throw new IllegalArgumentException("startedBy is required");
        }
        WorkflowVariables.assertNoReservedKeys(variables);
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        var replay = idempotencyStore.findLinkId(dossier.tenantId(), dossierId, idempotencyKey)
                .flatMap(linkRepository::findById);
        if (replay.isPresent()) {
            return new StartResult(replay.get(), true);
        }
        // Revue adversariale TODO §93 (I8) : même liste blanche qu'à la complétion d'une tâche (D-TASK-FORM-DOCTYPE-SCOPE).
        if (DocumentTypeAllowList.concerns(variables)) {
            DossierType type = dossierTypeRepository.findByCode(dossier.tenantId(), dossier.dossierTypeCode())
                    .orElseThrow(() -> new DossierNotFoundException(
                            "Dossier type not found: " + dossier.tenantId() + "/" + dossier.dossierTypeCode()));
            DocumentTypeAllowList.assertAllowed(type, variables);
        }

        DossierWorkflowLink reserved = attachWorkflow.reserve(
                dossierId, processDefinitionKey, "manual:" + idempotencyKey, startedBy);
        Map<String, Object> engineVariables =
                WorkflowVariables.withPlatformVariables(variables, reserved, dossier.fields());

        String processInstanceId;
        try {
            processInstanceId = workflowEnginePort.start(
                    processDefinitionKey, reserved.tenantId(), reserved.id().toString(), engineVariables);
        } catch (RuntimeException ex) {
            try {
                attachWorkflow.abortReserved(reserved.id(), AbortReservedReason.ORPHAN_START_FAILED);
            } catch (RuntimeException abortEx) {
                ex.addSuppressed(abortEx);
            }
            throw ex;
        }

        // TX2 (DD-TX) : confirm + idempotency + onStart, atomiques — voir WorkflowStartConfirmer.
        workflowStartConfirmer.confirm(dossier, reserved, processInstanceId, processDefinitionKey, idempotencyKey);
        eventPublisher.publish(new WorkflowStartedEvent(reserved.id(), Instant.now(), processDefinitionKey));
        return new StartResult(linkRepository.findById(reserved.id()).orElse(reserved.withProcessInstance(processInstanceId)), false);
    }

    public record StartResult(DossierWorkflowLink link, boolean replay) {}
}
