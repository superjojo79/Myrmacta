package com.flowforge.infrastructure.persistence.reporting;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSlaAlertStateRepository
        extends JpaRepository<SlaAlertStateJpaEntity, SlaAlertStateJpaEntity.Key> {

    Optional<SlaAlertStateJpaEntity> findByTenantIdAndDossierId(String tenantId, UUID dossierId);

    void deleteByTenantId(String tenantId);
}
