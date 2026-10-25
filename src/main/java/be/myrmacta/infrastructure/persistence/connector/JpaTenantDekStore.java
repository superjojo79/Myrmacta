package be.myrmacta.infrastructure.persistence.connector;

import be.myrmacta.infrastructure.connector.crypto.TenantDekStore;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaTenantDekStore implements TenantDekStore {

    private final SpringDataTenantConnectorDekRepository repository;

    JpaTenantDekStore(SpringDataTenantConnectorDekRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findWrappedDek(String tenantId) {
        return repository.findById(tenantId).map(TenantConnectorDekJpaEntity::dekWrapped);
    }

    @Override
    @Transactional
    public void saveWrappedDek(String tenantId, String wrappedDek, int keyVersion) {
        if (repository.existsById(tenantId)) {
            return;
        }
        repository.saveAndFlush(
                new TenantConnectorDekJpaEntity(tenantId, wrappedDek, keyVersion, Instant.now()));
    }
}
