package com.flowforge.infrastructure.persistence.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataDossierWorkflowLinkRepository extends JpaRepository<DossierWorkflowLinkJpaEntity, UUID> {

    Optional<DossierWorkflowLinkJpaEntity> findFirstByDossierIdAndEndedAtIsNull(UUID dossierId);

    Optional<DossierWorkflowLinkJpaEntity> findByTenantIdAndProcessInstanceId(String tenantId, String processInstanceId);

    Optional<DossierWorkflowLinkJpaEntity>
            findFirstByDossierIdAndProcessDefinitionKeyAndEndedAtIsNullAndProcessInstanceIdIsNull(
                    UUID dossierId, String processDefinitionKey);

    List<DossierWorkflowLinkJpaEntity> findByDossierIdOrderBySequenceAsc(UUID dossierId);

    Page<DossierWorkflowLinkJpaEntity> findByDossierIdOrderBySequenceAsc(UUID dossierId, Pageable pageable);

    @Query("select coalesce(max(l.sequence), 0) + 1 from DossierWorkflowLinkJpaEntity l where l.dossierId = :dossierId")
    int nextSequence(@Param("dossierId") UUID dossierId);

    List<DossierWorkflowLinkJpaEntity> findByEndedAtIsNullAndProcessInstanceIdIsNullAndStartedAtBefore(
            Instant olderThan);

    /** D5 : prise par UPDATE conditionnel — un seul réconciliateur obtient 1 ligne tant que le lien est orphelin et non loué. */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DossierWorkflowLinkJpaEntity l set l.reconcileClaimedUntil = :until
            where l.id = :id and l.endedAt is null and l.processInstanceId is null
              and (l.reconcileClaimedUntil is null or l.reconcileClaimedUntil < :now)
            """)
    int claimForReconcile(@Param("id") UUID id, @Param("now") Instant now, @Param("until") Instant until);

    void deleteByDossierId(UUID dossierId);
}
