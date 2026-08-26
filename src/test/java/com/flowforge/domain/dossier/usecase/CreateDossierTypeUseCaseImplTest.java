package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.InvalidRetentionPolicyException;
import com.flowforge.domain.dossier.port.in.CreateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateDossierTypeUseCaseImplTest {

    private DossierTypeRepository repository;
    private CreateDossierTypeUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(DossierTypeRepository.class);
        useCase = new CreateDossierTypeUseCaseImpl(repository, new TenantAdminGuard());
    }

    @Test
    void create_adminPersistsType() {
        DossierType created = useCase.create(
                new CreateDossierTypeUseCase.CreateDossierTypeCommand(
                        "tenant_dev",
                        "CLAIM",
                        "Claim",
                        "wf-claim",
                        true,
                        false,
                        List.of(),
                        null,
                        false,
                        null),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(created.code()).isEqualTo("CLAIM");
        assertThat(created.tenantId()).isEqualTo("tenant_dev");
        assertThat(created.retentionDays()).isNull();
        assertThat(created.retentionNever()).isFalse();

        ArgumentCaptor<DossierType> saved = ArgumentCaptor.forClass(DossierType.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(created);
    }

    @Test
    void create_persistsRetentionOverrideDays() {
        DossierType created = useCase.create(
                new CreateDossierTypeUseCase.CreateDossierTypeCommand(
                        "tenant_dev",
                        "LEGAL",
                        "Legal",
                        "wf-legal",
                        true,
                        false,
                        List.of(),
                        365,
                        false,
                        null),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(created.retentionDays()).isEqualTo(365);
        assertThat(created.retentionNever()).isFalse();
    }

    @Test
    void create_persistsSlaTargetHours() {
        DossierType created = useCase.create(
                new CreateDossierTypeUseCase.CreateDossierTypeCommand(
                        "tenant_dev",
                        "SIN",
                        "Sinistre",
                        "wf-sin",
                        true,
                        false,
                        List.of(),
                        null,
                        false,
                        48),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(created.slaTargetHours()).isEqualTo(48);
    }

    @Test
    void create_persistsRetentionNever() {
        DossierType created = useCase.create(
                new CreateDossierTypeUseCase.CreateDossierTypeCommand(
                        "tenant_dev",
                        "LOG",
                        "Log",
                        "wf-log",
                        true,
                        false,
                        List.of(),
                        null,
                        true,
                        null),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(created.retentionDays()).isNull();
        assertThat(created.retentionNever()).isTrue();
    }

    @Test
    void create_rejectsRetentionNeverWithDaysSet() {
        assertThatThrownBy(() -> useCase.create(
                        new CreateDossierTypeUseCase.CreateDossierTypeCommand(
                                "tenant_dev",
                                "LOG",
                                "Log",
                                "wf-log",
                                true,
                                false,
                                List.of(),
                                30,
                                true,
                        null),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(InvalidRetentionPolicyException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void create_rejectsNonPositiveRetentionDays() {
        assertThatThrownBy(() -> useCase.create(
                        new CreateDossierTypeUseCase.CreateDossierTypeCommand(
                                "tenant_dev",
                                "LEGAL",
                                "Legal",
                                "wf-legal",
                                true,
                                false,
                                List.of(),
                                0,
                                false,
                        null),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(InvalidRetentionPolicyException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void create_nonAdminDeniedWithoutRepositoryCall() {
        assertThatThrownBy(() -> useCase.create(
                        new CreateDossierTypeUseCase.CreateDossierTypeCommand(
                                "tenant_dev",
                                "CLAIM",
                                "Claim",
                                "wf-claim",
                                true,
                                false,
                                List.of(),
                                null,
                                false,
                        null),
                        AccessContext.user("manager", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
    }
}
