package be.myrmacta.domain.notification.usecase;

import be.myrmacta.domain.notification.model.UserNotificationNotFoundException;
import be.myrmacta.domain.notification.model.UserNotificationRecord;
import be.myrmacta.domain.notification.port.in.NotificationInboxUseCase;
import be.myrmacta.domain.notification.port.out.UserNotificationRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class NotificationInboxUseCaseImpl implements NotificationInboxUseCase {

    private final UserNotificationRepository repository;

    public NotificationInboxUseCaseImpl(UserNotificationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public PageResult<UserNotificationRecord> list(String tenantId, String recipient, PageQuery query) {
        return repository.page(
                Objects.requireNonNull(tenantId, "tenantId"),
                Objects.requireNonNull(recipient, "recipient"),
                Objects.requireNonNull(query, "query"));
    }

    @Override
    public long unreadCount(String tenantId, String recipient) {
        return repository.countUnread(
                Objects.requireNonNull(tenantId, "tenantId"), Objects.requireNonNull(recipient, "recipient"));
    }

    @Override
    public void markRead(UUID id, String tenantId, String recipient) {
        Objects.requireNonNull(id, "id");
        boolean updated = repository.markRead(
                id, Objects.requireNonNull(tenantId, "tenantId"), Objects.requireNonNull(recipient, "recipient"), Instant.now());
        if (!updated) {
            throw new UserNotificationNotFoundException(id);
        }
    }

    @Override
    public void markAllRead(String tenantId, String recipient) {
        repository.markAllRead(
                Objects.requireNonNull(tenantId, "tenantId"), Objects.requireNonNull(recipient, "recipient"), Instant.now());
    }
}
