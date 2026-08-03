package com.flowforge.infrastructure.persistence.connector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConnectorInstanceRepository extends JpaRepository<ConnectorInstanceJpaEntity, UUID> {

    Optional<ConnectorInstanceJpaEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, String tenantId);

    Optional<ConnectorInstanceJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    List<ConnectorInstanceJpaEntity> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(String tenantId);
}
