package be.myrmacta.infrastructure.persistence.workflow;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkflowLinkGroupRepository extends JpaRepository<WorkflowLinkGroupJpaEntity, UUID> {}
