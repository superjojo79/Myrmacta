package be.myrmacta.infrastructure.persistence.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTaskQuestionSchemaRepository extends JpaRepository<TaskQuestionSchemaJpaEntity, UUID> {

    Optional<TaskQuestionSchemaJpaEntity> findByTenantIdAndSchemaKey(String tenantId, String schemaKey);

    List<TaskQuestionSchemaJpaEntity> findByTenantIdOrderBySchemaKeyAsc(String tenantId);

    void deleteByTenantIdAndSchemaKey(String tenantId, String schemaKey);
}
