package be.myrmacta.domain.workflow.palette.port.in;

import be.myrmacta.domain.workflow.palette.BuildingBlock;
import java.util.List;

/** Catalogue des briques de la palette (TODO §73) : lu par l'éditeur BPMN et par le tool MCP. */
public interface ListBuildingBlocksUseCase {

    Catalog list();

    record Catalog(int version, List<BuildingBlock> blocks) {}
}
