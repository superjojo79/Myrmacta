package com.flowforge.infrastructure.persistence.promotion;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConfigImportRepository extends JpaRepository<ConfigImportJpaEntity, UUID> {

    List<ConfigImportJpaEntity> findByTenantIdOrderByPerformedAtDesc(String tenantId, Pageable page);
}
