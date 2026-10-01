package com.flowforge.infrastructure.persistence.reporting;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSlaAlertStateRepository
        extends JpaRepository<SlaAlertStateJpaEntity, SlaAlertStateJpaEntity.Key> {

    Optional<SlaAlertStateJpaEntity> findByTenantIdAndDossierId(String tenantId, UUID dossierId);

    void deleteByTenantId(String tenantId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
            update SlaAlertStateJpaEntity e set e.lastStatus = :next, e.updatedAt = :now
            where e.tenantId = :tenantId and e.dossierId = :dossierId and e.lastStatus = :expected
            """)
    int transition(
            @org.springframework.data.repository.query.Param("tenantId") String tenantId,
            @org.springframework.data.repository.query.Param("dossierId") UUID dossierId,
            @org.springframework.data.repository.query.Param("expected") com.flowforge.domain.reporting.model.SlaStatus expected,
            @org.springframework.data.repository.query.Param("next") com.flowforge.domain.reporting.model.SlaStatus next,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);
}
