package be.myrmacta.infrastructure.persistence.promotion;

import be.myrmacta.domain.promotion.model.ConfigImportRecord;
import be.myrmacta.domain.promotion.port.out.ConfigImportLogRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaConfigImportLogRepositoryAdapter implements ConfigImportLogRepository {

    private final SpringDataConfigImportRepository repository;

    JpaConfigImportLogRepositoryAdapter(SpringDataConfigImportRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ConfigImportRecord save(ConfigImportRecord record) {
        return repository.saveAndFlush(ConfigImportJpaEntity.fromDomain(record)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfigImportRecord> findRecent(String tenantId, int limit) {
        return repository.findByTenantIdOrderByPerformedAtDesc(tenantId, PageRequest.of(0, limit)).stream()
                .map(ConfigImportJpaEntity::toDomain)
                .toList();
    }
}
