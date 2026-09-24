package com.flowforge.domain.workflow.portal.usecase;

import com.flowforge.domain.workflow.UserQuestionAnswers;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.portal.PortalAccessResolver;
import com.flowforge.domain.workflow.portal.PortalParticipantNotifier;
import com.flowforge.domain.workflow.portal.model.PortalAccessDeniedException;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.port.in.SubmitPortalTaskUseCase;
import com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * TODO §68 — ordre volontaire, dans <strong>une</strong> transaction (appelant transactionnel) :
 * <ol>
 *   <li>validation des réponses par le schéma (400 : le lien reste {@code ACTIVE}, T-SUBMIT-2) ;</li>
 *   <li>{@code ACTIVE → COMPLETED} en compare-and-set : une seconde soumission concurrente perd et
 *       reçoit le refus neutre (T-SUBMIT-3) ;</li>
 *   <li>trace {@code TaskQuestionAnswer} ({@code submittedBy = portal:<linkId>}) ;</li>
 *   <li>complétion de la tâche — le lien n'étant plus vivant, le listener d'invalidation ne le
 *       révoque pas ;</li>
 *   <li>notification des participants internes (US-6).</li>
 * </ol>
 */
public final class SubmitPortalTaskUseCaseImpl implements SubmitPortalTaskUseCase {

    private final String tenantId;
    private final PortalAccessResolver accessResolver;
    private final TaskQuestionSchemaRepository schemaRepository;
    private final UserQuestionAnswers userQuestionAnswers;
    private final WorkflowEnginePort workflowEnginePort;
    private final PortalLinkRepository repository;
    private final PortalParticipantNotifier notifier;
    private final Clock clock;

    public SubmitPortalTaskUseCaseImpl(
            String tenantId,
            PortalAccessResolver accessResolver,
            TaskQuestionSchemaRepository schemaRepository,
            UserQuestionAnswers userQuestionAnswers,
            WorkflowEnginePort workflowEnginePort,
            PortalLinkRepository repository,
            PortalParticipantNotifier notifier,
            Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.schemaRepository = Objects.requireNonNull(schemaRepository, "schemaRepository");
        this.userQuestionAnswers = Objects.requireNonNull(userQuestionAnswers, "userQuestionAnswers");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void submit(String token, Map<String, Object> submitted) {
        PortalAccessResolver.ResolvedAccess access = accessResolver.resolve(token);
        PortalLink link = access.link();
        Map<String, Object> values = submitted == null ? Map.of() : submitted;
        WorkflowVariables.assertNoReservedKeys(values);
        TaskQuestionSchema schema = schemaRepository.findByKey(tenantId, link.schemaKey())
                .orElseThrow(() -> new PortalAccessDeniedException("schema gone for link " + link.id()));
        Map<String, Object> answers = userQuestionAnswers.validate(schema, values);

        Instant now = clock.instant();
        PortalLink completed = link.completed(now);
        if (!repository.transition(link, completed)) {
            throw new PortalAccessDeniedException("link " + link.id() + " already used");
        }
        userQuestionAnswers.record(link.schemaKey(), link.dossierId(), link.taskId(), link.processInstanceId(), answers,
                link.actorId(), now);
        workflowEnginePort.completeTask(link.taskId(), answers);
        notifier.notifySubmitted(completed);
    }
}
