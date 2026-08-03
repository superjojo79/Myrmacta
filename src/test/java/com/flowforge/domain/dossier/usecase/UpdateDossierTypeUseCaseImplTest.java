package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.in.UpdateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateDossierTypeUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String TYPE_CODE = "SINISTRE";

    private DossierTypeRepository repository;
    private UpdateDossierTypeUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(DossierTypeRepository.class);
        useCase = new UpdateDossierTypeUseCaseImpl(repository, new TenantAdminGuard());
    }

    @Test
    void update_adminUpdatesMutableFieldsAndKeepsCodeUnchanged() {
        DossierType existing = type("Old label", true, List.of(field("priority", FieldType.STRING)));
        List<FieldDefinition> newFields = List.of(field("amount", FieldType.NUMBER));
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(existing));

        DossierType updated = useCase.update(
                new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                        TENANT, TYPE_CODE, "New label", "wf-new", false, true, newFields),
                admin());

        assertThat(updated.id()).isEqualTo(existing.id());
        assertThat(updated.tenantId()).isEqualTo(TENANT);
        assertThat(updated.code()).isEqualTo(TYPE_CODE);
        assertThat(updated.label()).isEqualTo("New label");
        assertThat(updated.workflowKey()).isEqualTo("wf-new");
        assertThat(updated.active()).isFalse();
        assertThat(updated.allowUnknownFields()).isTrue();
        assertThat(updated.fieldDefinitions()).isEqualTo(newFields);

        ArgumentCaptor<DossierType> saved = ArgumentCaptor.forClass(DossierType.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(updated);
    }

    @Test
    void update_nonAdminThrowsAccessDeniedWithoutRepositoryCall() {
        assertThatThrownBy(() -> useCase.update(command(), AccessContext.user("manager", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void update_missingTypeThrowsNotFoundWithoutSaving() {
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(command(), admin()))
                .isInstanceOf(DossierNotFoundException.class);

        verify(repository).findByCode(TENANT, TYPE_CODE);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static UpdateDossierTypeUseCase.UpdateDossierTypeCommand command() {
        return new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                TENANT, TYPE_CODE, "New label", "wf-new", false, false, List.of());
    }

    private static AccessContext.User admin() {
        return AccessContext.user("admin", Set.of("TENANT_ADMIN"));
    }

    private static DossierType type(String label, boolean active, List<FieldDefinition> fields) {
        return new DossierType(UUID.randomUUID(), TENANT, TYPE_CODE, label, "wf-old", active, false, fields);
    }

    private static FieldDefinition field(String name, FieldType type) {
        return new FieldDefinition(name, name, type, false, null, List.of(), null);
    }
}
