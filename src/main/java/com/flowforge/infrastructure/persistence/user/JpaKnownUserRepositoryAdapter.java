package com.flowforge.infrastructure.persistence.user;

import com.flowforge.domain.user.model.KnownUser;
import com.flowforge.domain.user.port.out.KnownUserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaKnownUserRepositoryAdapter implements KnownUserRepository {

    private final SpringDataKnownUserRepository repository;

    JpaKnownUserRepositoryAdapter(SpringDataKnownUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void upsert(KnownUser user) {
        repository.findByTenantIdAndUserId(user.tenantId(), user.userId())
                .ifPresentOrElse(
                        existing -> existing.refresh(user),
                        () -> repository.saveAndFlush(KnownUserJpaEntity.create(user)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KnownUser> findById(String tenantId, String userId) {
        return repository.findByTenantIdAndUserId(tenantId, userId).map(KnownUserJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnownUser> findAll(String tenantId) {
        return repository.findByTenantIdOrderByDisplayNameAsc(tenantId).stream()
                .map(KnownUserJpaEntity::toDomain)
                .toList();
    }
}
