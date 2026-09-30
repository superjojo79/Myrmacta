package com.flowforge.infrastructure.config;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.workflow.palette.BlockReferenceVerifier;
import com.flowforge.domain.workflow.palette.port.in.ListBlockReferencesUseCase;
import com.flowforge.domain.workflow.palette.port.in.ListBuildingBlocksUseCase;
import com.flowforge.domain.workflow.palette.port.out.BlockReferenceLookupPort;
import com.flowforge.domain.workflow.palette.usecase.ListBlockReferencesUseCaseImpl;
import com.flowforge.domain.workflow.palette.usecase.ListBuildingBlocksUseCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TODO §73 (palette-metier-bpmn, LC1) — cas d'usage de la palette métier. */
@Configuration
class PaletteConfig {

    @Bean
    ListBuildingBlocksUseCase listBuildingBlocksUseCase() {
        return new ListBuildingBlocksUseCaseImpl();
    }

    @Bean
    BlockReferenceVerifier blockReferenceVerifier(TenantProperties tenantProperties, BlockReferenceLookupPort lookup) {
        return new BlockReferenceVerifier(tenantProperties.id(), lookup);
    }

    @Bean
    ListBlockReferencesUseCase listBlockReferencesUseCase(
            TenantProperties tenantProperties, BlockReferenceLookupPort lookup, TenantAdminGuard guard) {
        return new ListBlockReferencesUseCaseImpl(tenantProperties.id(), lookup, guard);
    }
}
