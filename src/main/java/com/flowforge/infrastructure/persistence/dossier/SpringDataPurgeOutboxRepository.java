package com.flowforge.infrastructure.persistence.dossier;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPurgeOutboxRepository extends JpaRepository<PurgeOutboxJpaEntity, UUID> {
    boolean existsByDossierId(UUID dossierId);
}
