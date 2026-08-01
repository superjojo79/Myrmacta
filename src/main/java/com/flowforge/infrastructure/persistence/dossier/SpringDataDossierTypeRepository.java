package com.flowforge.infrastructure.persistence.dossier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataDossierTypeRepository extends JpaRepository<DossierTypeJpaEntity, UUID> {
    Optional<DossierTypeJpaEntity> findByTenantIdAndCode(String tenantId, String code);

    List<DossierTypeJpaEntity> findAllByTenantIdOrderByCode(String tenantId);
}
