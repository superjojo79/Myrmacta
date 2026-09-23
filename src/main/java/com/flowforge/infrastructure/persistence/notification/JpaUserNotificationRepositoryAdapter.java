package com.flowforge.infrastructure.persistence.notification;

import com.flowforge.domain.notification.model.UserNotificationRecord;
import com.flowforge.domain.notification.port.out.UserNotificationRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.infrastructure.persistence.SpringPages;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaUserNotificationRepositoryAdapter implements UserNotificationRepository {

    private final SpringDataUserNotificationRepository repository;

    JpaUserNotificationRepositoryAdapter(SpringDataUserNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public UUID save(UUID id, String tenantId, String recipient, String payloadJson, Instant now) {
        Objects.requireNonNull(id, "id");
        repository.saveAndFlush(UserNotificationJpaEntity.create(id, tenantId, recipient, payloadJson, now));
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserNotificationRecord> page(
            String tenantId, String recipient, PageQuery query) {
        return SpringPages.map(
                repository.findByTenantIdAndRecipientOrderByCreatedAtDesc(tenantId, recipient, SpringPages.of(query)),
                UserNotificationJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(String tenantId, String recipient) {
        return repository.countByTenantIdAndRecipientAndReadAtIsNull(tenantId, recipient);
    }

    @Override
    @Transactional
    public boolean markRead(UUID id, String tenantId, String recipient, Instant now) {
        Optional<UserNotificationJpaEntity> found =
                repository.findByIdAndTenantIdAndRecipient(id, tenantId, recipient);
        if (found.isEmpty()) {
            return false;
        }
        UserNotificationJpaEntity entity = found.get();
        entity.markRead(now);
        repository.saveAndFlush(entity);
        return true;
    }

    @Override
    @Transactional
    public int markAllRead(String tenantId, String recipient, Instant now) {
        return repository.markAllRead(tenantId, recipient, now);
    }

    @Override
    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return repository.deleteByCreatedAtBefore(Objects.requireNonNull(cutoff, "cutoff"));
    }
}
