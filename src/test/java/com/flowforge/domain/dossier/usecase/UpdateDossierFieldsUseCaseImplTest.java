package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.DossierFieldValidationException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.in.UpdateDossierFieldsUseCase.UpdateDossierFieldsCommand;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdateDossierFieldsUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String TYPE_CODE = "SINISTRE";

    private DossierRepository dossierRepository;
    private DossierTypeRepository typeRepository;
    private DossierAccessPolicy accessPolicy;
    private UpdateDossierFieldsUseCaseImpl useCase;
    private AccessContext.User actor;

    @BeforeEach
    void setUp() {
        dossierRepository = mock(DossierRepository.class);
        typeRepository = mock(DossierTypeRepository.class);
        accessPolicy = mock(DossierAccessPolicy.class);
        useCase = new UpdateDossierFieldsUseCaseImpl(dossierRepository, typeRepository, accessPolicy);
        actor = AccessContext.user("u1", Set.of("DOSSIER_MANAGER"));
    }

    @Test
    void update_mergesValidatesSavesAndReturnsResultWithChangedSensitiveFields() {
        Dossier existing = dossier(DossierStatus.OUVERT, 3, Map.of("montant", 100, "ssn", "111"));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(typeRepository.findByCode(TENANT, TYPE_CODE))
                .thenReturn(Optional.of(type(field("montant", FieldType.NUMBER, false), sensitiveField("ssn"))));
        when(dossierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.update(
                new UpdateDossierFieldsCommand(existing.id(), Map.of("montant", 200, "ssn", "222"), "editor", 3L),
                actor);

        assertThat(result.dossier().fields()).containsEntry("montant", 200).containsEntry("ssn", "222");
        assertThat(result.dossier().updatedBy()).isEqualTo("editor");
        assertThat(result.dossier().updatedAt()).isAfterOrEqualTo(existing.updatedAt());
        assertThat(result.sensitiveFieldsChanged()).containsExactly("ssn");
    }

    @Test
    void update_rejectsNullFieldsPatch() {
        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierFieldsCommand(UUID.randomUUID(), null, "editor", null), actor))
                .isInstanceOf(IllegalArgumentException.class);

        verify(dossierRepository, never()).findById(any());
    }

    @Test
    void update_rejectsEmptyFieldsPatch() {
        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierFieldsCommand(UUID.randomUUID(), Map.of(), "editor", null), actor))
                .isInstanceOf(IllegalArgumentException.class);

        verify(dossierRepository, never()).findById(any());
    }

    @Test
    void update_rejectsWhenActorLacksEdit() {
        Dossier existing = dossier(DossierStatus.OUVERT, 3, Map.of("montant", 100));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        org.mockito.Mockito.doThrow(new AccessDeniedException("Access denied for permission EDIT"))
                .when(accessPolicy)
                .assertCanEdit(existing, actor);

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierFieldsCommand(existing.id(), Map.of("montant", 200), "editor", null),
                        actor))
                .isInstanceOf(AccessDeniedException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void update_rejectsWhenDossierNotFound() {
        UUID id = UUID.randomUUID();
        when(dossierRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierFieldsCommand(id, Map.of("montant", 200), "editor", null), actor))
                .isInstanceOf(DossierNotFoundException.class);
    }

    @Test
    void update_rejectsWhenStatusIsCloture() {
        Dossier existing = dossier(DossierStatus.CLOTURE, 3, Map.of("montant", 100));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierFieldsCommand(existing.id(), Map.of("montant", 200), "editor", null),
                        actor))
                .isInstanceOf(DossierConflictException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void update_rejectsWhenStatusIsArchive() {
        Dossier existing = dossier(DossierStatus.ARCHIVE, 3, Map.of("montant", 100));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierFieldsCommand(existing.id(), Map.of("montant", 200), "editor", null),
                        actor))
                .isInstanceOf(DossierConflictException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void update_rejectsClotureEvenWithMatchingExpectedVersion() {
        // Requirements edge case: the status gate is checked independently of the optimistic
        // lock — a CLOTURE dossier is rejected even when expectedVersion matches exactly, and
        // the error message must reflect the status cause, not the version cause, so a client
        // can tell the two 409s apart.
        Dossier existing = dossier(DossierStatus.CLOTURE, 3, Map.of("montant", 100));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierFieldsCommand(existing.id(), Map.of("montant", 200), "editor", 3L),
                        actor))
                .isInstanceOf(DossierConflictException.class)
                .hasMessageContaining("status")
                .hasMessageNotContaining("Expected version");

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void update_acceptsEditingOnNonBlockedStatuses() {
        for (DossierStatus status :
                List.of(DossierStatus.DRAFT, DossierStatus.OUVERT, DossierStatus.EN_COURS, DossierStatus.SUSPENDU)) {
            Dossier existing = dossier(status, 3, Map.of("montant", 100));
            when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));
            when(typeRepository.findByCode(TENANT, TYPE_CODE))
                    .thenReturn(Optional.of(type(field("montant", FieldType.NUMBER, false))));
            when(dossierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            var result = useCase.update(
                    new UpdateDossierFieldsCommand(existing.id(), Map.of("montant", 200), "editor", null), actor);

            assertThat(result.dossier().fields()).containsEntry("montant", 200);
        }
    }

    @Test
    void update_rejectsWhenExpectedVersionDiffers() {
        Dossier existing = dossier(DossierStatus.OUVERT, 3, Map.of("montant", 100));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierFieldsCommand(existing.id(), Map.of("montant", 200), "editor", 2L), actor))
                .isInstanceOf(DossierConflictException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void update_acceptsWithoutExpectedVersion() {
        Dossier existing = dossier(DossierStatus.OUVERT, 3, Map.of("montant", 100));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(typeRepository.findByCode(TENANT, TYPE_CODE))
                .thenReturn(Optional.of(type(field("montant", FieldType.NUMBER, false))));
        when(dossierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.update(
                new UpdateDossierFieldsCommand(existing.id(), Map.of("montant", 200), "editor", null), actor);

        assertThat(result.dossier().fields()).containsEntry("montant", 200);
    }

    @Test
    void update_rejectsClearingARequiredField() {
        Dossier existing = dossier(DossierStatus.OUVERT, 3, Map.of("montant", 100));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(typeRepository.findByCode(TENANT, TYPE_CODE))
                .thenReturn(Optional.of(type(field("montant", FieldType.NUMBER, true))));
        Map<String, Object> patch = new HashMap<>();
        patch.put("montant", null);

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierFieldsCommand(existing.id(), patch, "editor", null), actor))
                .isInstanceOf(DossierFieldValidationException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void update_rejectsTypeMismatchInMergedSet() {
        Dossier existing = dossier(DossierStatus.OUVERT, 3, Map.of("montant", 100));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(typeRepository.findByCode(TENANT, TYPE_CODE))
                .thenReturn(Optional.of(type(field("montant", FieldType.NUMBER, false))));

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierFieldsCommand(existing.id(), Map.of("montant", "not-a-number"), "editor", null),
                        actor))
                .isInstanceOf(DossierFieldValidationException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void update_rejectsUnknownFieldWhenNotAllowed() {
        Dossier existing = dossier(DossierStatus.OUVERT, 3, Map.of("montant", 100));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(typeRepository.findByCode(TENANT, TYPE_CODE))
                .thenReturn(Optional.of(type(false, field("montant", FieldType.NUMBER, false))));

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierFieldsCommand(existing.id(), Map.of("mystere", "x"), "editor", null), actor))
                .isInstanceOf(DossierFieldValidationException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void update_acceptsEditingEvenWhenDossierTypeInactive() {
        Dossier existing = dossier(DossierStatus.OUVERT, 3, Map.of("montant", 100));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(typeRepository.findByCode(TENANT, TYPE_CODE))
                .thenReturn(Optional.of(inactiveType(field("montant", FieldType.NUMBER, false))));
        when(dossierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.update(
                new UpdateDossierFieldsCommand(existing.id(), Map.of("montant", 200), "editor", null), actor);

        assertThat(result.dossier().fields()).containsEntry("montant", 200);
    }

    @Test
    void update_allowsChangingSensitiveFieldForAnyEditHolder() {
        Dossier existing = dossier(DossierStatus.OUVERT, 3, Map.of("ssn", "111"));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(typeRepository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(type(sensitiveField("ssn"))));
        when(dossierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.update(
                new UpdateDossierFieldsCommand(existing.id(), Map.of("ssn", "222"), "editor", null), actor);

        assertThat(result.sensitiveFieldsChanged()).containsExactly("ssn");
        verify(dossierRepository).save(any());
    }

    private static Dossier dossier(DossierStatus status, long version, Map<String, Object> fields) {
        Instant createdAt = Instant.parse("2026-07-31T12:00:00Z");
        return new Dossier(
                UUID.randomUUID(),
                TENANT,
                "DOS-SINISTRE-2026-000001",
                TYPE_CODE,
                "Incident A",
                status,
                fields,
                null,
                version,
                "creator",
                createdAt,
                "creator",
                createdAt,
                null,
                null);
    }

    private static DossierType type(FieldDefinition... definitions) {
        return type(true, definitions);
    }

    private static DossierType type(boolean allowUnknownFields, FieldDefinition... definitions) {
        return new DossierType(
                UUID.randomUUID(), TENANT, TYPE_CODE, "Sinistre", "sinistre-workflow", true, allowUnknownFields,
                List.of(definitions));
    }

    private static DossierType inactiveType(FieldDefinition... definitions) {
        return new DossierType(
                UUID.randomUUID(), TENANT, TYPE_CODE, "Sinistre", "sinistre-workflow", false, true,
                List.of(definitions));
    }

    private static FieldDefinition field(String name, FieldType type, boolean required) {
        return new FieldDefinition(name, name, type, required, null, List.of(), null, false);
    }

    private static FieldDefinition sensitiveField(String name) {
        return new FieldDefinition(name, name, FieldType.STRING, false, null, List.of(), null, true);
    }
}
