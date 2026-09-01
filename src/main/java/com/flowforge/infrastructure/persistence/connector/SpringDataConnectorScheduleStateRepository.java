package com.flowforge.infrastructure.persistence.connector;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConnectorScheduleStateRepository
        extends JpaRepository<ConnectorScheduleStateJpaEntity, UUID> {}
