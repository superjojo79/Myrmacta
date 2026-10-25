package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.DossierTypeSchemaIncompatibleException;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.model.InvalidRetentionPolicyException;
import be.myrmacta.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
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
    private CheckDossierTypeSchemaCompatibilityUseCase checkCompatibilityUseCase;
    private UpdateDossierTypeUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(DossierTypeRepository.class);
        checkCompatibilityUseCase = mock(CheckDossierTypeSchemaCompatibilityUseCase.class);
        when(checkCompatibilityUseCase.check(any()))
                .thenReturn(new CheckDossierTypeSchemaCompatibilityUseCase.Result(0, List.of()));
        useCase = new UpdateDossierTypeUseCaseImpl(repository, new TenantAdminGuard(), checkCompatibilityUseCase);
    }

    @Test
    void update_adminUpdatesMutableFieldsAndKeepsCodeUnchanged() {
        DossierType existing = type("Old label", true, List.of(field("priority", FieldType.STRING)));
        List<FieldDefinition> newFields = List.of(field("amount", FieldType.NUMBER));
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(existing));

        DossierType updated = useCase.update(
                new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                        TENANT, TYPE_CODE, "New label", "wf-new", false, true, newFields, null, false, null, List.of()),
                admin());

        assertThat(updated.id()).isEqualTo(existing.id());
        assertThat(updated.tenantId()).isEqualTo(TENANT);
        assertThat(updated.code()).isEqualTo(TYPE_CODE);
        assertThat(updated.label()).isEqualTo("New label");
        assertThat(updated.workflowKey()).isEqualTo("wf-new");
        assertThat(updated.active()).isFalse();
        assertThat(updated.allowUnknownFields()).isTrue();
        assertThat(updated.fieldDefinitions()).isEqualTo(newFields);
        assertThat(updated.retentionDays()).isNull();
        assertThat(updated.retentionNever()).isFalse();

        ArgumentCaptor<DossierType> saved = ArgumentCaptor.forClass(DossierType.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(updated);
    }

    @Test
    void update_persistsRetentionOverride() {
        DossierType existing = type("Old label", true, List.of());
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(existing));

        DossierType updated = useCase.update(
                new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                        TENANT, TYPE_CODE, "Old label", "wf-old", true, false, List.of(), 90, false, null, List.of()),
                admin());

        assertThat(updated.retentionDays()).isEqualTo(90);
        assertThat(updated.retentionNever()).isFalse();
    }

    @Test
    void update_rejectsRetentionNeverWithDaysSet() {
        DossierType existing = type("Old label", true, List.of());
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                                TENANT, TYPE_CODE, "Old label", "wf-old", true, false, List.of(), 30, true, null, List.of()),
                        admin()))
                .isInstanceOf(InvalidRetentionPolicyException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void update_rejectsNonPositiveRetentionDays() {
        DossierType existing = type("Old label", true, List.of());
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                                TENANT, TYPE_CODE, "Old label", "wf-old", true, false, List.of(), -1, false, null, List.of()),
                        admin()))
                .isInstanceOf(InvalidRetentionPolicyException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void update_stampsUpdatedByWithActorAndKeepsCreatedBy() {
        DossierType existing = type("Old label", true, List.of());
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(existing));

        DossierType updated = useCase.update(command(), admin());

        assertThat(updated.createdBy()).isEqualTo(existing.createdBy());
        assertThat(updated.updatedBy()).isEqualTo("admin");
    }

    @Test
    void update_apiServiceUpdatesType() {
        DossierType existing = type("Old label", true, List.of());
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(existing));

        DossierType updated = useCase.update(command(), AccessContext.user("svc", Set.of("API_SERVICE")));

        assertThat(updated.label()).isEqualTo("New label");
        verify(repository).save(updated);
    }

    @Test
    void update_nonAdminThrowsAccessDeniedWithoutRepositoryCall() {
        assertThatThrownBy(() -> useCase.update(command(), AccessContext.user("manager", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
        verifyNoInteractions(checkCompatibilityUseCase);
    }

    @Test
    void update_missingTypeThrowsNotFoundWithoutSaving() {
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(command(), admin()))
                .isInstanceOf(DossierNotFoundException.class);

        verify(repository).findByCode(TENANT, TYPE_CODE);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(checkCompatibilityUseCase);
    }

    @Test
    void update_incompatibleSchema_throwsWithoutSaving() {
        DossierType existing = type("Old label", true, List.of());
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(existing));
        when(checkCompatibilityUseCase.check(any()))
                .thenReturn(new CheckDossierTypeSchemaCompatibilityUseCase.Result(3, List.of("REF-1", "REF-2")));

        assertThatThrownBy(() -> useCase.update(command(), admin()))
                .isInstanceOf(DossierTypeSchemaIncompatibleException.class)
                .satisfies(ex -> {
                    var incompatible = (DossierTypeSchemaIncompatibleException) ex;
                    assertThat(incompatible.incompatibleCount()).isEqualTo(3);
                    assertThat(incompatible.sampleReferences()).containsExactly("REF-1", "REF-2");
                });

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void update_checksCompatibilityWithNewSchemaBeforeSaving() {
        DossierType existing = type("Old label", true, List.of(field("priority", FieldType.STRING)));
        List<FieldDefinition> newFields = List.of(field("amount", FieldType.NUMBER));
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(existing));

        useCase.update(
                new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                        TENANT, TYPE_CODE, "New label", "wf-new", false, true, newFields, null, false, null, List.of()),
                admin());

        ArgumentCaptor<CheckDossierTypeSchemaCompatibilityUseCase.Query> captor =
                ArgumentCaptor.forClass(CheckDossierTypeSchemaCompatibilityUseCase.Query.class);
        verify(checkCompatibilityUseCase).check(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().dossierTypeCode()).isEqualTo(TYPE_CODE);
        assertThat(captor.getValue().newFieldDefinitions()).isEqualTo(newFields);
        assertThat(captor.getValue().allowUnknownFields()).isTrue();
    }

    @Test
    void update_duplicateFieldName_failsFastWithoutQueryingCompatibility() {
        DossierType existing = type("Old label", true, List.of());
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(existing));
        List<FieldDefinition> duplicated = List.of(
                field("amount", FieldType.NUMBER), field("amount", FieldType.STRING));

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                                TENANT, TYPE_CODE, "New label", "wf-new", false, true, duplicated, null, false, null, List.of()),
                        admin()))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(checkCompatibilityUseCase);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void update_persistsDocumentTemplateKeys() {
        DossierType existing = type("Old label", true, List.of());
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(existing));

        DossierType updated = useCase.update(
                new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                        TENANT, TYPE_CODE, "Old label", "wf-old", true, false, List.of(), null, false, null,
                        List.of("invoice", "contract")),
                admin());

        assertThat(updated.documentTemplateKeys()).containsExactly("invoice", "contract");
    }

    @Test
    void update_typeWithoutExistingDossiers_isAlwaysCompatible() {
        DossierType existing = type("Old label", true, List.of());
        when(repository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(existing));
        when(checkCompatibilityUseCase.check(any()))
                .thenReturn(new CheckDossierTypeSchemaCompatibilityUseCase.Result(0, List.of()));

        DossierType updated = useCase.update(command(), admin());

        verify(repository).save(updated);
    }

    private static UpdateDossierTypeUseCase.UpdateDossierTypeCommand command() {
        return new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                TENANT, TYPE_CODE, "New label", "wf-new", false, false, List.of(), null, false, null, List.of());
    }

    private static AccessContext.User admin() {
        return AccessContext.user("admin", Set.of("TENANT_ADMIN"));
    }

    private static DossierType type(String label, boolean active, List<FieldDefinition> fields) {
        return DossierType.inherit(UUID.randomUUID(), TENANT, TYPE_CODE, label, "wf-old", active, false, fields);
    }

    private static FieldDefinition field(String name, FieldType type) {
        return new FieldDefinition(name, name, type, false, null, List.of(), null);
    }
}
