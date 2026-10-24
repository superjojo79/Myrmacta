package com.flowforge.infrastructure.persistence.connector;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConnectorStoredObjectRepository extends JpaRepository<ConnectorStoredObjectJpaEntity, UUID> {

    Optional<ConnectorStoredObjectJpaEntity> findByTenantIdAndStorageRef(String tenantId, String storageRef);
}
