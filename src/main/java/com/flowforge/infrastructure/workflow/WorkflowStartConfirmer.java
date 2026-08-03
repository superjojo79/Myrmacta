package com.flowforge.infrastructure.workflow;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.model.WorkflowTypeConfigNotFoundException;
import com.flowforge.domain.workflow.port.out.IdempotencyStore;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Frontière transactionnelle unique (DD-TX, TX2) pour la finalisation d'un démarrage Flowable
 * réussi : {@code confirmStarted} + mémorisation de l'idempotency-key + transition
 * {@code onStartDossierStatus} optionnelle sont commis ou annulés ensemble. Appelé <b>après</b>
 * le {@code Flowable.start} (hors transaction JPA, DD-TX) — jamais avant.
 */
@Component
public class WorkflowStartConfirmer {

    private final AttachWorkflowUseCase attachWorkflow;
    private final IdempotencyStore idempotencyStore;
    private final UpdateDossierStatusUseCase updateDossierStatusUseCase;
    private final WorkflowTypeConfigRepository configRepository;

    public WorkflowStartConfirmer(
            AttachWorkflowUseCase attachWorkflow,
            IdempotencyStore idempotencyStore,
            UpdateDossierStatusUseCase updateDossierStatusUseCase,
            WorkflowTypeConfigRepository configRepository) {
        this.attachWorkflow = Objects.requireNonNull(attachWorkflow, "attachWorkflow");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.updateDossierStatusUseCase =
                Objects.requireNonNull(updateDossierStatusUseCase, "updateDossierStatusUseCase");
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
    }

    @Transactional
    public void confirm(
            Dossier dossier,
            DossierWorkflowLink reserved,
            String processInstanceId,
            String processDefinitionKey,
            String idempotencyKey) {
        attachWorkflow.confirmStarted(reserved.id(), processInstanceId);
        idempotencyStore.remember(reserved.tenantId(), dossier.id(), idempotencyKey, reserved.id());
        applyOnStartStatusIfConfigured(dossier, processDefinitionKey);
    }

    private void applyOnStartStatusIfConfigured(Dossier dossier, String processDefinitionKey) {
        WorkflowTypeConfig config = configRepository.findByWorkflowKey(dossier.tenantId(), processDefinitionKey)
                .orElseThrow(() -> new WorkflowTypeConfigNotFoundException(
                        "Workflow type config not found: " + processDefinitionKey));
        if (config.onStartDossierStatus() != null) {
            updateDossierStatusUseCase.update(
                    new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(
                            dossier.id(), config.onStartDossierStatus(), "workflow", null),
                    AccessContext.system("workflow-start"));
        }
    }
}
