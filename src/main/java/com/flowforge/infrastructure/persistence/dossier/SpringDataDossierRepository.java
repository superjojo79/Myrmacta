package com.flowforge.infrastructure.persistence.dossier;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataDossierRepository extends JpaRepository<DossierJpaEntity, UUID> {
    Optional<DossierJpaEntity> findByTenantIdAndReference(String tenantId, String reference);
}
