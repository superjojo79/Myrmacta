package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.TenantRetentionSettings;
import be.myrmacta.domain.dossier.model.TenantRetentionSettingsNotFoundException;
import be.myrmacta.domain.dossier.port.in.GetTenantRetentionSettingsUseCase;
import be.myrmacta.domain.dossier.port.out.TenantRetentionSettingsRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTenantRetentionSettingsUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private TenantRetentionSettingsRepository repository;
    private GetTenantRetentionSettingsUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(TenantRetentionSettingsRepository.class);
        useCase = new GetTenantRetentionSettingsUseCaseImpl(repository, new TenantAdminGuard());
    }

    @Test
    void get_adminReturnsExistingSettings() {
        TenantRetentionSettings settings = new TenantRetentionSettings(TENANT, 30, Instant.now(), "admin");
        when(repository.findByTenantId(TENANT)).thenReturn(Optional.of(settings));

        TenantRetentionSettings result = useCase.get(TENANT, AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(result).isEqualTo(settings);
    }

    @Test
    void get_missingSettingsThrowsNotFound() {
        when(repository.findByTenantId(TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(TENANT, AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(TenantRetentionSettingsNotFoundException.class);
    }

    @Test
    void get_nonAdminDeniedWithoutRepositoryCall() {
        assertThatThrownBy(() -> useCase.get(TENANT, AccessContext.user("manager", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
    }
}
