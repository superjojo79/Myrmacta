package be.myrmacta.infrastructure.persistence.workflow;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataDmnDraftRepository extends JpaRepository<DmnDraftJpaEntity, DmnDraftJpaEntity.DmnDraftId> {

    Optional<DmnDraftJpaEntity> findByTenantIdAndDecisionKey(String tenantId, String decisionKey);

    void deleteByTenantIdAndDecisionKey(String tenantId, String decisionKey);

    java.util.List<DmnDraftJpaEntity> findAllByTenantId(String tenantId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
            delete from DmnDraftJpaEntity d
            where d.tenantId = :tenantId and d.provenance <> 'UI' and d.updatedAt < :cutoff
            """)
    int deleteAutomatedOlderThan(
            @org.springframework.data.repository.query.Param("tenantId") String tenantId,
            @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}
