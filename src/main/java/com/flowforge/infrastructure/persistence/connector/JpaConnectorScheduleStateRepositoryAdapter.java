package com.flowforge.infrastructure.persistence.connector;

import com.flowforge.domain.connector.port.out.ConnectorScheduleStateRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaConnectorScheduleStateRepositoryAdapter implements ConnectorScheduleStateRepository {

    private final SpringDataConnectorScheduleStateRepository repository;

    JpaConnectorScheduleStateRepositoryAdapter(SpringDataConnectorScheduleStateRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findLastTriggeredAt(UUID connectorInstanceId) {
        return repository.findById(connectorInstanceId).map(ConnectorScheduleStateJpaEntity::lastTriggeredAt);
    }

    @Override
    @Transactional
    public void upsert(UUID connectorInstanceId, Instant triggeredAt) {
        repository.saveAndFlush(new ConnectorScheduleStateJpaEntity(connectorInstanceId, triggeredAt));
    }
}
