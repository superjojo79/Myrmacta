package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.TenantRetentionSettings;
import com.flowforge.domain.dossier.port.in.UpsertTenantRetentionSettingsUseCase;
import com.flowforge.domain.dossier.port.out.TenantRetentionSettingsRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpsertTenantRetentionSettingsUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private TenantRetentionSettingsRepository repository;
    private UpsertTenantRetentionSettingsUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(TenantRetentionSettingsRepository.class);
        useCase = new UpsertTenantRetentionSettingsUseCaseImpl(repository, new TenantAdminGuard());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void upsert_adminPersistsSettings() {
        TenantRetentionSettings saved = useCase.upsert(
                new UpsertTenantRetentionSettingsUseCase.UpsertCommand(TENANT, 30),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(saved.tenantId()).isEqualTo(TENANT);
        assertThat(saved.defaultRetentionDays()).isEqualTo(30);
        assertThat(saved.updatedBy()).isEqualTo("admin");

        ArgumentCaptor<TenantRetentionSettings> captor =
                ArgumentCaptor.forClass(TenantRetentionSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isEqualTo(saved);
    }

    @Test
    void upsert_rejectsNonPositiveDefaultRetentionDaysWithoutRepositoryCall() {
        assertThatThrownBy(() -> useCase.upsert(
                        new UpsertTenantRetentionSettingsUseCase.UpsertCommand(TENANT, 0),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void upsert_nonAdminDeniedWithoutRepositoryCall() {
        assertThatThrownBy(() -> useCase.upsert(
                        new UpsertTenantRetentionSettingsUseCase.UpsertCommand(TENANT, 30),
                        AccessContext.user("manager", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
    }
}
