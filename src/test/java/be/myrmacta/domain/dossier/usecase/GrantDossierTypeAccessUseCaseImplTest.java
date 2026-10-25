package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierPermission;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.in.GrantDossierTypeAccessUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrantDossierTypeAccessUseCaseImplTest {

    private DossierTypeRepository typeRepository;
    private DossierTypeAclRepository aclRepository;
    private GrantDossierTypeAccessUseCase useCase;

    @BeforeEach
    void setUp() {
        typeRepository = mock(DossierTypeRepository.class);
        aclRepository = mock(DossierTypeAclRepository.class);
        useCase = new GrantDossierTypeAccessUseCaseImpl(
                typeRepository, aclRepository, new TenantAdminGuard());
    }

    @Test
    void grant_persistsWhenTypeExistsAndActorIsAdmin() {
        when(typeRepository.findByCode("tenant_dev", "SINISTRE"))
                .thenReturn(Optional.of(DossierType.inherit(
                        UUID.randomUUID(),
                        "tenant_dev",
                        "SINISTRE",
                        "Sinistre",
                        "wf",
                        true,
                        false,
                        List.of())));

        useCase.grant(
                new GrantDossierTypeAccessUseCase.GrantCommand(
                        "tenant_dev", "SINISTRE", "user-1", DossierPermission.EDIT),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        verify(aclRepository).save(any());
    }

    @Test
    void grant_throwsNotFoundWhenTypeMissing() {
        when(typeRepository.findByCode("tenant_dev", "MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.grant(
                        new GrantDossierTypeAccessUseCase.GrantCommand(
                                "tenant_dev", "MISSING", "user-1", DossierPermission.VIEW),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(DossierNotFoundException.class);

        verify(aclRepository, never()).save(any());
    }

    @Test
    void grant_rejectsNonAdmin() {
        assertThatThrownBy(() -> useCase.grant(
                        new GrantDossierTypeAccessUseCase.GrantCommand(
                                "tenant_dev", "SINISTRE", "user-1", DossierPermission.VIEW),
                        AccessContext.user("u1", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);

        verify(aclRepository, never()).save(any());
    }
}
