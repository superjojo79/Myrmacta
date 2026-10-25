package be.myrmacta.infrastructure.persistence.workflow;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBpmnDraftRepository
        extends JpaRepository<BpmnDraftJpaEntity, BpmnDraftJpaEntity.BpmnDraftId> {

    Optional<BpmnDraftJpaEntity> findByTenantIdAndProcessDefinitionKey(String tenantId, String processDefinitionKey);

    void deleteByTenantIdAndProcessDefinitionKey(String tenantId, String processDefinitionKey);

    java.util.List<BpmnDraftJpaEntity> findAllByTenantId(String tenantId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
            delete from BpmnDraftJpaEntity d
            where d.tenantId = :tenantId and d.provenance <> 'UI' and d.updatedAt < :cutoff
            """)
    int deleteAutomatedOlderThan(
            @org.springframework.data.repository.query.Param("tenantId") String tenantId,
            @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}
