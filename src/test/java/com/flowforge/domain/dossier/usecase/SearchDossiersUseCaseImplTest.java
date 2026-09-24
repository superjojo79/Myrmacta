package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierListItem;
import com.flowforge.domain.dossier.model.DossierPage;
import com.flowforge.domain.dossier.model.DossierPermission;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import com.flowforge.domain.dossier.model.SearchDossiersQuery;
import com.flowforge.domain.dossier.port.out.DossierSearchPort;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SearchDossiersUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");

    private DossierSearchPort searchPort;
    private DossierTypeAclRepository aclRepository;
    private DossierTypeRepository typeRepository;
    private SearchDossiersUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        searchPort = org.mockito.Mockito.mock(DossierSearchPort.class);
        aclRepository = org.mockito.Mockito.mock(DossierTypeAclRepository.class);
        typeRepository = org.mockito.Mockito.mock(DossierTypeRepository.class);
        useCase = new SearchDossiersUseCaseImpl(searchPort, aclRepository, typeRepository);
    }

    @Test
    void adminBypass_searchesWithoutTypeRestriction() {
        when(searchPort.search(eq(TENANT), any(), eq(0), eq(20)))
                .thenReturn(DossierPage.of(List.of(item("FAC")), 0, 20, 1));
        when(typeRepository.findAll(TENANT)).thenReturn(List.of(type("FAC", "Facture")));

        DossierPage page = useCase.search(
                TENANT,
                SearchDossiersQuery.of(null, null, null, 0, 20),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        ArgumentCaptor<DossierSearchPort.Filter> filter = ArgumentCaptor.forClass(DossierSearchPort.Filter.class);
        verify(searchPort).search(eq(TENANT), filter.capture(), eq(0), eq(20));
        assertThat(filter.getValue().dossierTypeCodes()).isNull();
        assertThat(page.content()).singleElement().extracting(DossierListItem::dossierTypeLabel).isEqualTo("Facture");
    }

    @Test
    void userWithViewGrants_restrictsToAllowedTypes() {
        when(aclRepository.findBySubject(TENANT, "u1"))
                .thenReturn(List.of(acl("FAC", DossierPermission.VIEW)));
        when(searchPort.search(eq(TENANT), any(), eq(0), eq(20)))
                .thenReturn(DossierPage.of(List.of(item("FAC")), 0, 20, 1));
        when(typeRepository.findAll(TENANT)).thenReturn(List.of(type("FAC", "Facture")));

        useCase.search(
                TENANT,
                SearchDossiersQuery.of(null, null, null, 0, 20),
                AccessContext.user("u1", Set.of("DOSSIER_MANAGER")));

        ArgumentCaptor<DossierSearchPort.Filter> filter = ArgumentCaptor.forClass(DossierSearchPort.Filter.class);
        verify(searchPort).search(eq(TENANT), filter.capture(), eq(0), eq(20));
        assertThat(filter.getValue().dossierTypeCodes()).containsExactly("FAC");
    }

    @Test
    void zeroGrants_returnsEmptyWithoutCallingPort() {
        when(aclRepository.findBySubject(TENANT, "u1")).thenReturn(List.of());

        DossierPage page = useCase.search(
                TENANT,
                SearchDossiersQuery.of(null, null, null, 0, 20),
                AccessContext.user("u1", Set.of("DOSSIER_VIEWER")));

        assertThat(page.totalElements()).isZero();
        assertThat(page.content()).isEmpty();
        verify(searchPort, never()).search(any(), any(), anyInt(), anyInt());
    }

    @Test
    void typeFilterOutsideAcl_returnsEmptyWithoutCallingPort() {
        when(aclRepository.findBySubject(TENANT, "u1"))
                .thenReturn(List.of(acl("FAC", DossierPermission.VIEW)));

        DossierPage page = useCase.search(
                TENANT,
                SearchDossiersQuery.of(null, "CTR", null, 0, 20),
                AccessContext.user("u1", Set.of("DOSSIER_MANAGER")));

        assertThat(page.content()).isEmpty();
        verify(searchPort, never()).search(any(), any(), anyInt(), anyInt());
    }

    @Test
    void typeFilterInsideAcl_passesSingleTypeToPort() {
        when(aclRepository.findBySubject(TENANT, "u1"))
                .thenReturn(List.of(
                        acl("FAC", DossierPermission.VIEW),
                        acl("CTR", DossierPermission.EDIT)));
        when(searchPort.search(eq(TENANT), any(), eq(0), eq(20)))
                .thenReturn(DossierPage.empty(0, 20));
        when(typeRepository.findAll(TENANT)).thenReturn(List.of());

        useCase.search(
                TENANT,
                SearchDossiersQuery.of(null, "FAC", null, 0, 20),
                AccessContext.user("u1", Set.of("DOSSIER_MANAGER")));

        ArgumentCaptor<DossierSearchPort.Filter> filter = ArgumentCaptor.forClass(DossierSearchPort.Filter.class);
        verify(searchPort).search(eq(TENANT), filter.capture(), eq(0), eq(20));
        assertThat(filter.getValue().dossierTypeCodes()).containsExactly("FAC");
    }

    @Test
    void blankReference_treatedAsAbsent() {
        when(searchPort.search(eq(TENANT), any(), eq(0), eq(20)))
                .thenReturn(DossierPage.empty(0, 20));
        when(typeRepository.findAll(TENANT)).thenReturn(List.of());

        useCase.search(
                TENANT,
                SearchDossiersQuery.of(null, null, "   ", 0, 20),
                AccessContext.user("admin", Set.of("WORKFLOW_ADMIN")));

        ArgumentCaptor<DossierSearchPort.Filter> filter = ArgumentCaptor.forClass(DossierSearchPort.Filter.class);
        verify(searchPort).search(eq(TENANT), filter.capture(), eq(0), eq(20));
        assertThat(filter.getValue().referencePrefix()).isNull();
    }

    @Test
    void missingType_labelIsEmptyString() {
        when(searchPort.search(eq(TENANT), any(), eq(0), eq(20)))
                .thenReturn(DossierPage.of(List.of(item("GONE")), 0, 20, 1));
        when(typeRepository.findAll(TENANT)).thenReturn(List.of());

        DossierPage page = useCase.search(
                TENANT,
                SearchDossiersQuery.of(null, null, null, 0, 20),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(page.content().getFirst().dossierTypeLabel()).isEmpty();
    }

    @Test
    void apiService_searchesWithoutTypeRestriction() {
        when(searchPort.search(eq(TENANT), any(), eq(0), eq(20)))
                .thenReturn(DossierPage.of(List.of(item("FAC")), 0, 20, 1));
        when(typeRepository.findAll(TENANT)).thenReturn(List.of(type("FAC", "Facture")));

        useCase.search(
                TENANT,
                SearchDossiersQuery.of(null, null, null, 0, 20),
                AccessContext.user("svc", Set.of("API_SERVICE")));

        ArgumentCaptor<DossierSearchPort.Filter> filter = ArgumentCaptor.forClass(DossierSearchPort.Filter.class);
        verify(searchPort).search(eq(TENANT), filter.capture(), eq(0), eq(20));
        assertThat(filter.getValue().dossierTypeCodes()).isNull();
    }

    @Test
    void serviceContext_denied() {
        assertThatThrownBy(() -> useCase.search(
                        TENANT,
                        SearchDossiersQuery.of(null, null, null, 0, 20),
                        AccessContext.service("client")))
                .isInstanceOf(AccessDeniedException.class);
        verify(searchPort, never()).search(any(), any(), anyInt(), anyInt());
    }

    private static DossierListItem item(String typeCode) {
        return new DossierListItem(
                UUID.randomUUID(),
                "DOS-" + typeCode + "-2026-000001",
                "Label",
                typeCode,
                "",
                DossierStatus.OUVERT,
                NOW,
                NOW);
    }

    private static DossierType type(String code, String label) {
        return DossierType.inherit(
                UUID.randomUUID(), TENANT, code, label, "wf", true, false, List.of());
    }

    private static DossierTypeAclEntry acl(String code, DossierPermission permission) {
        return new DossierTypeAclEntry(TENANT, code, "u1", permission);
    }

    @Test
    void externalContext_denied() {
        // T-ACTOR-1 (TODO §68)
        assertThatThrownBy(() -> useCase.search(
                        TENANT,
                        SearchDossiersQuery.of(null, null, null, 0, 20),
                        AccessContext.external(java.util.UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);
        verify(searchPort, never()).search(any(), any(), anyInt(), anyInt());
    }
}
