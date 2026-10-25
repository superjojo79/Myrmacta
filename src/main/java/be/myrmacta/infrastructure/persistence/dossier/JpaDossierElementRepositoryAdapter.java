package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.persistence.SpringPages;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaDossierElementRepositoryAdapter implements DossierElementRepository {

    private final SpringDataDossierElementRepository repository;

    public JpaDossierElementRepositoryAdapter(SpringDataDossierElementRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public DossierElement save(DossierElement element) {
        return repository.save(DossierElementJpaEntity.fromDomain(element)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DossierElement> findByIdAndTenantId(UUID id, String tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).map(DossierElementJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DossierElement> findByDossierIdOrderByAddedAtAsc(UUID dossierId) {
        return repository.findByDossierIdOrderByAddedAtAsc(dossierId).stream()
                .map(DossierElementJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<DossierElement> pageByDossierId(UUID dossierId, PageQuery query) {
        var page = repository.findByDossierIdOrderByAddedAtAsc(dossierId, SpringPages.of(query));
        return SpringPages.map(page, DossierElementJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void deleteAllByDossierId(UUID dossierId) {
        repository.deleteByDossierId(dossierId);
    }
}
