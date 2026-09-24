package com.flowforge.domain.workflow;

import com.flowforge.domain.dossier.model.DossierFieldValidator;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldValidationError;
import com.flowforge.domain.workflow.model.TaskQuestionAnswer;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.port.out.TaskQuestionAnswerRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Validation et traçabilité des réponses d'un formulaire {@code user-question} (TODO §55),
 * partagées entre la complétion interne ({@code CompleteTaskUseCaseImpl}) et la soumission par un
 * tiers externe (TODO §68, {@code SubmitPortalTaskUseCaseImpl}) — une seule règle, pas deux copies
 * qui divergeraient (leçon TODO §52 Phase 9).
 *
 * <p>Champs inconnus refusés ({@code allowUnknownFields=false}) et seules les réponses filtrées par
 * le schéma sont renvoyées : le schéma est une vraie allowlist des variables de process qu'une
 * tâche {@code user-question} peut produire (revue adversariale post-Phase 9, TODO §55).
 */
public final class UserQuestionAnswers {

    private final DossierFieldValidator fieldValidator = new DossierFieldValidator();
    private final String tenantId;
    private final TaskQuestionAnswerRepository answerRepository;

    public UserQuestionAnswers(String tenantId, TaskQuestionAnswerRepository answerRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.answerRepository = Objects.requireNonNull(answerRepository, "answerRepository");
    }

    /** Réponses filtrées par le schéma ; {@link IllegalArgumentException} si invalides. */
    public Map<String, Object> validate(TaskQuestionSchema schema, Map<String, Object> submitted) {
        Map<String, Object> values = submitted == null ? Map.of() : submitted;
        List<FieldValidationError> errors = fieldValidator.validate(schema.fields(), values, false);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Schema '" + schema.schemaKey() + "' validation failed: " + errors);
        }
        Map<String, Object> answers = new LinkedHashMap<>();
        for (FieldDefinition field : schema.fields()) {
            Object value = values.get(field.name());
            if (value != null && !(value instanceof String s && s.isBlank())) {
                answers.put(field.name(), value);
            }
        }
        return answers;
    }

    public void record(
            String schemaKey, UUID dossierId, String taskId, String processInstanceId,
            Map<String, Object> answers, String submittedBy, Instant submittedAt) {
        answerRepository.save(new TaskQuestionAnswer(
                UUID.randomUUID(), tenantId, dossierId, taskId, processInstanceId, schemaKey, answers, submittedBy,
                submittedAt));
    }
}
