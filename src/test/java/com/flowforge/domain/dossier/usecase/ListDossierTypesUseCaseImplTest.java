package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierPermission;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.shared.PageQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListDossierTypesUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private FakeTypeRepository typeRepository;
    private FakeAclRepository aclRepository;
    private ListDossierTypesUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        typeRepository = new FakeTypeRepository();
        aclRepository = new FakeAclRepository();
        useCase = new ListDossierTypesUseCaseImpl(typeRepository, aclRepository);
        typeRepository.save(type("VIEWABLE"));
        typeRepository.save(type("EDITABLE"));
        typeRepository.save(type("HIDDEN"));
    }

    @Test
    void list_nonAdminIncludesTypesWithViewOrEditAcl() {
        aclRepository.save(new DossierTypeAclEntry(TENANT, "VIEWABLE", "u1", DossierPermission.VIEW));
        aclRepository.save(new DossierTypeAclEntry(TENANT, "EDITABLE", "u1", DossierPermission.EDIT));

        var result = useCase.list(TENANT, AccessContext.user("u1", Set.of("DOSSIER_MANAGER")), PageQuery.defaults());

        assertThat(result.content()).extracting(DossierType::code).containsExactly("VIEWABLE", "EDITABLE");
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    void list_adminAndSystemSeeAllTypes() {
        assertThat(useCase.list(TENANT, AccessContext.user("admin", Set.of("TENANT_ADMIN")), PageQuery.defaults())
                        .content())
                .extracting(DossierType::code)
                .containsExactly("VIEWABLE", "EDITABLE", "HIDDEN");
        assertThat(useCase.list(TENANT, AccessContext.system("batch"), PageQuery.defaults()).content())
                .extracting(DossierType::code)
                .containsExactly("VIEWABLE", "EDITABLE", "HIDDEN");
    }

    @Test
    void list_apiServiceSeesAllTypes() {
        assertThat(useCase.list(TENANT, AccessContext.user("svc", Set.of("API_SERVICE")), PageQuery.defaults())
                        .content())
                .extracting(DossierType::code)
                .containsExactly("VIEWABLE", "EDITABLE", "HIDDEN");
    }

    @Test
    void list_serviceContextIsDenied() {
        assertThatThrownBy(() -> useCase.list(TENANT, AccessContext.service("client"), PageQuery.defaults()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void list_userWithoutGrantsGetsEmptyPageWithoutHoles() {
        var result = useCase.list(TENANT, AccessContext.user("u1", Set.of("DOSSIER_MANAGER")), PageQuery.defaults());
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void list_pagesAfterAclFilter() {
        aclRepository.save(new DossierTypeAclEntry(TENANT, "VIEWABLE", "u1", DossierPermission.VIEW));
        aclRepository.save(new DossierTypeAclEntry(TENANT, "EDITABLE", "u1", DossierPermission.EDIT));

        var result = useCase.list(TENANT, AccessContext.user("u1", Set.of("DOSSIER_MANAGER")), PageQuery.of(1, 1));

        assertThat(result.content()).extracting(DossierType::code).containsExactly("EDITABLE");
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    private static DossierType type(String code) {
        return DossierType.inherit(UUID.randomUUID(), TENANT, code, code, "wf-" + code,
                true, false, List.of());
    }

    private static final class FakeTypeRepository implements DossierTypeRepository {
        private final List<DossierType> types = new ArrayList<>();

        @Override
        public void save(DossierType type) {
            types.add(type);
        }

        @Override
        public Optional<DossierType> findById(UUID id) {
            return types.stream().filter(type -> type.id().equals(id)).findFirst();
        }

        @Override
        public Optional<DossierType> findByCode(String tenantId, String code) {
            return types.stream()
                    .filter(type -> type.tenantId().equals(tenantId) && type.code().equals(code))
                    .findFirst();
        }

        @Override
        public List<DossierType> findAll(String tenantId) {
            return types.stream().filter(type -> type.tenantId().equals(tenantId)).toList();
        }
    }

    private static final class FakeAclRepository implements DossierTypeAclRepository {
        private final List<DossierTypeAclEntry> entries = new ArrayList<>();

        @Override
        public Optional<DossierTypeAclEntry> find(String tenantId, String dossierTypeCode, String subjectUserId) {
            return entries.stream()
                    .filter(entry -> entry.tenantId().equals(tenantId)
                            && entry.dossierTypeCode().equals(dossierTypeCode)
                            && entry.subjectUserId().equals(subjectUserId))
                    .findFirst();
        }

        @Override
        public void save(DossierTypeAclEntry entry) {
            entries.add(entry);
        }

        @Override
        public void delete(String tenantId, String dossierTypeCode, String subjectUserId) {
            entries.removeIf(entry -> entry.tenantId().equals(tenantId)
                    && entry.dossierTypeCode().equals(dossierTypeCode)
                    && entry.subjectUserId().equals(subjectUserId));
        }

        @Override
        public List<DossierTypeAclEntry> findBySubject(String tenantId, String subjectUserId) {
            return entries.stream()
                    .filter(entry -> entry.tenantId().equals(tenantId)
                            && entry.subjectUserId().equals(subjectUserId))
                    .toList();
        }
    }

    @Test
    void list_externalContextIsDenied() {
        // T-ACTOR-1 (TODO §68)
        assertThatThrownBy(() -> useCase.list(
                        TENANT, AccessContext.external(java.util.UUID.randomUUID()), PageQuery.defaults()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
