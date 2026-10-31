package be.myrmacta.infrastructure.persistence.user;

import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.port.out.UserGroupRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** TODO §114 — {@link UserGroupRepository} sur les tables {@code user_group} et {@code user_group_member}. */
@Component
class JpaUserGroupRepositoryAdapter implements UserGroupRepository {

    private final SpringDataUserGroupRepository repository;

    JpaUserGroupRepositoryAdapter(SpringDataUserGroupRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserGroup> findAll(String tenantId) {
        return repository.findByTenantIdOrderByCodeAsc(tenantId).stream().map(UserGroupJpaEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserGroup> findByCode(String tenantId, String code) {
        return repository.findByTenantIdAndCode(tenantId, code).map(UserGroupJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(UserGroup group) {
        repository.findByTenantIdAndCode(group.tenantId(), group.code())
                .ifPresentOrElse(
                        existing -> existing.refresh(group),
                        () -> repository.saveAndFlush(UserGroupJpaEntity.create(group)));
    }

    @Override
    @Transactional
    public boolean delete(String tenantId, String code) {
        Optional<UserGroupJpaEntity> existing = repository.findByTenantIdAndCode(tenantId, code);
        existing.ifPresent(repository::delete);
        return existing.isPresent();
    }
}
