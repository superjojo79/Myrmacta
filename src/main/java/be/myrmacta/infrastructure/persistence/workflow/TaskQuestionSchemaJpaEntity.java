package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "task_question_schema",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_task_question_schema_tenant_key",
                columnNames = {"tenant_id", "schema_key"}))
class TaskQuestionSchemaJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "schema_key", nullable = false, length = 128)
    private String schemaKey;

    @Column(nullable = false)
    private String label;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private List<FieldDefinition> fields = List.of();

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    protected TaskQuestionSchemaJpaEntity() {
        // JPA
    }

    private TaskQuestionSchemaJpaEntity(TaskQuestionSchema schema) {
        this.id = schema.id();
        this.tenantId = schema.tenantId();
        this.schemaKey = schema.schemaKey();
        this.label = schema.label();
        this.fields = schema.fields();
        this.createdBy = schema.createdBy();
        this.updatedBy = schema.updatedBy();
    }

    static TaskQuestionSchemaJpaEntity fromDomain(TaskQuestionSchema schema) {
        return new TaskQuestionSchemaJpaEntity(schema);
    }

    TaskQuestionSchema toDomain() {
        return new TaskQuestionSchema(id, tenantId, schemaKey, label, fields, createdBy, updatedBy);
    }
}
