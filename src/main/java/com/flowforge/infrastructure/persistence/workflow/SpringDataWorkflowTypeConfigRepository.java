package com.flowforge.infrastructure.persistence.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkflowTypeConfigRepository extends JpaRepository<WorkflowTypeConfigJpaEntity, UUID> {

    Optional<WorkflowTypeConfigJpaEntity> findByTenantIdAndWorkflowKey(String tenantId, String workflowKey);

    List<WorkflowTypeConfigJpaEntity> findByTenantIdOrderByWorkflowKeyAsc(String tenantId);

    Page<WorkflowTypeConfigJpaEntity> findByTenantIdOrderByWorkflowKeyAsc(String tenantId, Pageable pageable);
}
