package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.model.DossierRelation;
import be.myrmacta.domain.dossier.model.DuplicateDossierRelationException;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DossierRelationJpaAdapter implements DossierRelationRepository {

    private final SpringDataDossierRelationRepository repository;

    public DossierRelationJpaAdapter(SpringDataDossierRelationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public DossierRelation save(DossierRelation relation) {
        try {
            return repository.saveAndFlush(DossierRelationJpaEntity.fromDomain(relation)).toDomain();
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateDossierRelationException("Duplicate dossier relation", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DossierRelation> findByDossier(String tenantId, UUID dossierId) {
        return repository.findByDossier(tenantId, dossierId).stream()
                .map(DossierRelationJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DossierRelation> findByIdempotencyKey(
            String tenantId, UUID sourceDossierId, String idempotencyKey) {
        return repository
                .findByTenantIdAndSourceDossierIdAndIdempotencyKey(tenantId, sourceDossierId, idempotencyKey)
                .map(DossierRelationJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void deleteRelationsTouching(UUID dossierId) {
        repository.deleteTouching(dossierId);
    }
}
