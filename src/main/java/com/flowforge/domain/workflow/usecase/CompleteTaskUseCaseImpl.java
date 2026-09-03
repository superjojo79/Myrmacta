package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.domain.workflow.port.in.CompleteTaskUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CompleteTaskUseCaseImpl implements CompleteTaskUseCase {

    private final String tenantId;
    private final WorkflowEnginePort workflowEnginePort;
    private final DossierRepository dossierRepository;
    private final DossierTypeRepository dossierTypeRepository;

    public CompleteTaskUseCaseImpl(
            String tenantId,
            WorkflowEnginePort workflowEnginePort,
            DossierRepository dossierRepository,
            DossierTypeRepository dossierTypeRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.dossierTypeRepository = Objects.requireNonNull(dossierTypeRepository, "dossierTypeRepository");
    }

    @Override
    public void complete(String taskId, String dossierId, Map<String, Object> variables) {
        WorkflowVariables.assertNoReservedKeys(variables);
        assertDocumentTypeAllowed(dossierId, variables);
        workflowEnginePort.completeTask(taskId, variables == null ? Map.of() : variables);
    }

    /**
     * D-TASK-FORM-DOCTYPE-SCOPE (Phase 9 adversarial review finding E4): {@code
     * DossierType.documentTemplateKeys()} was only ever enforced at discovery time (the {@code
     * GET .../document-templates} endpoint filters to configured keys) — nothing stopped a task
     * holder from completing with an arbitrary {@code documentType} the tenant admin never
     * allow-listed. Any actor able to complete this task (EDIT on the dossier) already reaches the
     * same {@code ${documentGenerationTask}} in the same transaction, so this is enforced here
     * rather than trusting the frontend picker.
     */
    private void assertDocumentTypeAllowed(String dossierId, Map<String, Object> variables) {
        if (variables == null) {
            return;
        }
        Object documentType = variables.get(WorkflowVariables.DOCUMENT_TYPE);
        if (documentType == null) {
            return;
        }
        Dossier dossier = dossierRepository.findById(UUID.fromString(dossierId))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        DossierType type = dossierTypeRepository.findByCode(tenantId, dossier.dossierTypeCode())
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier type not found: " + tenantId + "/" + dossier.dossierTypeCode()));
        if (!type.documentTemplateKeys().contains(documentType.toString())) {
            throw new IllegalArgumentException(
                    "documentType '" + documentType + "' is not an allowed template for dossier type '"
                            + type.code() + "' -- configure it in Admin > Types > Templates de documents autorisés");
        }
    }
}
