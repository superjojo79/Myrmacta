package com.flowforge.domain.workflow.palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.workflow.model.UnknownBlockSourceException;
import com.flowforge.domain.workflow.palette.port.out.BlockReference;
import com.flowforge.domain.workflow.palette.port.out.BlockReferenceLookupPort;
import com.flowforge.domain.workflow.palette.usecase.ListBlockReferencesUseCaseImpl;
import com.flowforge.domain.workflow.palette.usecase.ListBuildingBlocksUseCaseImpl;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** S-CAT-1 (lecture), S-REF-1, S-REF-2 — cas d'usage de la palette (TODO §73, M1). */
class BlockReferencesUseCaseTest {

    private static final String TENANT = "tenant_test";
    private final BlockReferenceLookupPort lookup = mock(BlockReferenceLookupPort.class);
    private final ListBlockReferencesUseCaseImpl references =
            new ListBlockReferencesUseCaseImpl(TENANT, lookup, new TenantAdminGuard());

    private static AccessContext.User user(String... roles) {
        return new AccessContext.User("u1", Set.of(roles));
    }

    @Test
    void catalogUseCaseReturnsTheCatalogWithItsVersion() {
        var catalog = new ListBuildingBlocksUseCaseImpl().list();
        assertThat(catalog.version()).isEqualTo(BuildingBlockCatalog.VERSION);
        assertThat(catalog.blocks()).isEqualTo(BuildingBlockCatalog.all());
    }

    @Test
    void workflowAdminGetsTheThreeAdministrationSources() {
        when(lookup.list(TENANT, "documentTemplates")).thenReturn(List.of(new BlockReference("facture", "Facture")));
        when(lookup.list(TENANT, "connectorInstances")).thenReturn(List.of(new BlockReference("uuid-1", "ERP")));
        when(lookup.list(TENANT, "notificationWebhooks")).thenReturn(List.of(new BlockReference("uuid-2", "Slack")));

        for (String source : List.of("documentTemplates", "connectorInstances", "notificationWebhooks")) {
            assertThat(references.list(source, user("WORKFLOW_ADMIN"))).hasSize(1);
        }
        assertThat(references.list("documentTemplates", user("TENANT_ADMIN")))
                .containsExactly(new BlockReference("facture", "Facture"));
    }

    @Test
    void otherRolesAreRefusedBeforeAnyLookup() {
        assertThatThrownBy(() -> references.list("documentTemplates", user("DOSSIER_MANAGER")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(lookup);
    }

    @Test
    void apiServiceIsRefusedLikeTheControllerDoes() {
        // revue M2 : la garde du domaine ne doit pas être plus large que le contrat (TENANT_ADMIN / WORKFLOW_ADMIN)
        assertThatThrownBy(() -> references.list("documentTemplates", user("API_SERVICE")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(lookup);
    }

    @Test
    void onlyTheAdministrationSourcesAreServed() {
        // les autres sources sont lues via leurs API existantes (D-PALETTE-REFERENCES)
        for (String source : List.of("dossierTypes", "workflowTypes", "dmnTables", "nope")) {
            assertThatThrownBy(() -> references.list(source, user("TENANT_ADMIN")))
                    .isInstanceOf(UnknownBlockSourceException.class);
        }
        verifyNoInteractions(lookup);
    }

    @Test
    void referencesCarryOnlyValueAndLabel() {
        assertThat(BlockReference.class.getRecordComponents()).extracting(c -> c.getName())
                .containsExactly("value", "label");
    }
}
