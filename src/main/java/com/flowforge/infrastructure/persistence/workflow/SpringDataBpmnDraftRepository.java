package com.flowforge.infrastructure.persistence.workflow;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBpmnDraftRepository
        extends JpaRepository<BpmnDraftJpaEntity, BpmnDraftJpaEntity.BpmnDraftId> {

    Optional<BpmnDraftJpaEntity> findByTenantIdAndProcessDefinitionKey(String tenantId, String processDefinitionKey);

    void deleteByTenantIdAndProcessDefinitionKey(String tenantId, String processDefinitionKey);
}
