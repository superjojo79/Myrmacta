package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetDossierUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String TYPE_CODE = "SINISTRE";

    private DossierRepository dossierRepository;
    private DossierTypeRepository typeRepository;
    private DossierAccessPolicy accessPolicy;
    private GetDossierUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        dossierRepository = mock(DossierRepository.class);
        typeRepository = mock(DossierTypeRepository.class);
        accessPolicy = mock(DossierAccessPolicy.class);
        useCase = new GetDossierUseCaseImpl(dossierRepository, typeRepository, accessPolicy);
    }

    @Test
    void get_redactsSensitiveFieldsForNonAdminViewer() {
        Dossier dossier = dossier(Map.of("priority", "HIGH", "ssn", "123-45-6789"));
        when(dossierRepository.findById(dossier.id())).thenReturn(Optional.of(dossier));
        when(typeRepository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(typeWithSensitiveSsn()));
        AccessContext.User viewer = AccessContext.user("u1", Set.of("DOSSIER_MANAGER"));

        Dossier result = useCase.get(dossier.id(), viewer);

        assertThat(result.fields()).containsEntry("priority", "HIGH");
        assertThat(result.fields()).doesNotContainKey("ssn");
    }

    @Test
    void get_keepsClearValuesForTenantAdmin() {
        Dossier dossier = dossier(Map.of("ssn", "123-45-6789"));
        when(dossierRepository.findById(dossier.id())).thenReturn(Optional.of(dossier));
        when(typeRepository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(typeWithSensitiveSsn()));
        AccessContext.User admin = AccessContext.user("admin", Set.of("TENANT_ADMIN"));

        Dossier result = useCase.get(dossier.id(), admin);

        assertThat(result.fields()).containsEntry("ssn", "123-45-6789");
    }

    @Test
    void get_keepsClearValuesForSystemContext() {
        Dossier dossier = dossier(Map.of("ssn", "123-45-6789"));
        when(dossierRepository.findById(dossier.id())).thenReturn(Optional.of(dossier));
        when(typeRepository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(typeWithSensitiveSsn()));

        Dossier result = useCase.get(dossier.id(), AccessContext.system("connector-trigger"));

        assertThat(result.fields()).containsEntry("ssn", "123-45-6789");
    }

    @Test
    void get_throwsWhenAccessPolicyDenies() {
        Dossier dossier = dossier(Map.of());
        when(dossierRepository.findById(dossier.id())).thenReturn(Optional.of(dossier));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(accessPolicy)
                .assertCanView(dossier, AccessContext.user("u1", Set.of()));

        assertThatThrownBy(() -> useCase.get(dossier.id(), AccessContext.user("u1", Set.of())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void get_throwsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(dossierRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(id, AccessContext.user("u1", Set.of())))
                .isInstanceOf(DossierNotFoundException.class);
    }

    @Test
    void getByReference_redactsSensitiveFieldsForNonAdminViewer() {
        Dossier dossier = dossier(Map.of("ssn", "123-45-6789"));
        when(dossierRepository.findByReference(TENANT, dossier.reference())).thenReturn(Optional.of(dossier));
        when(typeRepository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.of(typeWithSensitiveSsn()));

        Dossier result = useCase.getByReference(
                TENANT, dossier.reference(), AccessContext.user("u1", Set.of("DOSSIER_MANAGER")));

        assertThat(result.fields()).doesNotContainKey("ssn");
    }

    @Test
    void get_toleratesMissingDossierTypeByLeavingFieldsUnredacted() {
        Dossier dossier = dossier(Map.of("ssn", "123-45-6789"));
        when(dossierRepository.findById(dossier.id())).thenReturn(Optional.of(dossier));
        when(typeRepository.findByCode(TENANT, TYPE_CODE)).thenReturn(Optional.empty());

        Dossier result = useCase.get(dossier.id(), AccessContext.user("u1", Set.of("DOSSIER_MANAGER")));

        assertThat(result.fields()).containsEntry("ssn", "123-45-6789");
    }

    private static DossierType typeWithSensitiveSsn() {
        return DossierType.inherit(
                UUID.randomUUID(),
                TENANT,
                TYPE_CODE,
                "Sinistre",
                "wf-sinistre",
                true,
                false,
                List.of(
                        new FieldDefinition("priority", "Priority", FieldType.STRING, false, null, List.of(), null, false),
                        new FieldDefinition("ssn", "SSN", FieldType.STRING, false, null, List.of(), null, true)));
    }

    private static Dossier dossier(Map<String, Object> fields) {
        return new Dossier(
                UUID.randomUUID(),
                TENANT,
                "DOS-SINISTRE-2026-000001",
                TYPE_CODE,
                "Incident A",
                DossierStatus.OUVERT,
                fields,
                null,
                0,
                "u1",
                Instant.parse("2026-08-02T09:00:00Z"),
                "u1",
                Instant.parse("2026-08-02T09:00:00Z"),
                null,
                null,
                null);
    }
}
