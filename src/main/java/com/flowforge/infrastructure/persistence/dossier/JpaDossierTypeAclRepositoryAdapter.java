package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaDossierTypeAclRepositoryAdapter implements DossierTypeAclRepository {

    private final SpringDataDossierTypeAclRepository repository;

    public JpaDossierTypeAclRepositoryAdapter(SpringDataDossierTypeAclRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DossierTypeAclEntry> find(String tenantId, String dossierTypeCode, String subjectUserId) {
        return repository
                .findByTenantIdAndDossierTypeCodeAndSubjectUserId(tenantId, dossierTypeCode, subjectUserId)
                .map(DossierTypeAclJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(DossierTypeAclEntry entry) {
        var existing = repository.findByTenantIdAndDossierTypeCodeAndSubjectUserId(
                entry.tenantId(), entry.dossierTypeCode(), entry.subjectUserId());
        if (existing.isPresent()) {
            existing.get().updatePermission(entry.permission());
            return;
        }
        repository.save(DossierTypeAclJpaEntity.fromDomain(entry));
    }

    @Override
    @Transactional
    public void delete(String tenantId, String dossierTypeCode, String subjectUserId) {
        repository.deleteByTenantIdAndDossierTypeCodeAndSubjectUserId(tenantId, dossierTypeCode, subjectUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DossierTypeAclEntry> findBySubject(String tenantId, String subjectUserId) {
        return repository.findAllByTenantIdAndSubjectUserIdOrderByDossierTypeCode(tenantId, subjectUserId).stream()
                .map(DossierTypeAclJpaEntity::toDomain)
                .toList();
    }
}
