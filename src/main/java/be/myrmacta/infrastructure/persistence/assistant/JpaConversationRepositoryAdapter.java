package be.myrmacta.infrastructure.persistence.assistant;

import be.myrmacta.domain.assistant.model.AssistantAction;
import be.myrmacta.domain.assistant.model.AssistantExceptions;
import be.myrmacta.domain.assistant.model.ChatMessage;
import be.myrmacta.domain.assistant.model.Conversation;
import be.myrmacta.domain.assistant.port.out.ConversationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaConversationRepositoryAdapter implements ConversationRepository {

    private final SpringDataAssistantConversationRepository conversations;
    private final SpringDataAssistantMessageRepository messages;
    private final SpringDataAssistantUsageRepository usage;
    private final SpringDataAssistantActionRepository actions;

    JpaConversationRepositoryAdapter(
            SpringDataAssistantConversationRepository conversations,
            SpringDataAssistantMessageRepository messages,
            SpringDataAssistantUsageRepository usage,
            SpringDataAssistantActionRepository actions) {
        this.actions = actions;
        this.conversations = conversations;
        this.messages = messages;
        this.usage = usage;
    }

    @Override
    @Transactional
    public Conversation create(Conversation conversation) {
        return conversations.saveAndFlush(AssistantConversationJpaEntity.fromDomain(conversation)).toDomain();
    }

    @Override
    public boolean update(Conversation c) {
        return conversations.updateCounters(c.id(), c.updatedAt(), c.turns(), c.inputTokens(), c.outputTokens()) > 0;
    }

    @Override
    public boolean tryLock(UUID id, Instant now, Instant until) {
        return conversations.lock(id, now, until) > 0;
    }

    @Override
    public void unlock(UUID id) {
        conversations.unlock(id);
    }

    @Override
    @Transactional
    public AssistantAction saveAction(AssistantAction action) {
        return actions.saveAndFlush(AssistantActionJpaEntity.fromDomain(action)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssistantAction> findAction(UUID id, UUID conversationId) {
        return actions.findById(id).filter(a -> a.toDomain().conversationId().equals(conversationId)).map(AssistantActionJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssistantAction> actions(UUID conversationId) {
        return actions.findByConversationIdOrderByCreatedAtAsc(conversationId).stream().map(AssistantActionJpaEntity::toDomain).toList();
    }

    @Override
    public boolean transitionAction(UUID id, AssistantAction.Status expected, AssistantAction.Status next, String outcome, Instant at) {
        return actions.transition(id, expected.name(), next.name(), outcome, at) > 0;
    }

    @Override
    @Transactional
    public ChatMessage saveMessage(ChatMessage message) {
        return messages.saveAndFlush(AssistantMessageJpaEntity.fromDomain(message)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findByIdAndOwner(UUID id, String tenantId, String ownerId) {
        return conversations.findByIdAndTenantIdAndOwnerId(id, tenantId, ownerId).map(AssistantConversationJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Conversation> listByOwner(String tenantId, String ownerId, int limit) {
        return conversations.findByTenantIdAndOwnerIdOrderByUpdatedAtDesc(tenantId, ownerId, PageRequest.of(0, limit)).stream()
                .map(AssistantConversationJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> messages(UUID conversationId) {
        return messages.findByConversationIdOrderBySeqAsc(conversationId).stream()
                .map(AssistantMessageJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long tokensOn(String tenantId, LocalDate day) {
        return usage.findById(new AssistantUsageJpaEntity.Key(tenantId, day)).map(AssistantUsageJpaEntity::tokens).orElse(0L);
    }

    @Override
    public void addTokens(String tenantId, LocalDate day, long tokens) {
        if (tokens <= 0) {
            return;
        }
        if (usage.add(tenantId, day, tokens) > 0) {
            return;
        }
        try {
            usage.saveAndFlush(new AssistantUsageJpaEntity(tenantId, day, tokens));
        } catch (DataIntegrityViolationException raced) {
            // Un autre exécutant a créé la ligne entre-temps : on ajoute à la sienne.
            usage.add(tenantId, day, tokens);
        }
    }

    @Override
    @Transactional
    public boolean deleteByIdAndOwner(UUID id, String tenantId, String ownerId, Instant now) {
        if (conversations.findByIdAndTenantIdAndOwnerId(id, tenantId, ownerId).isEmpty()) {
            return false;
        }
        if (conversations.deleteIdle(id, tenantId, ownerId, now) == 0) {
            throw new AssistantExceptions.AssistantBusyException();
        }
        return true; // messages et actions suivent par cascade
    }

    @Override
    @Transactional
    public int deleteOlderThan(String tenantId, Instant cutoff) {
        messages.deleteOlderThan(tenantId, cutoff);
        actions.deleteOlderThan(tenantId, cutoff);
        return conversations.deleteOlderThan(tenantId, cutoff);
    }
}
