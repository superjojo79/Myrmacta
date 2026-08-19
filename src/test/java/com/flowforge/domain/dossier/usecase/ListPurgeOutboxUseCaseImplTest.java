package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.PurgeOutboxOrigin;
import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.model.PurgeOutboxStatus;
import com.flowforge.domain.dossier.port.in.ListPurgeOutboxUseCase;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListPurgeOutboxUseCaseImplTest {

    private PurgeOutboxRepository repository;
    private ListPurgeOutboxUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(PurgeOutboxRepository.class);
        useCase = new ListPurgeOutboxUseCaseImpl(repository, new TenantAdminGuard());
    }

    @Test
    void list_adminReturnsItemsAndCounts() {
        PurgeOutboxRecord item = PurgeOutboxRecord.pending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DOSSIER_PURGE_REQUESTED",
                PurgeOutboxOrigin.ADMIN,
                "{}",
                Instant.parse("2026-08-19T12:00:00Z"));
        when(repository.list(null, 50)).thenReturn(List.of(item));
        when(repository.countByStatus(PurgeOutboxStatus.PENDING)).thenReturn(1L);
        when(repository.countByStatus(PurgeOutboxStatus.PROCESSING)).thenReturn(0L);
        when(repository.countByStatus(PurgeOutboxStatus.FAILED)).thenReturn(2L);
        when(repository.countByStatus(PurgeOutboxStatus.COMPLETED)).thenReturn(3L);

        ListPurgeOutboxUseCase.Page page =
                useCase.list(AccessContext.user("admin", Set.of("TENANT_ADMIN")), null, 0);

        assertThat(page.items()).containsExactly(item);
        assertThat(page.counts().pending()).isEqualTo(1);
        assertThat(page.counts().failed()).isEqualTo(2);
        assertThat(page.counts().completed()).isEqualTo(3);
    }

    @Test
    void list_nonAdminDenied() {
        assertThatThrownBy(
                        () -> useCase.list(AccessContext.user("mgr", Set.of("DOSSIER_MANAGER")), null, 50))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }
}
