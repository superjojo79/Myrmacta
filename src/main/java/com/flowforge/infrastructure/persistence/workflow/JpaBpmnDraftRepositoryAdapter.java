package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.model.BpmnDraft;
import com.flowforge.domain.workflow.port.out.BpmnDraftRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaBpmnDraftRepositoryAdapter implements BpmnDraftRepository {

    private final SpringDataBpmnDraftRepository repository;

    JpaBpmnDraftRepositoryAdapter(SpringDataBpmnDraftRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BpmnDraft> findByTenantIdAndProcessDefinitionKey(String tenantId, String processDefinitionKey) {
        return repository
                .findByTenantIdAndProcessDefinitionKey(tenantId, processDefinitionKey)
                .map(BpmnDraftJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public BpmnDraft save(BpmnDraft draft) {
        return repository.saveAndFlush(BpmnDraftJpaEntity.fromDomain(draft)).toDomain();
    }

    @Override
    @Transactional
    public void deleteByTenantIdAndProcessDefinitionKey(String tenantId, String processDefinitionKey) {
        repository.deleteByTenantIdAndProcessDefinitionKey(tenantId, processDefinitionKey);
    }
}
