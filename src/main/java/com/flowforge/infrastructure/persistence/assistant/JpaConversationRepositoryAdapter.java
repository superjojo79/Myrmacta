package com.flowforge.infrastructure.persistence.assistant;

import com.flowforge.domain.assistant.model.ChatMessage;
import com.flowforge.domain.assistant.model.Conversation;
import com.flowforge.domain.assistant.port.out.ConversationRepository;
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

    JpaConversationRepositoryAdapter(
            SpringDataAssistantConversationRepository conversations,
            SpringDataAssistantMessageRepository messages,
            SpringDataAssistantUsageRepository usage) {
        this.conversations = conversations;
        this.messages = messages;
        this.usage = usage;
    }

    @Override
    @Transactional
    public Conversation save(Conversation conversation) {
        return conversations.saveAndFlush(AssistantConversationJpaEntity.fromDomain(conversation)).toDomain();
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
    public boolean deleteByIdAndOwner(UUID id, String tenantId, String ownerId) {
        if (conversations.findByIdAndTenantIdAndOwnerId(id, tenantId, ownerId).isEmpty()) {
            return false;
        }
        messages.deleteByConversation(id);
        return conversations.deleteOwned(id, tenantId, ownerId) > 0;
    }

    @Override
    @Transactional
    public int deleteOlderThan(String tenantId, Instant cutoff) {
        messages.deleteOlderThan(tenantId, cutoff);
        return conversations.deleteOlderThan(tenantId, cutoff);
    }
}
