package com.flowforge.infrastructure.persistence.workflow;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataDmnDraftRepository extends JpaRepository<DmnDraftJpaEntity, DmnDraftJpaEntity.DmnDraftId> {

    Optional<DmnDraftJpaEntity> findByTenantIdAndDecisionKey(String tenantId, String decisionKey);

    void deleteByTenantIdAndDecisionKey(String tenantId, String decisionKey);
}
