package be.myrmacta.domain.assistant.usecase;

import be.myrmacta.domain.assistant.model.AssistantExceptions.ConversationNotFoundException;
import be.myrmacta.domain.assistant.port.in.ManageConversationsUseCase;
import be.myrmacta.domain.assistant.port.out.ConversationRepository;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.assistant.model.Conversation;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** TODO §86 (LC3) — relire et supprimer ses conversations avec l'assistant (jamais celles d'un autre administrateur). */
public final class ManageConversationsUseCaseImpl implements ManageConversationsUseCase {

    private static final int LIST_LIMIT = 50;

    private final String tenantId;
    private final TenantAdminGuard guard;
    private final ConversationRepository repository;
    private final java.time.Clock clock;

    public ManageConversationsUseCaseImpl(String tenantId, TenantAdminGuard guard, ConversationRepository repository) {
        this(tenantId, guard, repository, java.time.Clock.systemUTC());
    }

    public ManageConversationsUseCaseImpl(
            String tenantId, TenantAdminGuard guard, ConversationRepository repository, java.time.Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
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
        if (!repository.deleteByIdAndOwner(id, tenantId, actor.userId(), clock.instant())) {
            throw new ConversationNotFoundException(id);
        }
    }
}
