package com.flowforge.infrastructure.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierRelation;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DuplicateDossierRelationException;
import com.flowforge.domain.dossier.model.RelationType;
import com.flowforge.domain.dossier.port.in.CreateDossierRelationUseCase;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.out.DossierRelationRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChildDossierRelationConfirmerTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant T0 = Instant.parse("2026-08-15T08:00:00Z");

    private DossierRelationRepository relationRepository;
    private CreateDossierUseCase createDossierUseCase;
    private CreateDossierRelationUseCase createDossierRelationUseCase;
    private ChildDossierRelationConfirmer confirmer;
    private UUID sourceId;
    private Dossier child;

    @BeforeEach
    void setUp() {
        relationRepository = mock(DossierRelationRepository.class);
        createDossierUseCase = mock(CreateDossierUseCase.class);
        createDossierRelationUseCase = mock(CreateDossierRelationUseCase.class);
        confirmer = new ChildDossierRelationConfirmer(
                relationRepository, createDossierUseCase, createDossierRelationUseCase);
        sourceId = UUID.randomUUID();
        child = dossier();
        when(createDossierUseCase.create(any(), any())).thenReturn(child);
    }

    @Test
    void firstCall_createsDossierAndRelation() {
        when(relationRepository.findByIdempotencyKey(TENANT, sourceId, "pi:act")).thenReturn(Optional.empty());

        UUID result = confirmer.confirm(
                TENANT, sourceId, "CHILD", "Enfant", Map.of(), RelationType.PARENT_CHILD, "pi:act");

        assertThat(result).isEqualTo(child.id());
        verify(createDossierUseCase).create(any(), any());
        verify(createDossierRelationUseCase).create(any(), any());
    }

    @Test
    void replay_returnsExistingChildWithoutWrites() {
        DossierRelation existing = relation(child.id(), "pi:act");
        when(relationRepository.findByIdempotencyKey(TENANT, sourceId, "pi:act")).thenReturn(Optional.of(existing));

        UUID result = confirmer.confirm(
                TENANT, sourceId, "CHILD", "Enfant", Map.of(), RelationType.PARENT_CHILD, "pi:act");

        assertThat(result).isEqualTo(child.id());
        verify(createDossierUseCase, never()).create(any(), any());
        verify(createDossierRelationUseCase, never()).create(any(), any());
    }

    @Test
    void race_propagatesDuplicateWithoutRecoveryAttemptInTheSameTransaction() {
        // No internal catch-and-recover: once the nested @Transactional save() lets
        // DuplicateDossierRelationException escape, this transaction is already marked
        // rollback-only by Spring (global-rollback-on-participation-failure) — any further
        // read attempted here would hit an already-doomed Hibernate session instead of
        // recovering cleanly. The caller (CreateChildDossierDelegate) is responsible for
        // retrying with a brand new confirm() call / fresh transaction.
        when(relationRepository.findByIdempotencyKey(TENANT, sourceId, "pi:act")).thenReturn(Optional.empty());
        DuplicateDossierRelationException race = new DuplicateDossierRelationException("race");
        when(createDossierRelationUseCase.create(any(), any())).thenThrow(race);

        assertThatThrownBy(() -> confirmer.confirm(
                        TENANT, sourceId, "CHILD", "Enfant", Map.of(), RelationType.PARENT_CHILD, "pi:act"))
                .isSameAs(race);
        verify(relationRepository, times(1)).findByIdempotencyKey(TENANT, sourceId, "pi:act");
    }

    private DossierRelation relation(UUID targetId, String key) {
        return new DossierRelation(
                UUID.randomUUID(),
                sourceId,
                targetId,
                TENANT,
                RelationType.PARENT_CHILD,
                "workflow",
                T0,
                key);
    }

    private static Dossier dossier() {
        UUID id = UUID.randomUUID();
        return new Dossier(
                id,
                TENANT,
                "DOS-CHILD",
                "CHILD",
                "Enfant",
                DossierStatus.DRAFT,
                Map.of(),
                null,
                0,
                "workflow",
                T0,
                "workflow",
                T0,
                null,
                null);
    }
}
