package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.WorkflowAlreadyRunningException;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.infrastructure.persistence.SpringPages;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
        try {
            return repository.saveAndFlush(DossierWorkflowLinkJpaEntity.fromDomain(link)).toDomain();
        } catch (DataIntegrityViolationException ex) {
            // uq_dossier_workflow_link_active : un second reserve() concurrent sur le même
            // dossier perd la course au niveau DB — traduit en exception domaine pour que les
            // appelants (ex. OutboxRelayScheduler) la traitent comme WorkflowAlreadyRunningException,
            // pas comme un échec transitoire à retenter.
            throw new WorkflowAlreadyRunningException(link.dossierId(), ex);
        }
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

    /** TODO §94 (revue L4) — une seule requête pour la liste des incidents du tenant. */
    @Override
    @Transactional(readOnly = true)
    public List<DossierWorkflowLink> findByProcessInstanceIds(
            String tenantId, java.util.Collection<String> processInstanceIds) {
        if (processInstanceIds.isEmpty()) {
            return List.of();
        }
        return repository.findByTenantIdAndProcessInstanceIdIn(tenantId, processInstanceIds).stream()
                .map(DossierWorkflowLinkJpaEntity::toDomain)
                .toList();
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
    public PageResult<DossierWorkflowLink> pageByDossierId(UUID dossierId, PageQuery query) {
        var page = repository.findByDossierIdOrderBySequenceAsc(dossierId, SpringPages.of(query));
        return SpringPages.map(page, DossierWorkflowLinkJpaEntity::toDomain);
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

    // Sans transaction englobante : la prise est un UPDATE atomique propre au dépôt.
    @Override
    public boolean claimForReconcile(UUID linkId, Instant now, Instant leaseUntil) {
        return repository.claimForReconcile(linkId, now, leaseUntil) == 1;
    }

    @Override
    @Transactional
    public void deleteAllByDossierId(UUID dossierId) {
        repository.deleteByDossierId(dossierId);
    }
}
