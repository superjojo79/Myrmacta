package com.flowforge.infrastructure.persistence.dossier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataDossierElementRepository extends JpaRepository<DossierElementJpaEntity, UUID> {

    Optional<DossierElementJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    List<DossierElementJpaEntity> findByDossierIdOrderByAddedAtAsc(UUID dossierId);

    Page<DossierElementJpaEntity> findByDossierIdOrderByAddedAtAsc(UUID dossierId, Pageable pageable);

    void deleteByDossierId(UUID dossierId);
}
