package be.myrmacta.infrastructure.persistence.connector;

import be.myrmacta.domain.connector.port.out.ConnectorStoredObjectRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** D-STORAGE-REF-DOSSIER (TODO §101) : une ligne par objet écrit par {@code document-storage}. */
@Component
class JpaConnectorStoredObjectRepositoryAdapter implements ConnectorStoredObjectRepository {

    private final SpringDataConnectorStoredObjectRepository repository;

    JpaConnectorStoredObjectRepositoryAdapter(SpringDataConnectorStoredObjectRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void register(String tenantId, String storageRef, UUID dossierId) {
        repository.saveAndFlush(
                new ConnectorStoredObjectJpaEntity(UUID.randomUUID(), tenantId, storageRef, dossierId, Instant.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isStoredFor(String tenantId, String storageRef, UUID dossierId) {
        return dossierId != null && repository.findByTenantIdAndStorageRef(tenantId, storageRef)
                .map(row -> dossierId.equals(row.dossierId()))
                .orElse(false);
    }

    @Override
    @Transactional
    public void forget(String tenantId, String storageRef) {
        repository.findByTenantIdAndStorageRef(tenantId, storageRef).ifPresent(row -> {
            repository.delete(row);
            repository.flush();
        });
    }
}
