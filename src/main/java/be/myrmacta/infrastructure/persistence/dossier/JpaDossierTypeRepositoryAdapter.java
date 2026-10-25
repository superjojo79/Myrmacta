package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.DossierFieldRules;
import be.myrmacta.domain.dossier.DossierUniqueKeys;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.DossierTypeSchemaIncompatibleException;
import be.myrmacta.domain.dossier.model.UniqueFieldKeys;
import be.myrmacta.domain.dossier.model.UniqueKey;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.persistence.SpringPages;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaDossierTypeRepositoryAdapter implements DossierTypeRepository {

    private static final int SAMPLE_LIMIT = 5;

    private final SpringDataDossierTypeRepository repository;
    private final SpringDataDossierRepository dossierRepository;
    private final JdbcDossierUniqueValueStore uniqueValues;
    private final DossierUniqueKeys uniqueKeys;

    @Autowired
    public JpaDossierTypeRepositoryAdapter(
            SpringDataDossierTypeRepository repository,
            SpringDataDossierRepository dossierRepository,
            JdbcDossierUniqueValueStore uniqueValues,
            DossierFieldRules fieldRules) {
        this.repository = repository;
        this.dossierRepository = dossierRepository;
        this.uniqueValues = uniqueValues;
        this.uniqueKeys = new DossierUniqueKeys(fieldRules);
    }

    /**
     * TODO §102 (D-UNIQUE-TYPE-LOCK) : sous verrou exclusif du type, les valeurs uniques sont reconstruites à partir des
     * dossiers ouverts et des nouvelles définitions. Aucun enregistrement de dossier du type ne peut s'intercaler : il
     * attend le verrou partagé. Un doublon (créé entre le contrôle de compatibilité et ce verrou) annule tout.
     */
    @Override
    @Transactional
    public void save(DossierType type) {
        uniqueValues.lockTypeExclusive(type.tenantId(), type.code());
        repository.save(DossierTypeJpaEntity.fromDomain(type));
        rebuildUniqueValues(type);
    }

    private void rebuildUniqueValues(DossierType type) {
        Map<UUID, Set<UniqueKey>> keysByDossier = new LinkedHashMap<>();
        List<Dossier> dossiers = List.of();
        if (UniqueFieldKeys.hasUniqueField(type.fieldDefinitions())) {
            dossiers = dossierRepository.findByTenantIdAndDossierTypeCode(type.tenantId(), type.code()).stream()
                    .map(DossierJpaEntity::toDomain)
                    .filter(dossier -> !dossier.status().blocksMutation())
                    .toList();
            dossiers.forEach(dossier -> keysByDossier.put(dossier.id(), uniqueKeys.of(type.fieldDefinitions(), dossier)));
        }
        if (!uniqueValues.replaceForType(type.tenantId(), type.code(), keysByDossier).isEmpty()) {
            var duplicates = uniqueKeys.duplicates(type.fieldDefinitions(), dossiers);
            List<String> references = duplicates.stream().flatMap(d -> d.references().stream()).distinct().toList();
            throw new DossierTypeSchemaIncompatibleException(
                    references.size(), references.stream().limit(SAMPLE_LIMIT).toList(), duplicates);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DossierType> findById(UUID id) {
        return repository.findById(id).map(DossierTypeJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DossierType> findByCode(String tenantId, String code) {
        return repository.findByTenantIdAndCode(tenantId, code).map(DossierTypeJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DossierType> findAll(String tenantId) {
        return repository.findAllByTenantIdOrderByCode(tenantId).stream()
                .map(DossierTypeJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<DossierType> page(String tenantId, Set<String> allowedCodes, PageQuery query) {
        if (allowedCodes != null && allowedCodes.isEmpty()) {
            return PageResult.empty(query.page(), query.size());
        }
        var pageable = SpringPages.of(query);
        var page = allowedCodes == null
                ? repository.findAllByTenantIdOrderByCodeAsc(tenantId, pageable)
                : repository.findAllByTenantIdAndCodeInOrderByCodeAsc(tenantId, allowedCodes, pageable);
        return SpringPages.map(page, DossierTypeJpaEntity::toDomain);
    }
}
