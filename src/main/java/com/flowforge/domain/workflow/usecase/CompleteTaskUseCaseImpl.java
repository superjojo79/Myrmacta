package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierFieldValidator;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.workflow.TaskAssignmentGuard;
import com.flowforge.domain.workflow.TaskQuestionFormKeys;
import com.flowforge.domain.workflow.UserQuestionAnswers;
import com.flowforge.domain.workflow.DocumentTypeAllowList;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.domain.workflow.model.TaskQuestionAnswer;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.model.UnknownTaskQuestionSchemaException;
import com.flowforge.domain.workflow.port.in.CompleteTaskUseCase;
import com.flowforge.domain.workflow.port.out.TaskQuestionAnswerRepository;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import java.time.Instant;
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
    private final UserQuestionAnswers userQuestionAnswers;
    private final DossierWorkflowLinkRepository linkRepository;
    private final WorkflowParticipantPort participantPort;
    private final SendNotificationUseCase sendNotificationUseCase;

    public CompleteTaskUseCaseImpl(
            String tenantId,
            WorkflowEnginePort workflowEnginePort,
            DossierRepository dossierRepository,
            DossierTypeRepository dossierTypeRepository,
            TaskQuestionSchemaRepository taskQuestionSchemaRepository,
            TaskQuestionAnswerRepository taskQuestionAnswerRepository,
            DossierWorkflowLinkRepository linkRepository,
            WorkflowParticipantPort participantPort,
            SendNotificationUseCase sendNotificationUseCase) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.dossierTypeRepository = Objects.requireNonNull(dossierTypeRepository, "dossierTypeRepository");
        this.taskQuestionSchemaRepository =
                Objects.requireNonNull(taskQuestionSchemaRepository, "taskQuestionSchemaRepository");
        this.taskQuestionAnswerRepository =
                Objects.requireNonNull(taskQuestionAnswerRepository, "taskQuestionAnswerRepository");
        this.userQuestionAnswers = new UserQuestionAnswers(this.tenantId, this.taskQuestionAnswerRepository);
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.participantPort = Objects.requireNonNull(participantPort, "participantPort");
        this.sendNotificationUseCase = Objects.requireNonNull(sendNotificationUseCase, "sendNotificationUseCase");
    }

    @Override
    public void complete(String taskId, String dossierId, Map<String, Object> variables, AccessContext.User actor) {
        Objects.requireNonNull(actor, "actor");
        assertAssignee(taskId, actor);
        WorkflowVariables.assertNoReservedKeys(variables);
        assertDocumentTypeAllowed(dossierId, variables);
        Map<String, Object> effectiveVariables =
                assertAndRecordUserQuestionAnswers(taskId, dossierId, variables, actor.userId());
        // F6 (Phase 10 adversarial review): resolved *before* completeTask, not after. If this
        // completion is the workflow's last task, completeTask's own state transition can flip
        // the link to inactive (endedAt set, via the synchronous updateWorkflowLinkStatusTask) —
        // a lookup performed after that point would then silently find no active link and skip
        // notifying other participants entirely, even though they were active participants right
        // up until this completion.
        Optional<DossierWorkflowLink> link = linkRepository.findActiveByDossierId(UUID.fromString(dossierId));
        workflowEnginePort.completeTask(taskId, effectiveVariables);
        notifyOtherParticipants(link, dossierId, taskId, actor.userId());
    }

    /**
     * M6 (TODO §66): no active link found (dossier without a workflow link, or an already-closed
     * one) is an expected, silent no-op — not every completed task has other participants to
     * notify. Unlike {@code WorkflowStateEventListener} (an infrastructure listener reacting to
     * the engine after the fact), {@link #sendNotificationUseCase} is called here inside the
     * synchronous request — {@code WorkflowTaskController#complete} is {@code @Transactional}, so
     * the {@code completeTask(...)} call above is <strong>not yet committed</strong> when this
     * runs: a real send failure propagates like the rest of this method and rolls back the task
     * completion together with it, consistent with every other write in this bounded context
     * (domain cannot depend on org.slf4j — ArchUnit — so there is no way to log-and-swallow from
     * here the way an infrastructure component would). Corrected per F4 (Phase 10 adversarial
     * review): this comment previously claimed the completion was "already committed" above, which
     * was false — the same fix now applies {@code @Transactional} consistently to {@code reassign}
     * (see {@link WorkflowLinkVisualizationUseCaseImpl#reassign}).
     */
    private void notifyOtherParticipants(
            Optional<DossierWorkflowLink> link, String dossierId, String taskId, String actorUserId) {
        if (link.isEmpty()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "type", "TASK_COMPLETED_BY_OTHER",
                "title", "Tâche terminée",
                "body", "Une tâche du workflow a été terminée par un autre participant.",
                "linkId", link.get().id().toString(),
                "dossierId", dossierId,
                "taskId", taskId);
        for (String userId : participantPort.findParticipantUserIds(link.get())) {
            if (userId.equals(actorUserId)) {
                continue;
            }
            sendNotificationUseCase.send(
                    new NotificationEvent(List.of(NotificationChannel.BROWSER), null, null, null, userId, null, payload),
                    tenantId);
        }
    }

    /**
     * TODO §57 H2: assignment is not advisory. Unassigned tasks remain completable by anyone who
     * already passed the dossier EDIT gate. Admins may complete a task assigned to someone else
     * (same privilege as reassignment).
     */
    private void assertAssignee(String taskId, AccessContext.User actor) {
        TaskAssignmentGuard.assertActorMayAct(
                workflowEnginePort.findAssigneeByTaskId(taskId).orElse(null), actor);
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

        Map<String, Object> answers = userQuestionAnswers.validate(schema, submitted);
        String processInstanceId = workflowEnginePort.findProcessInstanceIdByTaskId(taskId).orElse(null);
        userQuestionAnswers.record(
                schemaKey, UUID.fromString(dossierId), taskId, processInstanceId, answers, actorUserId, Instant.now());

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
        if (!DocumentTypeAllowList.concerns(variables)) {
            return;
        }
        Dossier dossier = dossierRepository.findById(UUID.fromString(dossierId))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        DossierType type = dossierTypeRepository.findByCode(tenantId, dossier.dossierTypeCode())
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier type not found: " + tenantId + "/" + dossier.dossierTypeCode()));
        DocumentTypeAllowList.assertAllowed(type, variables);
    }
}
