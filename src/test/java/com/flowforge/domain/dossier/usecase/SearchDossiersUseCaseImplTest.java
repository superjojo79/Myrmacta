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

    // ---- TODO §81 (D2) ----

    private static com.flowforge.domain.dossier.model.FieldDefinition searchable(
            String name, com.flowforge.domain.dossier.model.FieldType t, List<String> options) {
        return new com.flowforge.domain.dossier.model.FieldDefinition(
                name, name, t, false, null, options, null, false, null, null, null, null, null, null, true);
    }

    private void typeWithFields() {
        when(typeRepository.findByCode(TENANT, "SIN")).thenReturn(java.util.Optional.of(new DossierType(
                UUID.randomUUID(), TENANT, "SIN", "Sinistre", "wf", true, true,
                List.of(searchable("contrat", com.flowforge.domain.dossier.model.FieldType.STRING, null),
                        searchable("montant", com.flowforge.domain.dossier.model.FieldType.NUMBER, null),
                        searchable("cat", com.flowforge.domain.dossier.model.FieldType.SELECT, List.of("A", "B")),
                        new com.flowforge.domain.dossier.model.FieldDefinition("note", "note",
                                com.flowforge.domain.dossier.model.FieldType.STRING, false, null, null, null, false)),
                null, false, null, "t", "t")));
        when(searchPort.search(eq(TENANT), any(), anyInt(), anyInt())).thenReturn(DossierPage.empty(0, 20));
    }

    private static com.flowforge.domain.dossier.model.FieldCondition cond(
            String f, com.flowforge.domain.dossier.model.FieldCondition.Op op, String... v) {
        return new com.flowforge.domain.dossier.model.FieldCondition(f, op, List.of(v));
    }

    private DossierPage searchAsAdmin(String type, com.flowforge.domain.dossier.model.FieldCondition... c) {
        return useCase.search(TENANT, SearchDossiersQuery.of(null, type, null, 0, 20, List.of(c)),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));
    }

    @Test
    void fieldFiltersAreTypedAndHandedToThePort() {
        typeWithFields();
        searchAsAdmin("SIN",
                cond("montant", com.flowforge.domain.dossier.model.FieldCondition.Op.GTE, "5000.50"),
                cond("contrat", com.flowforge.domain.dossier.model.FieldCondition.Op.EQ, "C-42"));
        ArgumentCaptor<DossierSearchPort.Filter> filter = ArgumentCaptor.forClass(DossierSearchPort.Filter.class);
        verify(searchPort).search(eq(TENANT), filter.capture(), eq(0), eq(20));
        assertThat(filter.getValue().fieldPredicates()).hasSize(2);
        assertThat(filter.getValue().fieldPredicates().get(0).kind()).isEqualTo(DossierSearchPort.Kind.NUMBER);
        assertThat(filter.getValue().fieldPredicates().get(0).values()).containsExactly(new java.math.BigDecimal("5000.50"));
        assertThat(filter.getValue().fieldPredicates().get(1).kind()).isEqualTo(DossierSearchPort.Kind.TEXT);
    }

    @Test
    void invalidFieldFiltersAreRefusedNeverSilentlyEmpty() {
        typeWithFields();
        var op = com.flowforge.domain.dossier.model.FieldCondition.Op.class;
        assertThatThrownBy(() -> searchAsAdmin("SIN", cond("note", op.getEnumConstants()[0], "x")))
                .isInstanceOf(com.flowforge.domain.dossier.model.InvalidDossierSearchException.class).hasMessageContaining("not searchable");
        assertThatThrownBy(() -> searchAsAdmin("SIN", cond("ghost", op.getEnumConstants()[0], "x")))
                .hasMessageContaining("not defined");
        assertThatThrownBy(() -> searchAsAdmin("SIN", cond("montant", com.flowforge.domain.dossier.model.FieldCondition.Op.PREFIX, "5")))
                .hasMessageContaining("not supported");
        assertThatThrownBy(() -> searchAsAdmin("SIN", cond("montant", com.flowforge.domain.dossier.model.FieldCondition.Op.GTE, "1e3")))
                .hasMessageContaining("plain decimal");
        assertThatThrownBy(() -> searchAsAdmin("SIN", cond("cat", com.flowforge.domain.dossier.model.FieldCondition.Op.EQ, "Z")))
                .hasMessageContaining("not an option");
        assertThatThrownBy(() -> searchAsAdmin(null, cond("contrat", com.flowforge.domain.dossier.model.FieldCondition.Op.EQ, "x")))
                .hasMessageContaining("dossierTypeCode");
        assertThatThrownBy(() -> searchAsAdmin("NOPE", cond("contrat", com.flowforge.domain.dossier.model.FieldCondition.Op.EQ, "x")))
                .hasMessageContaining("unknown dossier type");
        verify(searchPort, never()).search(any(), any(), anyInt(), anyInt());
    }

    @Test
    void filtersAreValidatedEvenWhenTheCallerSeesNoType() {
        typeWithFields();
        when(aclRepository.findBySubject(TENANT, "u")).thenReturn(List.of());
        assertThatThrownBy(() -> useCase.search(TENANT,
                SearchDossiersQuery.of(null, "SIN", null, 0, 20,
                        List.of(cond("note", com.flowforge.domain.dossier.model.FieldCondition.Op.EQ, "x"))),
                AccessContext.user("u", Set.of("USER"))))
                .isInstanceOf(com.flowforge.domain.dossier.model.InvalidDossierSearchException.class);
    }
}
