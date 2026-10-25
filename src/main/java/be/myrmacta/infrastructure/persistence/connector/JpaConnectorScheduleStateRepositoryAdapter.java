package be.myrmacta.infrastructure.persistence.connector;

import be.myrmacta.domain.connector.port.out.ConnectorScheduleStateRepository;
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

    // Sans transaction englobante : chaque opération est atomique côté dépôt, et une violation de clé primaire (autre exécutant
    // arrivé le premier) n'invalide pas une transaction que nous aurions ouverte.
    @Override
    public boolean claimTrigger(UUID connectorInstanceId, Instant expectedLast, Instant claimedAt) {
        if (expectedLast == null) {
            try {
                return repository.insertFirst(connectorInstanceId, claimedAt) == 1;
            } catch (org.springframework.dao.DataIntegrityViolationException alreadyClaimed) {
                return false;
            }
        }
        return repository.compareAndSet(connectorInstanceId, expectedLast, claimedAt) == 1;
    }

    @Override
    public void releaseTrigger(UUID connectorInstanceId, Instant previousLast, Instant claimedAt) {
        if (previousLast == null) {
            repository.deleteIfAt(connectorInstanceId, claimedAt);
        } else {
            repository.compareAndSet(connectorInstanceId, claimedAt, previousLast);
        }
    }

    @Override
    @Transactional
    public void upsert(UUID connectorInstanceId, Instant triggeredAt) {
        repository.saveAndFlush(new ConnectorScheduleStateJpaEntity(connectorInstanceId, triggeredAt));
    }
}
