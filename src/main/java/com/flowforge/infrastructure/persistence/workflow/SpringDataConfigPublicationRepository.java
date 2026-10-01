package com.flowforge.infrastructure.persistence.workflow;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConfigPublicationRepository extends JpaRepository<ConfigPublicationJpaEntity, UUID> {

    List<ConfigPublicationJpaEntity> findByTenantIdOrderByPublishedAtDesc(String tenantId, Pageable page);

    List<ConfigPublicationJpaEntity> findByTenantIdAndArtifactTypeAndArtifactKeyOrderByPublishedAtDesc(
            String tenantId, String artifactType, String artifactKey, Pageable page);
}
