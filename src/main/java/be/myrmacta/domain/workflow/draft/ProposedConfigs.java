package be.myrmacta.domain.workflow.draft;

import be.myrmacta.domain.workflow.model.ProposedWorkflowConfig;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import java.util.Optional;
import java.util.UUID;

/** TODO §82 (D6) — la {@link WorkflowTypeConfig} telle qu'elle serait appliquée pour une configuration proposée. */
public final class ProposedConfigs {

    private ProposedConfigs() {}

    public static WorkflowTypeConfig toConfig(
            String tenantId, Optional<WorkflowTypeConfig> existing, String key, ProposedWorkflowConfig config, String actorId) {
        return existing
                .map(current -> new WorkflowTypeConfig(
                        current.id(), current.tenantId(), key, config.label(), config.statusDefinitions(),
                        config.allowedDossierStatuses(), config.onStartDossierStatus(), config.observerUserIds(),
                        current.createdBy(), actorId))
                .orElseGet(() -> new WorkflowTypeConfig(
                        UUID.randomUUID(), tenantId, key, config.label(), config.statusDefinitions(),
                        config.allowedDossierStatuses(), config.onStartDossierStatus(), config.observerUserIds(),
                        actorId, actorId));
    }
}
