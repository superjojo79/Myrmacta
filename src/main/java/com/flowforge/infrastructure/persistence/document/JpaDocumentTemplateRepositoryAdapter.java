package com.flowforge.infrastructure.persistence.document;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.infrastructure.persistence.SpringPages;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaDocumentTemplateRepositoryAdapter implements DocumentTemplateRepository {

    private final SpringDataDocumentTemplateRepository repository;

    JpaDocumentTemplateRepositoryAdapter(SpringDataDocumentTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public DocumentTemplate save(DocumentTemplate template) {
        Optional<DocumentTemplateJpaEntity> existing =
                repository.findByIdAndTenantId(template.id(), template.tenantId());
        DocumentTemplateJpaEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.applyDomain(template);
        } else {
            entity = DocumentTemplateJpaEntity.fromDomain(template);
        }
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentTemplate> findByIdAndTenantId(UUID id, String tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).map(DocumentTemplateJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTemplate> listByTenantId(String tenantId, String templateKey) {
        List<DocumentTemplateJpaEntity> entities = templateKey == null || templateKey.isBlank()
                ? repository.findByTenantIdOrderByTemplateKeyAscVersionDesc(tenantId)
                : repository.findByTenantIdAndTemplateKeyOrderByVersionDesc(tenantId, templateKey);
        return entities.stream().map(DocumentTemplateJpaEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<DocumentTemplate> pageByTenantId(String tenantId, String templateKey, PageQuery query) {
        var pageable = SpringPages.of(query);
        var page = templateKey == null || templateKey.isBlank()
                ? repository.findByTenantIdOrderByUpdatedAtDesc(tenantId, pageable)
                : repository.findByTenantIdAndTemplateKeyOrderByUpdatedAtDesc(tenantId, templateKey, pageable);
        return SpringPages.map(page, DocumentTemplateJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public int nextVersion(String tenantId, String templateKey, String variantKey, String locale) {
        return repository.maxVersion(tenantId, templateKey, variantKey, locale) + 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentTemplate> findBestActive(
            String tenantId, String templateKey, String variantKey, String locale) {
        return repository
                .findBestActive(tenantId, templateKey, variantKey, locale)
                .map(DocumentTemplateJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentTemplate> findActiveExact(
            String tenantId, String templateKey, String variantKey, String locale) {
        return repository
                .findActiveExact(tenantId, templateKey, variantKey, locale, TemplateStatus.ACTIVE)
                .map(DocumentTemplateJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTemplate> findActiveByTemplateKeysIn(String tenantId, List<String> templateKeys) {
        return repository
                .findByTenantIdAndTemplateKeyInAndStatus(tenantId, templateKeys, TemplateStatus.ACTIVE)
                .stream()
                .map(DocumentTemplateJpaEntity::toDomain)
                .toList();
    }
}
