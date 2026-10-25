package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.workflow.palette.BuildingBlock;
import be.myrmacta.domain.workflow.palette.port.in.ListBuildingBlocksUseCase;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * TODO §73 (P1) — le même catalogue de briques que l'éditeur BPMN. L'agent compose du BPMN à partir des briques
 * (élément, attributs {@code flowable:*}, paramètres typés, sources de valeurs) puis le vérifie avec
 * {@code deploy_workflow} en {@code dryRun}. Lecture seule ; aucune donnée du tenant.
 */
@Component
public class ListBpmnBuildingBlocksTool {

    private final ListBuildingBlocksUseCase useCase;

    public ListBpmnBuildingBlocksTool(ListBuildingBlocksUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @McpTool(
            name = "list_bpmn_building_blocks",
            description = "List the business building blocks of the BPMN palette: element to create, flowable:* "
                    + "attributes to write, typed parameters and the source of their values. Compose a BPMN from "
                    + "them, then validate it with deploy_workflow dryRun=true.")
    public Catalog listBpmnBuildingBlocks() {
        ListBuildingBlocksUseCase.Catalog catalog = useCase.list();
        return new Catalog(catalog.version(), catalog.blocks());
    }

    public record Catalog(int version, List<BuildingBlock> blocks) {}
}
