package be.myrmacta.domain.workflow.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Traceability record for one {@code user-question} task completion (TODO §55, D-USERQ-TRACE-SCOPE)
 * — raw answers only, no aggregation/reporting built on top in this iteration. Written by
 * {@code CompleteTaskUseCaseImpl} in the same request as the task completion it records.
 */
public record TaskQuestionAnswer(
        UUID id,
        String tenantId,
        UUID dossierId,
        String taskId,
        String processInstanceId,
        String schemaKey,
        Map<String, Object> answers,
        String submittedBy,
        Instant submittedAt
) {
    public TaskQuestionAnswer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(schemaKey, "schemaKey");
        Objects.requireNonNull(submittedAt, "submittedAt");
        answers = answers == null ? Map.of() : Map.copyOf(answers);
    }
}
