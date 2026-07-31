package com.flowforge.infrastructure.persistence.dossier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataDossierTypeAclRepository extends JpaRepository<DossierTypeAclJpaEntity, UUID> {
    Optional<DossierTypeAclJpaEntity> findByTenantIdAndDossierTypeCodeAndSubjectUserId(
            String tenantId,
            String dossierTypeCode,
            String subjectUserId);

    void deleteByTenantIdAndDossierTypeCodeAndSubjectUserId(
            String tenantId,
            String dossierTypeCode,
            String subjectUserId);

    List<DossierTypeAclJpaEntity> findAllByTenantIdAndSubjectUserIdOrderByDossierTypeCode(
            String tenantId,
            String subjectUserId);
}
