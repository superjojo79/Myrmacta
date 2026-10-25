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
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierConflictException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.IllegalDossierStatusTransitionException;
import be.myrmacta.domain.dossier.port.in.UpdateDossierStatusUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateDossierStatusUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String TYPE_CODE = "SINISTRE";

    private DossierRepository dossierRepository;
    private DossierAccessPolicy accessPolicy;
    private UpdateDossierStatusUseCase useCase;
    private AccessContext.User actor;

    @BeforeEach
    void setUp() {
        dossierRepository = mock(DossierRepository.class);
        accessPolicy = mock(DossierAccessPolicy.class);
        useCase = new UpdateDossierStatusUseCaseImpl(dossierRepository, accessPolicy);
        actor = AccessContext.user("u1", Set.of("DOSSIER_MANAGER"));
    }

    @Test
    void update_movesStatusAndPersistsWhenVersionMatches() {
        Dossier existing = dossier(DossierStatus.DRAFT, 3);
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(dossierRepository.save(any())).thenAnswer(invocation -> {
            Dossier arg = invocation.getArgument(0);
            return arg.withVersion(arg.version() + 1);
        });

        Dossier updated = useCase.update(
                new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(
                        existing.id(), DossierStatus.OUVERT, "u2", 3L),
                actor);

        assertThat(updated.status()).isEqualTo(DossierStatus.OUVERT);
        assertThat(updated.version()).isEqualTo(4);
        assertThat(updated.updatedBy()).isEqualTo("u2");
        assertThat(updated.updatedAt()).isAfterOrEqualTo(existing.updatedAt());
        assertThat(updated.closedBy()).isNull();
        assertThat(updated.closedAt()).isNull();

        verify(accessPolicy).assertCanEdit(existing, actor);
        ArgumentCaptor<Dossier> saved = ArgumentCaptor.forClass(Dossier.class);
        verify(dossierRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(DossierStatus.OUVERT);
        assertThat(saved.getValue().version()).isEqualTo(3);
    }

    @Test
    void update_setsClosureMetadataWhenClosing() {
        Dossier existing = dossier(DossierStatus.OUVERT, 4);
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(dossierRepository.save(any())).thenAnswer(invocation -> {
            Dossier arg = invocation.getArgument(0);
            return arg.withVersion(arg.version() + 1);
        });

        Dossier updated = useCase.update(
                new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(
                        existing.id(), DossierStatus.CLOTURE, "closer", null),
                actor);

        assertThat(updated.status()).isEqualTo(DossierStatus.CLOTURE);
        assertThat(updated.version()).isEqualTo(5);
        assertThat(updated.closedBy()).isEqualTo("closer");
        assertThat(updated.closedAt()).isNotNull();
        assertThat(updated.closedAt()).isEqualTo(updated.updatedAt());
        verify(dossierRepository).save(any());
    }

    @Test
    void update_throwsConflictWhenExpectedVersionDiffers() {
        Dossier existing = dossier(DossierStatus.DRAFT, 3);
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(
                                existing.id(), DossierStatus.OUVERT, "u2", 2L),
                        actor))
                .isInstanceOf(DossierConflictException.class);

        verify(accessPolicy).assertCanEdit(existing, actor);
        verify(dossierRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void update_rejectsIllegalTransitionWithoutSaving() {
        Dossier existing = dossier(DossierStatus.DRAFT, 3);
        when(dossierRepository.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.update(
                        new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(
                                existing.id(), DossierStatus.CLOTURE, "u2", 3L),
                        actor))
                .isInstanceOf(IllegalDossierStatusTransitionException.class);

        verify(dossierRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static Dossier dossier(DossierStatus status, long version) {
        Instant createdAt = Instant.parse("2026-07-31T12:00:00Z");
        return new Dossier(
                UUID.randomUUID(),
                TENANT,
                "DOS-SINISTRE-2026-000001",
                TYPE_CODE,
                "Incident A",
                status,
                Map.of("priority", "NORMAL"),
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
}
