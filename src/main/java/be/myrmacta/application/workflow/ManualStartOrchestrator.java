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
import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowStartConfirmationPort;
import be.myrmacta.domain.workflow.DocumentTypeAllowList;
import be.myrmacta.domain.workflow.StartVariableDeclarations;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.port.out.StartVariableCatalogPort;
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
    private final StartVariableCatalogPort startVariableCatalog;
    private final WorkflowLinkGroupRepository linkGroups;

    public ManualStartOrchestrator(
            DossierRepository dossierRepository,
            DossierTypeRepository dossierTypeRepository,
            DossierWorkflowLinkRepository linkRepository,
            AttachWorkflowUseCase attachWorkflow,
            WorkflowEnginePort workflowEnginePort,
            IdempotencyStore idempotencyStore,
            WorkflowStartConfirmationPort workflowStartConfirmer,
            WorkflowEventPublisher eventPublisher,
            StartVariableCatalogPort startVariableCatalog) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.dossierTypeRepository = Objects.requireNonNull(dossierTypeRepository, "dossierTypeRepository");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.attachWorkflow = Objects.requireNonNull(attachWorkflow, "attachWorkflow");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.workflowStartConfirmer = Objects.requireNonNull(workflowStartConfirmer, "workflowStartConfirmer");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.startVariableCatalog = Objects.requireNonNull(startVariableCatalog, "startVariableCatalog");
        this.linkGroups = null;
    }

    /** TODO §113 (D-GROUP-LINK) — avec le registre des groupes destinataires des liens. */
    public ManualStartOrchestrator(
            DossierRepository dossierRepository,
            DossierTypeRepository dossierTypeRepository,
            DossierWorkflowLinkRepository linkRepository,
            AttachWorkflowUseCase attachWorkflow,
            WorkflowEnginePort workflowEnginePort,
            IdempotencyStore idempotencyStore,
            WorkflowStartConfirmationPort workflowStartConfirmer,
            WorkflowEventPublisher eventPublisher,
            StartVariableCatalogPort startVariableCatalog,
            WorkflowLinkGroupRepository linkGroups) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.dossierTypeRepository = Objects.requireNonNull(dossierTypeRepository, "dossierTypeRepository");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.attachWorkflow = Objects.requireNonNull(attachWorkflow, "attachWorkflow");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.workflowStartConfirmer = Objects.requireNonNull(workflowStartConfirmer, "workflowStartConfirmer");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.startVariableCatalog = Objects.requireNonNull(startVariableCatalog, "startVariableCatalog");
        this.linkGroups = Objects.requireNonNull(linkGroups, "linkGroups");
    }

    /**
     * TODO §111 (D-START-DECLARED) — mêmes règles que le démarrage, sans réserver de lien. Le {@code dryRun} MCP s'en sert.
     */
    public Map<String, Object> bindStartVariables(String processDefinitionKey, Map<String, Object> variables) {
        WorkflowVariables.assertNoReservedKeys(variables);
        return StartVariableDeclarations.bind(startVariableCatalog.findDeclared(processDefinitionKey), variables);
    }

    public StartResult start(
            UUID dossierId,
            String processDefinitionKey,
            Map<String, Object> variables,
            String idempotencyKey,
            String startedBy) {
        return start(dossierId, processDefinitionKey, variables, idempotencyKey, startedBy, null);
    }

    /**
     * TODO §113 (D-GROUP-LINK) — idem, et le process reçoit {@code assignedGroup} comme groupe destinataire, posé sur le lien
     * entre la réservation et le démarrage du moteur (la première tâche naît pendant le démarrage). {@code null} : aucun.
     */
    public StartResult start(
            UUID dossierId,
            String processDefinitionKey,
            Map<String, Object> variables,
            String idempotencyKey,
            String startedBy,
            String assignedGroup) {
        if (assignedGroup != null && linkGroups == null) {
            throw new IllegalStateException("assigning a group needs the link group store");
        }
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
        Map<String, Object> accepted = StartVariableDeclarations.bind(
                startVariableCatalog.findDeclared(processDefinitionKey), variables);
        if (DocumentTypeAllowList.concerns(accepted)) {
            DossierType type = dossierTypeRepository.findByCode(dossier.tenantId(), dossier.dossierTypeCode())
                    .orElseThrow(() -> new DossierNotFoundException(
                            "Dossier type not found: " + dossier.tenantId() + "/" + dossier.dossierTypeCode()));
            DocumentTypeAllowList.assertAllowed(type, accepted);
        }

        DossierWorkflowLink reserved = attachWorkflow.reserve(
                dossierId, processDefinitionKey, "manual:" + idempotencyKey, startedBy);
        Map<String, Object> engineVariables =
                WorkflowVariables.withPlatformVariables(accepted, reserved, dossier.fields());
        if (assignedGroup != null) {
            linkGroups.assign(reserved.tenantId(), reserved.id(), assignedGroup);
        }

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
