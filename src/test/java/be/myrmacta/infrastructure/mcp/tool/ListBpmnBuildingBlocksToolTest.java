package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.workflow.palette.BuildingBlockCatalog;
import be.myrmacta.domain.workflow.palette.usecase.ListBuildingBlocksUseCaseImpl;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

/** S-CAT-6 — le tool MCP sert le même catalogue que l'API, en lecture seule (TODO §73, P1). */
class ListBpmnBuildingBlocksToolTest {

    private final ListBpmnBuildingBlocksTool tool = new ListBpmnBuildingBlocksTool(new ListBuildingBlocksUseCaseImpl());

    @Test
    void servesTheSameCatalogueAsTheApi() {
        var catalog = tool.listBpmnBuildingBlocks();
        assertThat(catalog.version()).isEqualTo(BuildingBlockCatalog.VERSION);
        assertThat(catalog.blocks()).isEqualTo(BuildingBlockCatalog.all());
    }

    @Test
    void isTheOnlyToolOfTheClassAndTakesNoParameter() {
        Method[] tools = Arrays.stream(ListBpmnBuildingBlocksTool.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(McpTool.class)).toArray(Method[]::new);
        assertThat(tools).hasSize(1);
        assertThat(tools[0].getParameterCount()).isZero();
        assertThat(tools[0].getAnnotation(McpTool.class).name()).isEqualTo("list_bpmn_building_blocks");
    }
}
