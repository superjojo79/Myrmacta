package be.myrmacta.infrastructure.persistence.notification.template;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.persistence.SpringPages;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaNotificationTemplateRepositoryAdapter implements NotificationTemplateRepository {

    private final SpringDataNotificationTemplateRepository repository;

    JpaNotificationTemplateRepositoryAdapter(SpringDataNotificationTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public NotificationTemplate save(NotificationTemplate template) {
        NotificationTemplateJpaEntity entity = repository
                .findByIdAndTenantId(template.id(), template.tenantId())
                .map(existing -> {
                    existing.applyDomain(template);
                    return existing;
                })
                .orElseGet(() -> NotificationTemplateJpaEntity.fromDomain(template));
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationTemplate> findByIdAndTenantId(UUID id, String tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).map(NotificationTemplateJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<NotificationTemplate> pageByTenantId(String tenantId, String templateKey, PageQuery query) {
        var pageable = SpringPages.of(query);
        var page = templateKey == null || templateKey.isBlank()
                ? repository.findByTenantIdOrderByUpdatedAtDesc(tenantId, pageable)
                : repository.findByTenantIdAndTemplateKeyOrderByUpdatedAtDesc(tenantId, templateKey, pageable);
        return SpringPages.map(page, NotificationTemplateJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public int nextVersion(String tenantId, String templateKey, String variantKey, String locale) {
        return repository.maxVersion(tenantId, templateKey, variantKey, locale) + 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationTemplate> findBestActive(
            String tenantId, String templateKey, String variantKey, String locale) {
        return repository
                .findBestActive(tenantId, templateKey, variantKey, locale)
                .map(NotificationTemplateJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationTemplate> findActiveExact(
            String tenantId, String templateKey, String variantKey, String locale) {
        return repository
                .findActiveExact(tenantId, templateKey, variantKey, locale, TemplateStatus.ACTIVE)
                .map(NotificationTemplateJpaEntity::toDomain);
    }
}
