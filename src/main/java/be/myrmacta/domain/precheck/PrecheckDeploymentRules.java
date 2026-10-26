package be.myrmacta.domain.precheck;

import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.precheck.model.PrecheckBlock;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import be.myrmacta.domain.precheck.port.out.PrecheckAvailabilityPort;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import java.util.List;
import java.util.Objects;

/**
 * TODO §106 (issue #211, lot B, P8, C3, R5, D-PRECHECK-BLOCK) — au déploiement : la brique est refusée tant que la
 * pré-vérification est désactivée, et contrôlée contre chaque type de dossier lié au processus (même {@code workflowKey}) :
 * un champ transmis doit exister et ne pas être sensible, un type de pièce doit exister et n'être lu que s'il n'est pas
 * sensible. Un type modifié après le déploiement est rattrapé à l'exécution (constat « À VÉRIFIER »).
 */
public final class PrecheckDeploymentRules {

    private final String tenantId;
    private final PrecheckAvailabilityPort availability;
    private final DossierTypeRepository typeRepository;

    public PrecheckDeploymentRules(String tenantId, PrecheckAvailabilityPort availability,
            DossierTypeRepository typeRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
    }

    public void verify(String processDefinitionKey, List<PrecheckBlock> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        if (!availability.enabled()) {
            throw new InvalidBpmnException("BPMN AI pre-check block requires AI pre-check to be enabled "
                    + "(APP_PRECHECK_ENABLED=true): " + blocks.getFirst().activityId());
        }
        List<DossierType> bound = typeRepository.findAll(tenantId).stream()
                .filter(type -> processDefinitionKey.equals(type.workflowKey()))
                .toList();
        for (PrecheckBlock block : blocks) {
            for (DossierType type : bound) {
                verify(block, type);
            }
        }
    }

    private static void verify(PrecheckBlock block, DossierType type) {
        String owner = "BPMN activity " + block.activityId() + " (dossier type " + type.code() + ")";
        for (String name : block.spec().fields()) {
            FieldDefinition field = type.fieldDefinitions().stream()
                    .filter(d -> d.name().equals(name)).findFirst()
                    .orElseThrow(() -> new InvalidBpmnException(owner + ": precheckFields names an unknown field " + name));
            if (field.isSensitive()) {
                throw new InvalidBpmnException(owner + ": precheckFields must not name the sensitive field " + name);
            }
        }
        for (PrecheckSpec.PieceRequirement piece : block.spec().pieces()) {
            PieceType pieceType = type.pieceType(piece.key())
                    .orElseThrow(() -> new InvalidBpmnException(
                            owner + ": precheckPieces names an unknown piece type " + piece.key()));
            if (piece.read() && pieceType.sensitive()) {
                throw new InvalidBpmnException(owner + ": the sensitive piece type " + piece.key()
                        + " cannot be read by the model (remove 'lire')");
            }
        }
    }
}
