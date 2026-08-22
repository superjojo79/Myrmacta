package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.DossierStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface SpringDataDossierRepository
        extends JpaRepository<DossierJpaEntity, UUID>, JpaSpecificationExecutor<DossierJpaEntity> {
    Optional<DossierJpaEntity> findByTenantIdAndReference(String tenantId, String reference);

    @Query("select d from DossierJpaEntity d "
            + "where d.tenantId = :tenantId and d.status = :status and d.closedAt is not null "
            + "order by d.closedAt asc, d.id asc")
    List<DossierJpaEntity> findClosedForRetention(String tenantId, DossierStatus status, Pageable pageable);
}
