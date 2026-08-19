package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaDossierWorkflowLinkRepositoryAdapter implements DossierWorkflowLinkRepository {

    private final SpringDataDossierWorkflowLinkRepository repository;

    JpaDossierWorkflowLinkRepositoryAdapter(SpringDataDossierWorkflowLinkRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public DossierWorkflowLink save(DossierWorkflowLink link) {
        return repository.saveAndFlush(DossierWorkflowLinkJpaEntity.fromDomain(link)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DossierWorkflowLink> findById(UUID id) {
        return repository.findById(id).map(DossierWorkflowLinkJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DossierWorkflowLink> findByProcessInstanceId(String tenantId, String processInstanceId) {
        return repository.findByTenantIdAndProcessInstanceId(tenantId, processInstanceId)
                .map(DossierWorkflowLinkJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DossierWorkflowLink> findActiveByDossierId(UUID dossierId) {
        return repository.findFirstByDossierIdAndEndedAtIsNull(dossierId).map(DossierWorkflowLinkJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DossierWorkflowLink> findReservedByDossierAndProcess(UUID dossierId, String processDefinitionKey) {
        return repository
                .findFirstByDossierIdAndProcessDefinitionKeyAndEndedAtIsNullAndProcessInstanceIdIsNull(
                        dossierId, processDefinitionKey)
                .map(DossierWorkflowLinkJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DossierWorkflowLink> findByDossierIdOrderBySequence(UUID dossierId) {
        return repository.findByDossierIdOrderBySequenceAsc(dossierId).stream()
                .map(DossierWorkflowLinkJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int nextSequence(UUID dossierId) {
        return repository.nextSequence(dossierId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DossierWorkflowLink> findOrphanReserved(Instant olderThan) {
        return repository.findByEndedAtIsNullAndProcessInstanceIdIsNullAndStartedAtBefore(olderThan).stream()
                .map(DossierWorkflowLinkJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteAllByDossierId(UUID dossierId) {
        repository.deleteByDossierId(dossierId);
    }
}
