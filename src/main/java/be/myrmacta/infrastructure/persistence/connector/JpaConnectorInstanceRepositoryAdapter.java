package be.myrmacta.infrastructure.persistence.connector;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.connector.port.out.ConnectorSecretCipher;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.persistence.SpringPages;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaConnectorInstanceRepositoryAdapter implements ConnectorInstanceRepository {

    private final SpringDataConnectorInstanceRepository repository;
    private final ConnectorSecretCipher cipher;

    JpaConnectorInstanceRepositoryAdapter(
            SpringDataConnectorInstanceRepository repository, ConnectorSecretCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Override
    @Transactional
    public ConnectorInstanceData save(ConnectorInstanceData data) {
        var encrypted = cipher.encryptPasswordFields(data.tenantId(), data.connectorType(), data.config());
        Optional<ConnectorInstanceJpaEntity> existing =
                repository.findByIdAndTenantId(data.id(), data.tenantId());
        ConnectorInstanceJpaEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.applyDomain(data, encrypted);
        } else {
            entity = ConnectorInstanceJpaEntity.fromDomain(data, encrypted);
        }
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConnectorInstanceData> findByIdAndTenantId(UUID id, String tenantId) {
        return repository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConnectorInstanceData> findByIdAndTenantIdIncludingDeleted(UUID id, String tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConnectorInstanceData> listByTenantId(String tenantId) {
        return repository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ConnectorInstanceData> pageByTenantId(String tenantId, PageQuery query) {
        return SpringPages.map(
                repository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId, SpringPages.of(query)),
                this::toDomain);
    }

    @Override
    @Transactional
    public void softDelete(UUID id, String tenantId) {
        repository
                .findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .ifPresent(entity -> {
                    entity.markDeleted(Instant.now());
                    repository.saveAndFlush(entity);
                });
    }

    private ConnectorInstanceData toDomain(ConnectorInstanceJpaEntity entity) {
        return entity.toDomain(
                cipher.decryptPasswordFields(entity.tenantId(), entity.connectorType(), entity.config()));
    }
}
