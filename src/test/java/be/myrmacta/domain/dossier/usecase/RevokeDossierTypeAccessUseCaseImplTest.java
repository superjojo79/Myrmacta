package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.port.in.RevokeDossierTypeAccessUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RevokeDossierTypeAccessUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String TYPE_CODE = "SINISTRE";
    private static final String SUBJECT = "manager";

    private DossierTypeAclRepository aclRepository;
    private RevokeDossierTypeAccessUseCase useCase;

    @BeforeEach
    void setUp() {
        aclRepository = mock(DossierTypeAclRepository.class);
        useCase = new RevokeDossierTypeAccessUseCaseImpl(aclRepository, new TenantAdminGuard());
    }

    @Test
    void revoke_adminDeletesAclEntry() {
        useCase.revoke(TENANT, TYPE_CODE, SUBJECT, AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        verify(aclRepository).delete(TENANT, TYPE_CODE, SUBJECT);
    }

    @Test
    void revoke_nonAdminThrowsAccessDeniedWithoutDeleting() {
        assertThatThrownBy(() -> useCase.revoke(
                        TENANT, TYPE_CODE, SUBJECT, AccessContext.user("manager", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(aclRepository);
    }
}
