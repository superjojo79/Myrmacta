package com.flowforge.infrastructure.persistence.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataDossierWorkflowLinkRepository extends JpaRepository<DossierWorkflowLinkJpaEntity, UUID> {

    Optional<DossierWorkflowLinkJpaEntity> findFirstByDossierIdAndEndedAtIsNull(UUID dossierId);

    Optional<DossierWorkflowLinkJpaEntity>
            findFirstByDossierIdAndProcessDefinitionKeyAndEndedAtIsNullAndProcessInstanceIdIsNull(
                    UUID dossierId, String processDefinitionKey);

    List<DossierWorkflowLinkJpaEntity> findByDossierIdOrderBySequenceAsc(UUID dossierId);

    @Query("select coalesce(max(l.sequence), 0) + 1 from DossierWorkflowLinkJpaEntity l where l.dossierId = :dossierId")
    int nextSequence(@Param("dossierId") UUID dossierId);

    List<DossierWorkflowLinkJpaEntity> findByEndedAtIsNullAndProcessInstanceIdIsNullAndStartedAtBefore(
            Instant olderThan);
}
