package be.myrmacta.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.notification.model.UserNotificationNotFoundException;
import be.myrmacta.domain.notification.port.in.NotificationInboxUseCase;
import be.myrmacta.domain.notification.port.out.UserNotificationRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationInboxUseCaseImplTest {

    private UserNotificationRepository repository;
    private NotificationInboxUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(UserNotificationRepository.class);
        useCase = new NotificationInboxUseCaseImpl(repository);
    }

    @Test
    void list_delegatesToRepository() {
        PageQuery query = PageQuery.defaults();
        when(repository.page("tenant_dev", "user-1", query)).thenReturn(PageResult.empty(0, 20));

        PageResult<?> result = useCase.list("tenant_dev", "user-1", query);

        assertThat(result.content()).isEmpty();
        verify(repository).page("tenant_dev", "user-1", query);
    }

    @Test
    void markRead_repositoryReturnsTrue_succeeds() {
        UUID id = UUID.randomUUID();
        when(repository.markRead(eq(id), eq("tenant_dev"), eq("user-1"), any())).thenReturn(true);

        useCase.markRead(id, "tenant_dev", "user-1");

        verify(repository).markRead(eq(id), eq("tenant_dev"), eq("user-1"), any());
    }

    @Test
    void markRead_repositoryReturnsFalse_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.markRead(eq(id), eq("tenant_dev"), eq("user-1"), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.markRead(id, "tenant_dev", "user-1"))
                .isInstanceOf(UserNotificationNotFoundException.class);
    }

    @Test
    void markAllRead_delegatesToRepository() {
        useCase.markAllRead("tenant_dev", "user-1");

        verify(repository).markAllRead(eq("tenant_dev"), eq("user-1"), any());
    }

    @Test
    void unreadCount_delegatesToRepository() {
        // TODO §66 F3 — server-truth badge count.
        when(repository.countUnread("tenant_dev", "user-1")).thenReturn(3L);

        long count = useCase.unreadCount("tenant_dev", "user-1");

        assertThat(count).isEqualTo(3L);
    }
}
