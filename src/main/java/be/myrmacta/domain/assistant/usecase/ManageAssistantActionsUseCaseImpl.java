package be.myrmacta.domain.assistant.usecase;

import be.myrmacta.domain.assistant.model.AssistantAction;
import be.myrmacta.domain.assistant.model.AssistantExceptions.ActionNotFoundException;
import be.myrmacta.domain.assistant.model.AssistantExceptions.ActionNotPendingException;
import be.myrmacta.domain.assistant.model.AssistantExceptions.ConversationNotFoundException;
import be.myrmacta.domain.assistant.port.in.ManageAssistantActionsUseCase;
import be.myrmacta.domain.assistant.port.out.ConversationRepository;
import be.myrmacta.domain.assistant.port.out.DesignToolsPort;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §86 (LC3, revue M2) — appliquer une écriture proposée par l'assistant : avec l'identité de l'utilisateur, une seule fois
 * (prise atomique avant l'exécution), dans sa propre conversation. Le résultat ou l'échec est conservé sur l'action.
 */
public final class ManageAssistantActionsUseCaseImpl implements ManageAssistantActionsUseCase {

    private static final int MAX_OUTCOME_CHARS = 600;

    private final String tenantId;
    private final Clock clock;
    private final TenantAdminGuard guard;
    private final ConversationRepository repository;
    private final DesignToolsPort tools;

    public ManageAssistantActionsUseCaseImpl(
            String tenantId, Clock clock, TenantAdminGuard guard, ConversationRepository repository, DesignToolsPort tools) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tools = Objects.requireNonNull(tools, "tools");
    }

    @Override
    public List<AssistantAction> list(AccessContext.User actor, UUID conversationId) {
        owned(actor, conversationId);
        return repository.actions(conversationId);
    }

    @Override
    public AssistantAction apply(AccessContext.User actor, UUID conversationId, UUID actionId) {
        owned(actor, conversationId);
        AssistantAction action = repository.findAction(actionId, conversationId).orElseThrow(() -> new ActionNotFoundException(actionId));
        if (!repository.transitionAction(actionId, AssistantAction.Status.PENDING, AssistantAction.Status.APPLYING, null, clock.instant())) {
            throw new ActionNotPendingException(actionId);
        }
        try {
            String result = tools.invokeConfirmed(action.tool(), action.argumentsJson(), actor);
            repository.transitionAction(actionId, AssistantAction.Status.APPLYING, AssistantAction.Status.APPLIED,
                    truncate(result), clock.instant());
        } catch (RuntimeException failure) {
            String reason = failure instanceof DesignToolsPort.ToolInvocationException
                    ? failure.getMessage()
                    : "the tool failed (" + failure.getClass().getSimpleName() + ")";
            repository.transitionAction(actionId, AssistantAction.Status.APPLYING, AssistantAction.Status.FAILED,
                    truncate(reason), clock.instant());
        }
        return repository.findAction(actionId, conversationId).orElseThrow(() -> new ActionNotFoundException(actionId));
    }

    @Override
    public AssistantAction dismiss(AccessContext.User actor, UUID conversationId, UUID actionId) {
        owned(actor, conversationId);
        repository.findAction(actionId, conversationId).orElseThrow(() -> new ActionNotFoundException(actionId));
        if (!repository.transitionAction(actionId, AssistantAction.Status.PENDING, AssistantAction.Status.DISMISSED, null, clock.instant())) {
            throw new ActionNotPendingException(actionId);
        }
        return repository.findAction(actionId, conversationId).orElseThrow(() -> new ActionNotFoundException(actionId));
    }

    private void owned(AccessContext.User actor, UUID conversationId) {
        guard.requireTenantOrWorkflowAdmin(actor);
        repository.findByIdAndOwner(conversationId, tenantId, actor.userId())
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_OUTCOME_CHARS ? value : value.substring(0, MAX_OUTCOME_CHARS) + "…";
    }
}
