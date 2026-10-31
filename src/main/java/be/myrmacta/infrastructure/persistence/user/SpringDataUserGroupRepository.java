package be.myrmacta.infrastructure.persistence.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserGroupRepository extends JpaRepository<UserGroupJpaEntity, UUID> {

    Optional<UserGroupJpaEntity> findByTenantIdAndCode(String tenantId, String code);

    List<UserGroupJpaEntity> findByTenantIdOrderByCodeAsc(String tenantId);
}
