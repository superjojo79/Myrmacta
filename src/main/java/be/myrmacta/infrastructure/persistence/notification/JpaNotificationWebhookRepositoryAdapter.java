package be.myrmacta.infrastructure.persistence.notification;

import be.myrmacta.domain.connector.port.out.ConnectorSecretCipher;
import be.myrmacta.domain.notification.model.NotificationWebhookConfig;
import be.myrmacta.domain.notification.port.out.NotificationWebhookRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.persistence.SpringPages;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaNotificationWebhookRepositoryAdapter implements NotificationWebhookRepository {

    private static final String ENC_PREFIX = "enc:v1:";

    private final SpringDataNotificationWebhookRepository repository;
    private final ConnectorSecretCipher cipher;

    JpaNotificationWebhookRepositoryAdapter(
            SpringDataNotificationWebhookRepository repository, ConnectorSecretCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Override
    @Transactional
    public NotificationWebhookConfig save(NotificationWebhookConfig config) {
        String secretAtRest = encryptSecretIfNeeded(config.tenantId(), config.secretCiphertext());
        Optional<NotificationWebhookJpaEntity> existing =
                repository.findByIdAndTenantId(config.id(), config.tenantId());
        NotificationWebhookJpaEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.applyDomain(config, secretAtRest);
        } else {
            entity = NotificationWebhookJpaEntity.fromDomain(config, secretAtRest);
        }
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationWebhookConfig> findByIdAndTenantId(UUID id, String tenantId) {
        return repository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId).map(NotificationWebhookJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationWebhookConfig> findByIdAndTenantIdIncludingDeleted(UUID id, String tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).map(NotificationWebhookJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationWebhookConfig> listByTenantId(String tenantId) {
        return repository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId).stream()
                .map(NotificationWebhookJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<NotificationWebhookConfig> pageByTenantId(String tenantId, PageQuery query) {
        return SpringPages.map(
                repository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId, SpringPages.of(query)),
                NotificationWebhookJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void softDelete(UUID id, String tenantId, Instant now) {
        repository
                .findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .ifPresent(entity -> {
                    entity.markDeleted(now);
                    repository.saveAndFlush(entity);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveByName(String tenantId, String name, UUID excludeIdOrNull) {
        return repository.existsActiveByName(tenantId, name, excludeIdOrNull);
    }

    private String encryptSecretIfNeeded(String tenantId, String secret) {
        if (secret == null) {
            return null;
        }
        if (secret.startsWith(ENC_PREFIX)) {
            return secret;
        }
        return cipher.encrypt(tenantId, secret);
    }
}
