package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.PurgeOutboxOrigin;
import be.myrmacta.domain.dossier.model.PurgeOutboxRecord;
import be.myrmacta.domain.dossier.model.PurgeOutboxStatus;
import be.myrmacta.domain.dossier.port.in.ListPurgeOutboxUseCase;
import be.myrmacta.domain.dossier.port.out.PurgeOutboxRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
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
        when(repository.page(null, PageQuery.defaults())).thenReturn(PageResult.of(List.of(item), 0, 20, 1));
        when(repository.countByStatus(PurgeOutboxStatus.PENDING)).thenReturn(1L);
        when(repository.countByStatus(PurgeOutboxStatus.PROCESSING)).thenReturn(0L);
        when(repository.countByStatus(PurgeOutboxStatus.FAILED)).thenReturn(2L);
        when(repository.countByStatus(PurgeOutboxStatus.COMPLETED)).thenReturn(3L);

        ListPurgeOutboxUseCase.Page page =
                useCase.list(AccessContext.user("admin", Set.of("TENANT_ADMIN")), null, PageQuery.defaults());

        assertThat(page.result().content()).containsExactly(item);
        assertThat(page.counts().pending()).isEqualTo(1);
        assertThat(page.counts().processing()).isEqualTo(0);
        assertThat(page.counts().failed()).isEqualTo(2);
        assertThat(page.counts().completed()).isEqualTo(3);
    }

    @Test
    void list_countsIgnoreStatusFilter() {
        PageQuery query = PageQuery.of(0, 20);
        when(repository.page(PurgeOutboxStatus.FAILED, query)).thenReturn(PageResult.empty(0, 20));
        when(repository.countByStatus(PurgeOutboxStatus.PENDING)).thenReturn(4L);
        when(repository.countByStatus(PurgeOutboxStatus.PROCESSING)).thenReturn(1L);
        when(repository.countByStatus(PurgeOutboxStatus.FAILED)).thenReturn(2L);
        when(repository.countByStatus(PurgeOutboxStatus.COMPLETED)).thenReturn(8L);

        ListPurgeOutboxUseCase.Page page = useCase.list(
                AccessContext.user("admin", Set.of("TENANT_ADMIN")), PurgeOutboxStatus.FAILED, query);

        assertThat(page.counts().pending()).isEqualTo(4);
        assertThat(page.counts().failed()).isEqualTo(2);
        verify(repository).page(PurgeOutboxStatus.FAILED, query);
    }

    @Test
    void list_nonAdminDenied() {
        assertThatThrownBy(
                        () -> useCase.list(
                                AccessContext.user("mgr", Set.of("DOSSIER_MANAGER")), null, PageQuery.defaults()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }
}
