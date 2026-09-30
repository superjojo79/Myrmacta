package com.flowforge.domain.workflow.palette;

import com.flowforge.domain.workflow.model.UnknownBlockReferenceException;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.palette.port.out.BlockReferenceLookupPort;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * TODO §73 (D-PALETTE-DEPLOY-REFERENCES) — vérifie, au déploiement, que les ressources référencées par les nouveaux
 * attributs de brique existent : template actif, instance de connecteur de rôle service, type de dossier,
 * configuration de workflow (ou le process en cours de déploiement), webhook, et statut déclaré dans la configuration
 * <b>de ce process</b> (stockée, ou soumise en {@code dryRun}).
 */
public final class BlockReferenceVerifier {

    private final String tenantId;
    private final BlockReferenceLookupPort lookup;

    public BlockReferenceVerifier(String tenantId, BlockReferenceLookupPort lookup) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    public void verify(String processKey, Optional<WorkflowTypeConfig> effectiveConfig, List<BlockParameterUsage> usages) {
        for (BlockParameterUsage usage : usages) {
            boolean exists = switch (usage.valueSource()) {
                case "workflowStatuses" -> effectiveConfig
                        .map(c -> c.statusDefinitions().stream()
                                .map(WorkflowStatusDefinition::code)
                                .anyMatch(usage.value()::equals))
                        .orElse(false);
                case "workflowTypes" -> usage.value().equals(processKey)
                        || lookup.exists(tenantId, "workflowTypes", usage.value());
                default -> lookup.exists(tenantId, usage.valueSource(), usage.value());
            };
            if (!exists) {
                throw new UnknownBlockReferenceException(usage.activityId(), usage.parameter(), usage.value());
            }
        }
    }
}
