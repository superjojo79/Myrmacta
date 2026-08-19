package com.flowforge.infrastructure.persistence.dossier;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataDossierTypeRepository extends JpaRepository<DossierTypeJpaEntity, UUID> {
    Optional<DossierTypeJpaEntity> findByTenantIdAndCode(String tenantId, String code);

    List<DossierTypeJpaEntity> findAllByTenantIdOrderByCode(String tenantId);

    Page<DossierTypeJpaEntity> findAllByTenantIdOrderByCodeAsc(String tenantId, Pageable pageable);

    Page<DossierTypeJpaEntity> findAllByTenantIdAndCodeInOrderByCodeAsc(
            String tenantId, Collection<String> codes, Pageable pageable);
}
