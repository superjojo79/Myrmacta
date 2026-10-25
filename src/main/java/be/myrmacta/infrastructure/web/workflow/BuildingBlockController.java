package be.myrmacta.infrastructure.web.workflow;

import be.myrmacta.domain.workflow.palette.BuildingBlock;
import be.myrmacta.domain.workflow.palette.port.in.ListBlockReferencesUseCase;
import be.myrmacta.domain.workflow.palette.port.in.ListBuildingBlocksUseCase;
import be.myrmacta.domain.workflow.palette.port.out.BlockReference;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §73 (palette-metier-bpmn) — catalogue des briques (statique, sans donnée du tenant) et listes minimales des
 * trois sources réservées à l'administration (D-PALETTE-REFERENCES). Éditeur BPMN : TENANT_ADMIN, WORKFLOW_ADMIN.
 */
@RestController
@RequestMapping("/api/bpmn-building-blocks")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
class BuildingBlockController {

    private final ListBuildingBlocksUseCase catalogUseCase;
    private final ListBlockReferencesUseCase referencesUseCase;
    private final AccessContextFactory accessContextFactory;

    BuildingBlockController(
            ListBuildingBlocksUseCase catalogUseCase,
            ListBlockReferencesUseCase referencesUseCase,
            AccessContextFactory accessContextFactory) {
        this.catalogUseCase = catalogUseCase;
        this.referencesUseCase = referencesUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping
    ResponseEntity<BuildingBlockCatalogResponse> catalog() {
        ListBuildingBlocksUseCase.Catalog catalog = catalogUseCase.list();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new BuildingBlockCatalogResponse(catalog.version(), catalog.blocks()));
    }

    @GetMapping("/references/{source}")
    List<BlockReferenceResponse> references(@PathVariable String source, Authentication authentication) {
        return referencesUseCase.list(source, accessContextFactory.user(authentication)).stream()
                .map(BlockReferenceResponse::from)
                .toList();
    }

    record BuildingBlockCatalogResponse(int version, List<BuildingBlock> blocks) {}

    record BlockReferenceResponse(String value, String label) {
        static BlockReferenceResponse from(BlockReference reference) {
            return new BlockReferenceResponse(reference.value(), reference.label());
        }
    }
}
