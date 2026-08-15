package com.flowforge.infrastructure.flowable;

import com.flowforge.application.workflow.ManualStartOrchestrator;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DuplicateDossierRelationException;
import com.flowforge.domain.dossier.model.RelationType;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.dossier.ChildDossierRelationConfirmer;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("createChildDossierTask")
class CreateChildDossierDelegate implements JavaDelegate {

    private static final Set<RelationType> ALLOWED_TYPES =
            Set.of(RelationType.PARENT_CHILD, RelationType.SUBSEQUENT);

    private final ChildDossierRelationConfirmer confirmer;
    private final ManualStartOrchestrator manualStartOrchestrator;
    private final DossierRepository dossierRepository;
    private final UpdateDossierStatusUseCase updateDossierStatusUseCase;
    private final TenantProperties tenantProperties;

    CreateChildDossierDelegate(
            ChildDossierRelationConfirmer confirmer,
            ManualStartOrchestrator manualStartOrchestrator,
            DossierRepository dossierRepository,
            UpdateDossierStatusUseCase updateDossierStatusUseCase,
            TenantProperties tenantProperties) {
        this.confirmer = confirmer;
        this.manualStartOrchestrator = manualStartOrchestrator;
        this.dossierRepository = dossierRepository;
        this.updateDossierStatusUseCase = updateDossierStatusUseCase;
        this.tenantProperties = tenantProperties;
    }

    @Override
    public void execute(DelegateExecution execution) {
        UUID sourceDossierId = requiredUuid(execution, "dossierId");
        String childType = requiredString(execution, "childDossierTypeCode");
        String childLabel = requiredString(execution, "childDossierLabel");
        String workflowProcessKey = requiredString(execution, "workflowProcessKey");
        RelationType relationType = parseRelationType(requiredString(execution, "relationType"));
        if (!ALLOWED_TYPES.contains(relationType)) {
            throw new IllegalArgumentException(
                    "relationType must be PARENT_CHILD or SUBSEQUENT, got: " + relationType);
        }
        String processInstanceId = execution.getProcessInstanceId();
        String activityId = execution.getCurrentActivityId();
        if (processInstanceId == null || processInstanceId.isBlank() || activityId == null || activityId.isBlank()) {
            throw new IllegalArgumentException("processInstanceId and activityId are required to derive idempotencyKey");
        }
        String idempotencyKey = processInstanceId + ":" + activityId;
        @SuppressWarnings("unchecked")
        Map<String, Object> childFields = execution.getVariable("childDossierFields") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> childVariables = execution.getVariable("childWorkflowVariables") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        UUID childDossierId = confirmWithRetryOnRace(
                sourceDossierId, childType, childLabel, childFields, relationType, idempotencyKey);
        openDraftIfNeeded(childDossierId);
        manualStartOrchestrator.start(childDossierId, workflowProcessKey, childVariables, idempotencyKey, "workflow");
    }

    /**
     * {@link ChildDossierRelationConfirmer#confirm} can lose a genuine race against another
     * execution creating the same child under the same idempotency key: its own transaction
     * fails on the unique constraint and must be let go (see the confirmer's own comment for
     * why it cannot recover internally). Retrying here calls confirm() again in a brand new
     * transaction, whose idempotency lookup then finds the winner's already-committed row.
     * Bounded to a single retry — a second failure is a real problem, not a race, and should
     * fault the job rather than loop.
     */
    private UUID confirmWithRetryOnRace(
            UUID sourceDossierId,
            String childType,
            String childLabel,
            Map<String, Object> childFields,
            RelationType relationType,
            String idempotencyKey) {
        try {
            return confirmer.confirm(
                    tenantProperties.id(), sourceDossierId, childType, childLabel, childFields, relationType,
                    idempotencyKey);
        } catch (DuplicateDossierRelationException ex) {
            return confirmer.confirm(
                    tenantProperties.id(), sourceDossierId, childType, childLabel, childFields, relationType,
                    idempotencyKey);
        }
    }

    private void openDraftIfNeeded(UUID childDossierId) {
        Dossier child = dossierRepository.findById(childDossierId).orElse(null);
        if (child == null || child.status() != DossierStatus.DRAFT) {
            return;
        }
        updateDossierStatusUseCase.update(
                new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(
                        childDossierId, DossierStatus.OUVERT, "workflow", null),
                AccessContext.system("flowable-delegate"));
    }

    private static RelationType parseRelationType(String raw) {
        try {
            return RelationType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid relationType: " + raw, ex);
        }
    }

    private static String requiredString(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(name + " process variable is required");
        }
        return value.toString();
    }

    private static UUID requiredUuid(DelegateExecution execution, String name) {
        return UUID.fromString(requiredString(execution, name));
    }
}
