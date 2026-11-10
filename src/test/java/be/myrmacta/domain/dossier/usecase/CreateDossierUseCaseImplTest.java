package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierFieldValidationException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.model.InactiveDossierTypeException;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.out.DossierReferenceGenerator;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateDossierUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String TYPE_CODE = "SINISTRE";

    private DossierTypeRepository typeRepository;
    private DossierRepository dossierRepository;
    private DossierReferenceGenerator referenceGenerator;
    private DossierAccessPolicy accessPolicy;
    private CreateDossierUseCase useCase;
    private AccessContext.User actor;

    @BeforeEach
    void setUp() {
        typeRepository = mock(DossierTypeRepository.class);
        dossierRepository = mock(DossierRepository.class);
        referenceGenerator = mock(DossierReferenceGenerator.class);
        accessPolicy = mock(DossierAccessPolicy.class);
        useCase = new CreateDossierUseCaseImpl(
                typeRepository, dossierRepository, referenceGenerator, accessPolicy);
        actor = AccessContext.user("u1", Set.of("DOSSIER_MANAGER"));
    }

    @Test
    void create_appliesDefaultsAndPersistsDraftWithGeneratedReference() {
        var type = type(true, false, List.of(field("priority", FieldType.STRING, false, "NORMAL")));
        when(typeRepository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(type));
        when(referenceGenerator.generate(TENANT, TYPE_CODE)).thenReturn("DOS-SINISTRE-2026-000001");
        when(dossierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Dossier dossier = useCase.create(
                new CreateDossierUseCase.CreateDossierCommand(
                        TENANT, TYPE_CODE, "Incident A", Map.of(), "u1"),
                actor);

        assertThat(dossier.reference()).isEqualTo("DOS-SINISTRE-2026-000001");
        assertThat(dossier.status()).isEqualTo(DossierStatus.DRAFT);
        assertThat(dossier.version()).isZero();
        assertThat(dossier.currentWorkflowLinkId()).isNull();
        assertThat(dossier.fields()).containsEntry("priority", "NORMAL");
        assertThat(dossier.createdBy()).isEqualTo("u1");
        assertThat(dossier.updatedBy()).isEqualTo("u1");

        verify(accessPolicy).assertCanEditType(TENANT, TYPE_CODE, actor);
        ArgumentCaptor<Dossier> saved = ArgumentCaptor.forClass(Dossier.class);
        verify(dossierRepository).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(dossier);
    }

    @Test
    void create_rejectsInactiveDossierType() {
        when(typeRepository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(type(false, false, List.of())));

        assertThatThrownBy(() -> useCase.create(
                        new CreateDossierUseCase.CreateDossierCommand(
                                TENANT, TYPE_CODE, "Incident A", Map.of(), "u1"),
                        actor))
                .isInstanceOf(InactiveDossierTypeException.class);

        verify(referenceGenerator, never()).generate(TENANT, TYPE_CODE);
        verify(dossierRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void create_throwsValidationExceptionWithoutSavingWhenFieldsAreInvalid() {
        when(typeRepository.findByCode(TENANT, TYPE_CODE))
                .thenReturn(Optional.of(type(true, false, List.of(field("amount", FieldType.NUMBER, true, null)))));

        assertThatExceptionOfType(DossierFieldValidationException.class)
                .isThrownBy(() -> useCase.create(
                        new CreateDossierUseCase.CreateDossierCommand(
                                TENANT, TYPE_CODE, "Incident A", Map.of("amount", "bad"), "u1"),
                        actor))
                .satisfies(exception -> assertThat(exception.errors())
                        .extracting("code")
                        .containsExactly("TYPE_MISMATCH"));

        verify(referenceGenerator, never()).generate(TENANT, TYPE_CODE);
        verify(dossierRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /** TODO §102 (U8) — doublon refusé avant écriture, avec la référence du détenteur et les autres erreurs. */
    @Test
    void create_refusesAValueAlreadyHeldByAnOpenDossier_withTheOtherFieldErrors() {
        var plaque = new FieldDefinition("plaque", "Plaque", FieldType.STRING, false, null, null, null, false, null, null,
                null, null, null, null, false, null, null, true);
        var type = type(true, false, List.of(plaque, field("amount", FieldType.NUMBER, false, null)));
        when(typeRepository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(type));
        var port = mock(be.myrmacta.domain.dossier.port.out.DossierUniquenessPort.class);
        when(port.findHolders(TENANT, TYPE_CODE,
                Set.of(new be.myrmacta.domain.dossier.model.UniqueKey("plaque", "BE-1")), null))
                .thenReturn(Map.of(new be.myrmacta.domain.dossier.model.UniqueKey("plaque", "BE-1"), "DOS-1"));
        var checked = new CreateDossierUseCaseImpl(typeRepository, dossierRepository, referenceGenerator, accessPolicy,
                new be.myrmacta.domain.dossier.model.DossierFieldValidator(),
                be.myrmacta.domain.dossier.DossierFieldRules.disabled(),
                new be.myrmacta.domain.dossier.DossierUniquenessCheck(port));

        assertThatExceptionOfType(DossierFieldValidationException.class)
                .isThrownBy(() -> checked.create(new CreateDossierUseCase.CreateDossierCommand(
                        TENANT, TYPE_CODE, "A", Map.of("plaque", "BE-1", "amount", "bad"), "u1"), actor))
                .satisfies(exception -> assertThat(exception.errors())
                        .extracting("field", "code", "reference")
                        .containsExactlyInAnyOrder(
                                org.assertj.core.groups.Tuple.tuple("amount", "TYPE_MISMATCH", null),
                                org.assertj.core.groups.Tuple.tuple("plaque", "UNIQUE", "DOS-1")));
        verify(referenceGenerator, never()).generate(TENANT, TYPE_CODE);
        verify(dossierRepository, never()).save(any());
    }

    @Test
    void create_rejectsAManagedFieldInThePayload() {
        var managed = new FieldDefinition(
                "identifiantIam", "IAM", FieldType.STRING, false, null, null, null, false, null, null, null, null, null,
                null, false, null, null, false, true);
        when(typeRepository.findByCode(TENANT, TYPE_CODE))
                .thenReturn(Optional.of(type(true, false, List.of(managed, field("nom", FieldType.STRING, false, null)))));

        assertThatExceptionOfType(DossierFieldValidationException.class)
                .isThrownBy(() -> useCase.create(
                        new CreateDossierUseCase.CreateDossierCommand(
                                TENANT, TYPE_CODE, "A", Map.of("identifiantIam", "iam-forged", "nom", "Dupont"), "u1"),
                        actor))
                .satisfies(exception -> assertThat(exception.errors())
                        .anySatisfy(err -> assertThat(err.code()).isEqualTo("MANAGED_BY_WORKFLOW")));
        verify(dossierRepository, never()).save(any());
    }

    private static DossierType type(boolean active, boolean allowUnknownFields, List<FieldDefinition> fields) {
        return DossierType.inherit(UUID.randomUUID(), TENANT, TYPE_CODE, "Sinistre", "wf-sinistre",
                active, allowUnknownFields, fields);
    }

    private static FieldDefinition field(String name, FieldType type, boolean required, String defaultValue) {
        return new FieldDefinition(name, name, type, required, defaultValue, List.of(), null);
    }
}
