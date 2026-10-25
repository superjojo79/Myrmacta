package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.DossierFieldRules;
import be.myrmacta.domain.dossier.DossierUniqueKeys;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.UniqueFieldKeys;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaDossierRepositoryAdapter implements DossierRepository {

    private final SpringDataDossierRepository repository;
    private final SpringDataDossierTypeRepository typeRepository;
    private final JdbcDossierUniqueValueStore uniqueValues;
    private final DossierUniqueKeys uniqueKeys;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public JpaDossierRepositoryAdapter(
            SpringDataDossierRepository repository,
            SpringDataDossierTypeRepository typeRepository,
            JdbcDossierUniqueValueStore uniqueValues,
            DossierFieldRules fieldRules) {
        this.repository = repository;
        this.typeRepository = typeRepository;
        this.uniqueValues = uniqueValues;
        this.uniqueKeys = new DossierUniqueKeys(fieldRules);
    }

    /**
     * TODO §102 (D-UNIQUE-STORE, D-UNIQUE-TYPE-LOCK) : toute écriture d'un dossier tient à jour ses valeurs uniques dans la
     * même transaction, sous verrou partagé du type. Un doublon lève {@code DossierFieldValidationException} ({@code
     * UNIQUE}) et annule l'écriture.
     */
    @Override
    @Transactional
    public Dossier save(Dossier dossier) {
        uniqueValues.lockTypeShared(dossier.tenantId(), dossier.dossierTypeCode());
        List<FieldDefinition> definitions = currentDefinitions(dossier.tenantId(), dossier.dossierTypeCode());
        // flush so @Version is incremented before mapping back to the domain
        Dossier saved = repository.saveAndFlush(DossierJpaEntity.fromDomain(dossier)).toDomain();
        // Sans champ unique, le type ne tient aucune ligne : la reconstruction du type les a retirées (rebuild).
        if (UniqueFieldKeys.hasUniqueField(definitions)) {
            uniqueValues.sync(saved, uniqueKeys.of(definitions, saved));
        }
        return saved;
    }

    /** Définitions relues après le verrou : une copie déjà chargée dans ce contexte de persistance serait périmée. */
    private List<FieldDefinition> currentDefinitions(String tenantId, String dossierTypeCode) {
        return typeRepository.findByTenantIdAndCode(tenantId, dossierTypeCode)
                .map(entity -> {
                    entityManager.refresh(entity);
                    return entity.toDomain().fieldDefinitions();
                })
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Dossier> findById(UUID id) {
        return repository.findById(id).map(DossierJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Dossier> findByReference(String tenantId, String reference) {
        return repository.findByTenantIdAndReference(tenantId, reference).map(DossierJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dossier> findClosedForRetention(String tenantId, int limit) {
        return repository
                .findClosedForRetention(tenantId, DossierStatus.CLOTURE, PageRequest.of(0, limit))
                .stream()
                .map(DossierJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dossier> findByDossierTypeCode(String tenantId, String dossierTypeCode) {
        return repository.findByTenantIdAndDossierTypeCode(tenantId, dossierTypeCode).stream()
                .map(DossierJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
}
