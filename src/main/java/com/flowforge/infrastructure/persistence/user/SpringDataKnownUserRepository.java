package com.flowforge.infrastructure.persistence.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataKnownUserRepository extends JpaRepository<KnownUserJpaEntity, UUID> {

    Optional<KnownUserJpaEntity> findByTenantIdAndUserId(String tenantId, String userId);

    List<KnownUserJpaEntity> findByTenantIdOrderByDisplayNameAsc(String tenantId);
}
