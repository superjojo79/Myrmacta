package com.flowforge.domain.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.port.in.UpsertTenantSlaSettingsUseCase;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpsertTenantSlaSettingsUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-03-15T00:00:00Z");

    private TenantSlaSettingsRepository repository;
    private UpsertTenantSlaSettingsUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(TenantSlaSettingsRepository.class);
        useCase = new UpsertTenantSlaSettingsUseCaseImpl(
                repository, new TenantAdminGuard(), Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void upsert_adminPersistsSettings() {
        TenantSlaSettings saved = useCase.upsert(
                new UpsertTenantSlaSettingsUseCase.Command(TENANT, 90),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(saved.tenantId()).isEqualTo(TENANT);
        assertThat(saved.complianceTargetPercent()).isEqualTo(90);
        assertThat(saved.updatedBy()).isEqualTo("admin");
        assertThat(saved.updatedAt()).isEqualTo(NOW);

        ArgumentCaptor<TenantSlaSettings> captor = ArgumentCaptor.forClass(TenantSlaSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isEqualTo(saved);
    }

    @Test
    void upsert_rejectsPercentOutsideOneToHundred() {
        assertThatThrownBy(() -> useCase.upsert(
                        new UpsertTenantSlaSettingsUseCase.Command(TENANT, 0),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complianceTargetPercent");
        assertThatThrownBy(() -> useCase.upsert(
                        new UpsertTenantSlaSettingsUseCase.Command(TENANT, 101),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void upsert_nonAdminDeniedWithoutRepositoryCall() {
        assertThatThrownBy(() -> useCase.upsert(
                        new UpsertTenantSlaSettingsUseCase.Command(TENANT, 90),
                        AccessContext.user("manager", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
    }
}
