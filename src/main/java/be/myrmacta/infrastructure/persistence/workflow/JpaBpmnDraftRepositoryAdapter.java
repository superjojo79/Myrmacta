package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.model.BpmnDraft;
import be.myrmacta.domain.workflow.port.out.BpmnDraftRepository;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaBpmnDraftRepositoryAdapter implements BpmnDraftRepository {

    private final SpringDataBpmnDraftRepository repository;
    private final ObjectMapper mapper;

    JpaBpmnDraftRepositoryAdapter(SpringDataBpmnDraftRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BpmnDraft> findByTenantIdAndProcessDefinitionKey(String tenantId, String processDefinitionKey) {
        return repository
                .findByTenantIdAndProcessDefinitionKey(tenantId, processDefinitionKey)
                .map(e -> e.toDomain(mapper));
    }

    @Override
    @Transactional
    public BpmnDraft save(BpmnDraft draft) {
        return repository.saveAndFlush(BpmnDraftJpaEntity.fromDomain(draft, mapper)).toDomain(mapper);
    }

    @Override
    @Transactional
    public void deleteByTenantIdAndProcessDefinitionKey(String tenantId, String processDefinitionKey) {
        repository.deleteByTenantIdAndProcessDefinitionKey(tenantId, processDefinitionKey);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<BpmnDraft> findAllByTenantId(String tenantId) {
        return repository.findAllByTenantId(tenantId).stream().map(e -> e.toDomain(mapper)).toList();
    }

    @Override
    public int deleteAutomatedOlderThan(String tenantId, java.time.Instant cutoff) {
        return repository.deleteAutomatedOlderThan(tenantId, cutoff);
    }
}
