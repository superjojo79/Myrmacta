package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotEligibleForPurgeException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.PurgeOutboxOrigin;
import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.port.in.EnqueueDossierPurgeUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EnqueueDossierPurgeUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private static final String TENANT = "tenant_dev";

    private DossierRepository dossiers;
    private PurgeOutboxRepository outbox;
    private EnqueueDossierPurgeUseCase useCase;

    @BeforeEach
    void setUp() {
        dossiers = mock(DossierRepository.class);
        outbox = mock(PurgeOutboxRepository.class);
        useCase = new EnqueueDossierPurgeUseCaseImpl(dossiers, outbox, new TenantAdminGuard());
    }

    @Test
    void enqueue_cloture_insertsAdminPending() {
        UUID id = UUID.randomUUID();
        when(dossiers.findById(id)).thenReturn(Optional.of(dossier(id, DossierStatus.CLOTURE)));
        when(outbox.existsByDossierId(id)).thenReturn(false);

        EnqueueDossierPurgeUseCase.Result result =
                useCase.enqueue(id, AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(result).isEqualTo(EnqueueDossierPurgeUseCase.Result.CREATED);
        ArgumentCaptor<PurgeOutboxRecord> captor = ArgumentCaptor.forClass(PurgeOutboxRecord.class);
        verify(outbox).insert(captor.capture());
        assertThat(captor.getValue().origin()).isEqualTo(PurgeOutboxOrigin.ADMIN);
        assertThat(captor.getValue().dossierId()).isEqualTo(id);
        assertThat(captor.getValue().payloadJson()).contains(TENANT);
    }

    @Test
    void enqueue_alreadyPresent_isIdempotent() {
        UUID id = UUID.randomUUID();
        when(dossiers.findById(id)).thenReturn(Optional.of(dossier(id, DossierStatus.ARCHIVE)));
        when(outbox.existsByDossierId(id)).thenReturn(true);

        EnqueueDossierPurgeUseCase.Result result =
                useCase.enqueue(id, AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(result).isEqualTo(EnqueueDossierPurgeUseCase.Result.ALREADY_EXISTS);
        verify(outbox, never()).insert(any());
    }

    @Test
    void enqueue_openDossier_throwsConflict() {
        UUID id = UUID.randomUUID();
        when(dossiers.findById(id)).thenReturn(Optional.of(dossier(id, DossierStatus.OUVERT)));

        assertThatThrownBy(() -> useCase.enqueue(id, AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(DossierNotEligibleForPurgeException.class);
        verify(outbox, never()).insert(any());
    }

    @Test
    void enqueue_unknownDossier_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(dossiers.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.enqueue(id, AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(DossierNotFoundException.class);
    }

    @Test
    void enqueue_nonAdmin_denied() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> useCase.enqueue(id, AccessContext.user("mgr", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(com.flowforge.domain.dossier.model.AccessDeniedException.class);
        verify(dossiers, never()).findById(any());
    }

    private static Dossier dossier(UUID id, DossierStatus status) {
        return new Dossier(
                id,
                TENANT,
                "DOS-1",
                "CLAIM",
                "Dossier",
                status,
                Map.of(),
                null,
                0,
                "creator",
                NOW,
                "user",
                NOW,
                status.blocksMutation() ? "user" : null,
                status.blocksMutation() ? NOW : null,
                status == DossierStatus.ARCHIVE ? NOW : null);
    }
}
