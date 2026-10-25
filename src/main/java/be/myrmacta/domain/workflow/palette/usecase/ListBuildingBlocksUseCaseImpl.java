package be.myrmacta.domain.workflow.palette.usecase;

import be.myrmacta.domain.workflow.palette.BuildingBlockCatalog;
import be.myrmacta.domain.workflow.palette.port.in.ListBuildingBlocksUseCase;

public final class ListBuildingBlocksUseCaseImpl implements ListBuildingBlocksUseCase {

    @Override
    public Catalog list() {
        return new Catalog(BuildingBlockCatalog.VERSION, BuildingBlockCatalog.all());
    }
}
