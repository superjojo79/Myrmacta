package com.flowforge.domain.assistant.usecase;

import com.flowforge.domain.assistant.model.AssistantExceptions.ConversationNotFoundException;
import com.flowforge.domain.assistant.port.in.ManageConversationsUseCase;
import com.flowforge.domain.assistant.port.out.ConversationRepository;
import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.assistant.model.Conversation;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** TODO §86 (LC3) — relire et supprimer ses conversations avec l'assistant (jamais celles d'un autre administrateur). */
public final class ManageConversationsUseCaseImpl implements ManageConversationsUseCase {

    private static final int LIST_LIMIT = 50;

    private final String tenantId;
    private final TenantAdminGuard guard;
    private final ConversationRepository repository;

    public ManageConversationsUseCaseImpl(String tenantId, TenantAdminGuard guard, ConversationRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public List<Conversation> list(AccessContext.User actor) {
        guard.requireTenantOrWorkflowAdmin(actor);
        return repository.listByOwner(tenantId, actor.userId(), LIST_LIMIT);
    }

    @Override
    public ConversationView get(AccessContext.User actor, UUID id) {
        guard.requireTenantOrWorkflowAdmin(actor);
        Conversation conversation = repository.findByIdAndOwner(id, tenantId, actor.userId())
                .orElseThrow(() -> new ConversationNotFoundException(id));
        return new ConversationView(conversation, repository.messages(id));
    }

    @Override
    public void delete(AccessContext.User actor, UUID id) {
        guard.requireTenantOrWorkflowAdmin(actor);
        if (!repository.deleteByIdAndOwner(id, tenantId, actor.userId())) {
            throw new ConversationNotFoundException(id);
        }
    }
}
