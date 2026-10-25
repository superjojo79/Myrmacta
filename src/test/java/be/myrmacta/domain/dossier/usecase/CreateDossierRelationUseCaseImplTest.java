package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierRelation;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DuplicateDossierRelationException;
import be.myrmacta.domain.dossier.model.RelationType;
import be.myrmacta.domain.dossier.port.in.CreateDossierRelationUseCase;
import be.myrmacta.domain.dossier.port.in.CreateDossierRelationUseCase.CreateDossierRelationCommand;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class CreateDossierRelationUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private DossierRepository dossierRepository;
    private DossierRelationRepository relationRepository;
    private DossierAccessPolicy accessPolicy;
    private CreateDossierRelationUseCase useCase;
    private AccessContext.User actor;
    private Dossier source;
    private Dossier target;

    @BeforeEach
    void setUp() {
        dossierRepository = mock(DossierRepository.class);
        relationRepository = mock(DossierRelationRepository.class);
        accessPolicy = mock(DossierAccessPolicy.class);
        useCase = new CreateDossierRelationUseCaseImpl(dossierRepository, relationRepository, accessPolicy);
        actor = AccessContext.user("u1", Set.of("DOSSIER_MANAGER"));
        source = dossier("FAC", "Facture");
        target = dossier("SIN", "Sinistre");
        when(dossierRepository.findById(source.id())).thenReturn(Optional.of(source));
        when(dossierRepository.findById(target.id())).thenReturn(Optional.of(target));
        when(relationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @EnumSource(RelationType.class)
    void create_persistsWhenActorHasEditOnSourceAndViewOnTarget(RelationType type) {
        DossierRelation created = useCase.create(command(source.id(), target.id(), type, null), actor);

        assertThat(created.sourceDossierId()).isEqualTo(source.id());
        assertThat(created.targetDossierId()).isEqualTo(target.id());
        assertThat(created.type()).isEqualTo(type);
        assertThat(created.idempotencyKey()).isNull();
        assertThat(created.createdBy()).isEqualTo("u1");
        verify(accessPolicy).assertCanEdit(source, actor);
        verify(accessPolicy).assertCanView(target, actor);
        ArgumentCaptor<DossierRelation> saved = ArgumentCaptor.forClass(DossierRelation.class);
        verify(relationRepository).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(created);
    }

    @Test
    void create_rejectsWhenActorCannotEditSource() {
        doThrow(new AccessDeniedException("Access denied for permission EDIT"))
                .when(accessPolicy)
                .assertCanEdit(source, actor);

        assertThatThrownBy(() -> useCase.create(command(source.id(), target.id(), RelationType.LINKED, null), actor))
                .isInstanceOf(AccessDeniedException.class);
        verify(relationRepository, never()).save(any());
    }

    @Test
    void create_rejectsWhenActorCannotViewTarget() {
        doThrow(new AccessDeniedException("Access denied for permission VIEW"))
                .when(accessPolicy)
                .assertCanView(target, actor);

        assertThatThrownBy(() -> useCase.create(command(source.id(), target.id(), RelationType.LINKED, null), actor))
                .isInstanceOf(AccessDeniedException.class);
        verify(relationRepository, never()).save(any());
    }

    @Test
    void create_rejectsMissingTarget() {
        UUID missing = UUID.randomUUID();
        when(dossierRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(command(source.id(), missing, RelationType.LINKED, null), actor))
                .isInstanceOf(DossierNotFoundException.class);
        verify(relationRepository, never()).save(any());
    }

    @Test
    void create_rejectsTargetFromAnotherTenant() {
        Dossier foreign = dossier("tenant_other", "FAC", "Foreign");
        when(dossierRepository.findById(foreign.id())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> useCase.create(command(source.id(), foreign.id(), RelationType.LINKED, null), actor))
                .isInstanceOf(DossierNotFoundException.class);
        verify(relationRepository, never()).save(any());
    }

    @Test
    void create_rejectsMissingTypeOrIds() {
        assertThatThrownBy(() -> useCase.create(command(source.id(), target.id(), null, null), actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
        assertThatThrownBy(() -> useCase.create(
                        new CreateDossierRelationCommand(TENANT, null, target.id(), RelationType.LINKED, "u1", null),
                        actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceDossierId");
        verify(relationRepository, never()).save(any());
    }

    @Test
    void create_rejectsAutoRelation() {
        assertThatThrownBy(() -> useCase.create(command(source.id(), source.id(), RelationType.LINKED, null), actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source")
                .hasMessageContaining("target");
        verify(relationRepository, never()).save(any());
    }

    @Test
    void create_propagatesDuplicateAsDomainConflict() {
        when(relationRepository.save(any())).thenThrow(new DuplicateDossierRelationException("duplicate"));

        assertThatThrownBy(() -> useCase.create(command(source.id(), target.id(), RelationType.LINKED, null), actor))
                .isInstanceOf(DuplicateDossierRelationException.class);
    }

    @Test
    void create_storesIdempotencyKeyForServiceTask() {
        DossierRelation created = useCase.create(
                command(source.id(), target.id(), RelationType.PARENT_CHILD, "pi-1:createChild"),
                AccessContext.system("flowable-delegate"));

        assertThat(created.idempotencyKey()).isEqualTo("pi-1:createChild");
    }

    private CreateDossierRelationCommand command(
            UUID sourceId, UUID targetId, RelationType type, String idempotencyKey) {
        return new CreateDossierRelationCommand(TENANT, sourceId, targetId, type, "u1", idempotencyKey);
    }

    private static Dossier dossier(String typeCode, String label) {
        return dossier(TENANT, typeCode, label);
    }

    private static Dossier dossier(String tenantId, String typeCode, String label) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-15T08:00:00Z");
        return new Dossier(
                id,
                tenantId,
                "DOS-" + id.toString().substring(0, 8),
                typeCode,
                label,
                DossierStatus.OUVERT,
                Map.of(),
                null,
                0,
                "u1",
                now,
                "u1",
                now,
                null,
                null,
                null);
    }
}
