package be.myrmacta.domain.workflow.palette;

import be.myrmacta.domain.workflow.model.UnknownBlockReferenceException;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.palette.port.out.BlockReferenceLookupPort;
import be.myrmacta.domain.workflow.palette.port.out.ReferenceStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * TODO §73 (D-PALETTE-DEPLOY-REFERENCES) — vérifie, au déploiement, que les ressources référencées par les nouveaux
 * attributs de brique existent : template actif, instance de connecteur de rôle service, type de dossier,
 * configuration de workflow (ou le process en cours de déploiement), webhook, et statut déclaré dans la configuration
 * <b>de ce process</b> (stockée, ou soumise en {@code dryRun}).
 *
 * <p>TODO §93 (D-DRAFT-REF-STATUS) — en mode {@link Mode#DRAFT} (brouillon proposé par un canal automatisé), une
 * ressource qui existe mais n'est pas encore active est acceptée et renvoyée : la publication, elle, reste stricte.
 */
public final class BlockReferenceVerifier {

    /** {@code STRICT} : déploiement et publication ; {@code DRAFT} : proposition d'un brouillon. */
    public enum Mode {
        STRICT,
        DRAFT
    }

    private final String tenantId;
    private final BlockReferenceLookupPort lookup;

    public BlockReferenceVerifier(String tenantId, BlockReferenceLookupPort lookup) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    public void verify(String processKey, Optional<WorkflowTypeConfig> effectiveConfig, List<BlockParameterUsage> usages) {
        verify(processKey, effectiveConfig, usages, Mode.STRICT);
    }

    /** @return les références inactives acceptées (toujours vide en mode {@code STRICT}) */
    public List<InactiveBlockReference> verify(
            String processKey, Optional<WorkflowTypeConfig> effectiveConfig, List<BlockParameterUsage> usages, Mode mode) {
        Objects.requireNonNull(mode, "mode");
        List<InactiveBlockReference> inactive = new ArrayList<>();
        for (BlockParameterUsage usage : usages) {
            ReferenceStatus status = switch (usage.valueSource()) {
                case "workflowStatuses" -> effectiveConfig
                        .map(c -> c.statusDefinitions().stream()
                                .map(WorkflowStatusDefinition::code)
                                .anyMatch(usage.value()::equals))
                        .orElse(false) ? ReferenceStatus.ACTIVE : ReferenceStatus.MISSING;
                case "workflowTypes" -> usage.value().equals(processKey)
                        ? ReferenceStatus.ACTIVE
                        : lookup.status(tenantId, "workflowTypes", usage.value());
                default -> lookup.status(tenantId, usage.valueSource(), usage.value());
            };
            if (status == ReferenceStatus.INACTIVE && mode == Mode.DRAFT) {
                inactive.add(new InactiveBlockReference(
                        usage.activityId(), usage.parameter(), usage.valueSource(), usage.value()));
            } else if (status != ReferenceStatus.ACTIVE) {
                throw new UnknownBlockReferenceException(usage.activityId(), usage.parameter(), usage.valueSource(), usage.value());
            }
        }
        return List.copyOf(inactive);
    }
}
