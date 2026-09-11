package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.model.DmnDraft;
import com.flowforge.domain.workflow.port.out.DmnDraftRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaDmnDraftRepositoryAdapter implements DmnDraftRepository {

    private final SpringDataDmnDraftRepository repository;

    JpaDmnDraftRepositoryAdapter(SpringDataDmnDraftRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DmnDraft> findByTenantIdAndDecisionKey(String tenantId, String decisionKey) {
        return repository.findByTenantIdAndDecisionKey(tenantId, decisionKey).map(DmnDraftJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public DmnDraft save(DmnDraft draft) {
        return repository.saveAndFlush(DmnDraftJpaEntity.fromDomain(draft)).toDomain();
    }

    @Override
    @Transactional
    public void deleteByTenantIdAndDecisionKey(String tenantId, String decisionKey) {
        repository.deleteByTenantIdAndDecisionKey(tenantId, decisionKey);
    }
}
