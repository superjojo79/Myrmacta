package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierConflictException;
import be.myrmacta.domain.dossier.model.DossierFieldValidationException;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.in.UpdateDossierFieldsUseCase.UpdateDossierFieldsCommand;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
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

    /** TODO §102 (U8) — le dossier corrigé est exclu de la recherche du détenteur ; un autre détenteur est refusé. */
    @Test
    void update_refusesAValueHeldByAnotherOpenDossier_andExcludesItself() {
        var plaque = new FieldDefinition("plaque", "Plaque", FieldType.STRING, false, null, null, null, false, null, null,
                null, null, null, null, false, null, null, true);
        Dossier existing = dossier(DossierStatus.EN_COURS, 1, Map.of("plaque", "BE-1"));
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(typeRepository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(type(plaque)));
        when(dossierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var key = new be.myrmacta.domain.dossier.model.UniqueKey("plaque", "BE-2");
        var port = mock(be.myrmacta.domain.dossier.port.out.DossierUniquenessPort.class);
        when(port.findHolders(TENANT, TYPE_CODE, Set.of(key), existing.id())).thenReturn(Map.of(key, "DOS-9"));
        var checked = new UpdateDossierFieldsUseCaseImpl(dossierRepository, typeRepository, accessPolicy,
                new be.myrmacta.domain.dossier.model.DossierFieldValidator(),
                be.myrmacta.domain.dossier.DossierFieldRules.disabled(),
                new be.myrmacta.domain.dossier.DossierUniquenessCheck(port));

        assertThatThrownBy(() -> checked.update(
                new UpdateDossierFieldsCommand(existing.id(), Map.of("plaque", "BE-2"), "editor", 1L), actor))
                .isInstanceOfSatisfying(DossierFieldValidationException.class, ex -> assertThat(ex.errors())
                        .extracting("field", "code", "reference")
                        .containsExactly(org.assertj.core.groups.Tuple.tuple("plaque", "UNIQUE", "DOS-9")));
        verify(dossierRepository, never()).save(any());

        checked.update(new UpdateDossierFieldsCommand(existing.id(), Map.of("plaque", "BE-1"), "editor", 1L), actor);
        verify(port).findHolders(TENANT, TYPE_CODE,
                Set.of(new be.myrmacta.domain.dossier.model.UniqueKey("plaque", "BE-1")), existing.id());
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
                null,
                null);
    }

    private static DossierType type(FieldDefinition... definitions) {
        return type(true, definitions);
    }

    private static DossierType type(boolean allowUnknownFields, FieldDefinition... definitions) {
        return DossierType.inherit(
                UUID.randomUUID(), TENANT, TYPE_CODE, "Sinistre", "sinistre-workflow", true, allowUnknownFields,
                List.of(definitions));
    }

    private static DossierType inactiveType(FieldDefinition... definitions) {
        return DossierType.inherit(
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
