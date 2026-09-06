package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierFieldValidator;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldValidationError;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.workflow.TaskQuestionFormKeys;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.domain.workflow.model.TaskQuestionAnswer;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.model.UnknownTaskQuestionSchemaException;
import com.flowforge.domain.workflow.port.in.CompleteTaskUseCase;
import com.flowforge.domain.workflow.port.out.TaskQuestionAnswerRepository;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CompleteTaskUseCaseImpl implements CompleteTaskUseCase {

    private final String tenantId;
    private final WorkflowEnginePort workflowEnginePort;
    private final DossierRepository dossierRepository;
    private final DossierTypeRepository dossierTypeRepository;
    private final TaskQuestionSchemaRepository taskQuestionSchemaRepository;
    private final TaskQuestionAnswerRepository taskQuestionAnswerRepository;
    private final DossierFieldValidator fieldValidator = new DossierFieldValidator();

    public CompleteTaskUseCaseImpl(
            String tenantId,
            WorkflowEnginePort workflowEnginePort,
            DossierRepository dossierRepository,
            DossierTypeRepository dossierTypeRepository,
            TaskQuestionSchemaRepository taskQuestionSchemaRepository,
            TaskQuestionAnswerRepository taskQuestionAnswerRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.dossierTypeRepository = Objects.requireNonNull(dossierTypeRepository, "dossierTypeRepository");
        this.taskQuestionSchemaRepository =
                Objects.requireNonNull(taskQuestionSchemaRepository, "taskQuestionSchemaRepository");
        this.taskQuestionAnswerRepository =
                Objects.requireNonNull(taskQuestionAnswerRepository, "taskQuestionAnswerRepository");
    }

    @Override
    public void complete(String taskId, String dossierId, Map<String, Object> variables, String actorUserId) {
        WorkflowVariables.assertNoReservedKeys(variables);
        assertDocumentTypeAllowed(dossierId, variables);
        Map<String, Object> effectiveVariables =
                assertAndRecordUserQuestionAnswers(taskId, dossierId, variables, actorUserId);
        workflowEnginePort.completeTask(taskId, effectiveVariables);
    }

    /**
     * D-USERQ-FORMKEY-PARSING: resolves the schema from {@code formKey} read server-side
     * ({@link WorkflowEnginePort#findFormKeyByTaskId}), never from a client-submitted value —
     * mirrors why {@link #assertDocumentTypeAllowed} exists (D-TASK-FORM-DOCTYPE-SCOPE). No-op
     * (returns {@code variables} as-is) if the task's formKey is not the {@code user-question}
     * kind. Reuses {@link DossierFieldValidator} (required + type conformance + `SELECT` options)
     * rather than a hand-rolled subset — found in Phase 7 review that an earlier version here only
     * checked `required`/`SELECT`, silently accepting a non-numeric value on a `NUMBER` field or a
     * non-date string on a `DATE` field.
     *
     * <p>Adversarial-review finding (post-Phase-9): {@code allowUnknownFields} was hard-coded to
     * {@code true} here, and the caller forwarded the raw {@code variables} to {@link
     * WorkflowEnginePort#completeTask} regardless — so a field not declared by the schema was
     * validated by nobody and still reached the engine as a process variable (able to steer a
     * downstream exclusive gateway), while never appearing in the {@link TaskQuestionAnswer} trace
     * whose entire purpose is to record what was submitted. Fixed by rejecting unknown fields
     * ({@code allowUnknownFields=false}) and returning the schema-filtered {@code answers} map —
     * not the raw input — as what actually completes the task, so the schema is a real allow-list
     * of the process variables a {@code user-question} task can produce, not just a checklist for
     * fields it happens to recognize.
     */
    private Map<String, Object> assertAndRecordUserQuestionAnswers(
            String taskId, String dossierId, Map<String, Object> variables, String actorUserId) {
        Map<String, Object> submitted = variables == null ? Map.of() : variables;
        String formKey = workflowEnginePort.findFormKeyByTaskId(taskId).orElse(null);
        Optional<String> schemaKeyOpt = TaskQuestionFormKeys.schemaKeyOf(formKey);
        if (schemaKeyOpt.isEmpty()) {
            return submitted;
        }
        String schemaKey = schemaKeyOpt.get();
        TaskQuestionSchema schema = taskQuestionSchemaRepository.findByKey(tenantId, schemaKey)
                .orElseThrow(() -> new UnknownTaskQuestionSchemaException("Unknown schema: " + schemaKey));

        List<FieldValidationError> errors = fieldValidator.validate(schema.fields(), submitted, false);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Schema '" + schemaKey + "' validation failed: " + errors);
        }

        Map<String, Object> answers = new LinkedHashMap<>();
        for (FieldDefinition field : schema.fields()) {
            Object value = submitted.get(field.name());
            if (value != null && !(value instanceof String s && s.isBlank())) {
                answers.put(field.name(), value);
            }
        }

        String processInstanceId = workflowEnginePort.findProcessInstanceIdByTaskId(taskId).orElse(null);
        taskQuestionAnswerRepository.save(new TaskQuestionAnswer(
                UUID.randomUUID(),
                tenantId,
                UUID.fromString(dossierId),
                taskId,
                processInstanceId,
                schemaKey,
                answers,
                actorUserId,
                Instant.now()));

        return answers;
    }

    /**
     * D-TASK-FORM-DOCTYPE-SCOPE (Phase 9 adversarial review finding E4): {@code
     * DossierType.documentTemplateKeys()} was only ever enforced at discovery time (the {@code
     * GET .../document-templates} endpoint filters to configured keys) — nothing stopped a task
     * holder from completing with an arbitrary {@code documentType} the tenant admin never
     * allow-listed. Any actor able to complete this task (EDIT on the dossier) already reaches the
     * same {@code ${documentGenerationTask}} in the same transaction, so this is enforced here
     * rather than trusting the frontend picker.
     */
    private void assertDocumentTypeAllowed(String dossierId, Map<String, Object> variables) {
        if (variables == null) {
            return;
        }
        Object documentType = variables.get(WorkflowVariables.DOCUMENT_TYPE);
        if (documentType == null) {
            return;
        }
        Dossier dossier = dossierRepository.findById(UUID.fromString(dossierId))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        DossierType type = dossierTypeRepository.findByCode(tenantId, dossier.dossierTypeCode())
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier type not found: " + tenantId + "/" + dossier.dossierTypeCode()));
        if (!type.documentTemplateKeys().contains(documentType.toString())) {
            throw new IllegalArgumentException(
                    "documentType '" + documentType + "' is not an allowed template for dossier type '"
                            + type.code() + "' -- configure it in Admin > Types > Templates de documents autorisés");
        }
    }
}
