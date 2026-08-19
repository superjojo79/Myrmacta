package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetDossierTypeUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String TYPE_CODE = "SINISTRE";

    private DossierTypeRepository repository;
    private DossierAccessPolicy accessPolicy;
    private GetDossierTypeUseCase useCase;
    private AccessContext.User viewer;

    @BeforeEach
    void setUp() {
        repository = mock(DossierTypeRepository.class);
        accessPolicy = mock(DossierAccessPolicy.class);
        useCase = new GetDossierTypeUseCaseImpl(repository, accessPolicy);
        viewer = AccessContext.user("viewer", Set.of("DOSSIER_VIEWER"));
    }

    @Test
    void getByCode_whenViewAllowedReturnsType() {
        DossierType type = type();
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(type));

        DossierType result = useCase.getByCode(TENANT, TYPE_CODE, viewer);

        assertThat(result).isEqualTo(type);
        verify(accessPolicy).assertCanViewType(TENANT, TYPE_CODE, viewer);
    }

    @Test
    void getByCode_whenViewDeniedThrowsAccessDenied() {
        DossierType type = type();
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(type));
        doThrow(new AccessDeniedException("denied"))
                .when(accessPolicy).assertCanViewType(TENANT, TYPE_CODE, viewer);

        assertThatThrownBy(() -> useCase.getByCode(TENANT, TYPE_CODE, viewer))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getByCode_whenMissingThrowsNotFoundBeforeAccessCheck() {
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getByCode(TENANT, TYPE_CODE, viewer))
                .isInstanceOf(DossierNotFoundException.class);

        verifyNoInteractions(accessPolicy);
    }

    private static DossierType type() {
        return DossierType.inherit(UUID.randomUUID(), TENANT, TYPE_CODE, "Sinistre", "wf-sinistre",
                true, false, List.of());
    }
}
