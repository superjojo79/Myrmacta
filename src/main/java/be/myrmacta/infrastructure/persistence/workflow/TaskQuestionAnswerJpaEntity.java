package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.model.TaskQuestionAnswer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "task_question_answer")
class TaskQuestionAnswerJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "process_instance_id", length = 128)
    private String processInstanceId;

    @Column(name = "schema_key", nullable = false, length = 128)
    private String schemaKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> answers = Map.of();

    @Column(name = "submitted_by", length = 128)
    private String submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected TaskQuestionAnswerJpaEntity() {
        // JPA
    }

    private TaskQuestionAnswerJpaEntity(TaskQuestionAnswer answer) {
        this.id = answer.id();
        this.tenantId = answer.tenantId();
        this.dossierId = answer.dossierId();
        this.taskId = answer.taskId();
        this.processInstanceId = answer.processInstanceId();
        this.schemaKey = answer.schemaKey();
        this.answers = answer.answers();
        this.submittedBy = answer.submittedBy();
        this.submittedAt = answer.submittedAt();
    }

    static TaskQuestionAnswerJpaEntity fromDomain(TaskQuestionAnswer answer) {
        return new TaskQuestionAnswerJpaEntity(answer);
    }

    // No toDomain()/read method yet -- TaskQuestionAnswerRepository is write-only (D-USERQ-TRACE-SCOPE:
    // no consultation endpoint in this iteration). Add it back alongside the first real reader
    // rather than now, to avoid dead code (found unreachable in Phase 8 coverage review).
}
