package com.flowforge.domain.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.model.TenantSlaSettingsNotFoundException;
import com.flowforge.domain.reporting.port.in.GetTenantSlaSettingsUseCase;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTenantSlaSettingsUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private TenantSlaSettingsRepository repository;
    private GetTenantSlaSettingsUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(TenantSlaSettingsRepository.class);
        useCase = new GetTenantSlaSettingsUseCaseImpl(repository, new TenantAdminGuard());
    }

    @Test
    void get_adminReturnsExistingSettings() {
        TenantSlaSettings settings = new TenantSlaSettings(TENANT, 90, Instant.now(), "admin");
        when(repository.findByTenantId(TENANT)).thenReturn(Optional.of(settings));

        TenantSlaSettings result = useCase.get(TENANT, AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(result).isEqualTo(settings);
    }

    @Test
    void get_missingSettingsThrowsNotFound() {
        when(repository.findByTenantId(TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(TENANT, AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(TenantSlaSettingsNotFoundException.class);
    }

    @Test
    void get_nonAdminDeniedWithoutRepositoryCall() {
        assertThatThrownBy(() -> useCase.get(TENANT, AccessContext.user("manager", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
    }
}
