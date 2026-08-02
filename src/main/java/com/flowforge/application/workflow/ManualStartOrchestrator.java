package com.flowforge.application.workflow;

import com.flowforge.domain.dossier.model.AbortReservedReason;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.workflow.model.WorkflowStartedEvent;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.out.IdempotencyStore;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.port.out.WorkflowEventPublisher;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import com.flowforge.domain.workflow.WorkflowVariables;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ManualStartOrchestrator {

    private final DossierRepository dossierRepository;
    private final DossierWorkflowLinkRepository linkRepository;
    private final WorkflowTypeConfigRepository configRepository;
    private final AttachWorkflowUseCase attachWorkflow;
    private final WorkflowEnginePort workflowEnginePort;
    private final IdempotencyStore idempotencyStore;
    private final UpdateDossierStatusUseCase updateDossierStatusUseCase;
    private final WorkflowEventPublisher eventPublisher;

    public ManualStartOrchestrator(
            DossierRepository dossierRepository,
            DossierWorkflowLinkRepository linkRepository,
            WorkflowTypeConfigRepository configRepository,
            AttachWorkflowUseCase attachWorkflow,
            WorkflowEnginePort workflowEnginePort,
            IdempotencyStore idempotencyStore,
            UpdateDossierStatusUseCase updateDossierStatusUseCase,
            WorkflowEventPublisher eventPublisher) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.attachWorkflow = Objects.requireNonNull(attachWorkflow, "attachWorkflow");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.updateDossierStatusUseCase = Objects.requireNonNull(updateDossierStatusUseCase, "updateDossierStatusUseCase");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public StartResult start(
            UUID dossierId,
            String processDefinitionKey,
            Map<String, Object> variables,
            String idempotencyKey) {
        WorkflowVariables.assertNoReservedKeys(variables);
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        var replay = idempotencyStore.findLinkId(dossier.tenantId(), dossierId, idempotencyKey)
                .flatMap(linkRepository::findById);
        if (replay.isPresent()) {
            return new StartResult(replay.get(), true);
        }

        DossierWorkflowLink reserved = attachWorkflow.reserve(dossierId, processDefinitionKey, "manual:" + idempotencyKey);
        Map<String, Object> engineVariables = new HashMap<>(variables == null ? Map.of() : variables);
        engineVariables.put(WorkflowVariables.DOSSIER_ID, dossierId.toString());
        engineVariables.put(WorkflowVariables.DOSSIER_WORKFLOW_LINK_ID, reserved.id().toString());
        engineVariables.put(WorkflowVariables.TENANT_ID, reserved.tenantId());

        String processInstanceId;
        try {
            processInstanceId = workflowEnginePort.start(
                    processDefinitionKey, reserved.tenantId(), reserved.id().toString(), engineVariables);
        } catch (RuntimeException ex) {
            attachWorkflow.abortReserved(reserved.id(), AbortReservedReason.ORPHAN_START_FAILED);
            throw ex;
        }

        attachWorkflow.confirmStarted(reserved.id(), processInstanceId);
        idempotencyStore.remember(reserved.tenantId(), dossierId, idempotencyKey, reserved.id());
        applyOnStartStatusIfConfigured(dossier, processDefinitionKey);
        eventPublisher.publish(new WorkflowStartedEvent(reserved.id(), Instant.now(), processDefinitionKey));
        return new StartResult(linkRepository.findById(reserved.id()).orElse(reserved.withProcessInstance(processInstanceId)), false);
    }

    private void applyOnStartStatusIfConfigured(Dossier dossier, String processDefinitionKey) {
        WorkflowTypeConfig config = configRepository.findByWorkflowKey(dossier.tenantId(), processDefinitionKey)
                .orElseThrow(() -> new IllegalArgumentException("Workflow type config not found: " + processDefinitionKey));
        if (config.onStartDossierStatus() != null) {
            updateDossierStatusUseCase.update(
                    new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(
                            dossier.id(), config.onStartDossierStatus(), "workflow", null),
                    AccessContext.system("workflow-start"));
        }
    }

    public record StartResult(DossierWorkflowLink link, boolean replay) {}
}
