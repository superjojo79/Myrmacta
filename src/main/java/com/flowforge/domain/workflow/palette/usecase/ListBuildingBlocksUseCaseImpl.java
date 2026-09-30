package com.flowforge.domain.workflow.palette.usecase;

import com.flowforge.domain.workflow.palette.BuildingBlockCatalog;
import com.flowforge.domain.workflow.palette.port.in.ListBuildingBlocksUseCase;

public final class ListBuildingBlocksUseCaseImpl implements ListBuildingBlocksUseCase {

    @Override
    public Catalog list() {
        return new Catalog(BuildingBlockCatalog.VERSION, BuildingBlockCatalog.all());
    }
}
