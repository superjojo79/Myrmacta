package com.flowforge.infrastructure.persistence.dossier;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface SpringDataDossierRepository
        extends JpaRepository<DossierJpaEntity, UUID>, JpaSpecificationExecutor<DossierJpaEntity> {
    Optional<DossierJpaEntity> findByTenantIdAndReference(String tenantId, String reference);
}
