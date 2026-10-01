package com.flowforge.infrastructure.persistence.parcours;

import com.flowforge.domain.parcours.model.Parcours;
import com.flowforge.domain.parcours.model.ParcoursDefinition;
import com.flowforge.domain.parcours.port.out.ParcoursRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** La définition est stockée en JSON ; une relecture qui échoue est une erreur franche (jamais un parcours à moitié lu). */
@Component
class JpaParcoursRepositoryAdapter implements ParcoursRepository {

    private final SpringDataParcoursRepository repository;
    private final ObjectMapper mapper;

    JpaParcoursRepositoryAdapter(SpringDataParcoursRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public boolean save(Parcours p, Integer expectedVersion) {
        String json = mapper.writeValueAsString(p.definition());
        if (expectedVersion == null) {
            ParcoursJpaEntity e = new ParcoursJpaEntity();
            e.id = p.id();
            e.tenantId = p.tenantId();
            e.key = p.key();
            e.label = p.label();
            e.definition = json;
            e.version = p.version();
            e.createdBy = p.createdBy();
            e.updatedBy = p.updatedBy();
            e.createdAt = p.createdAt();
            e.updatedAt = p.updatedAt();
            try {
                repository.saveAndFlush(e);
                return true;
            } catch (DataIntegrityViolationException duplicate) {
                return false; // créé entre-temps par quelqu'un d'autre
            }
        }
        return repository.updateIfVersion(p.tenantId(), p.key(), expectedVersion, p.version(), p.label(), json, p.updatedBy(), p.updatedAt()) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Parcours> findByKey(String tenantId, String key) {
        return repository.findByTenantIdAndKey(tenantId, key).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Parcours> list(String tenantId) {
        return repository.findByTenantIdOrderByKeyAsc(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean delete(String tenantId, String key) {
        return repository.deleteByKey(tenantId, key) > 0;
    }

    @Override
    public void markCompiled(String tenantId, String key, Instant at, String sha256) {
        repository.markCompiled(tenantId, key, at, sha256);
    }

    private Parcours toDomain(ParcoursJpaEntity e) {
        return new Parcours(e.id, e.tenantId, e.key, e.label, mapper.readValue(e.definition, ParcoursDefinition.class), e.version,
                e.createdBy, e.updatedBy, e.createdAt, e.updatedAt, e.lastCompiledAt, e.lastCompiledSha256);
    }
}
