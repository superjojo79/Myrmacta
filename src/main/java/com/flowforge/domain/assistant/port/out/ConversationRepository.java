package com.flowforge.domain.assistant.port.out;

import com.flowforge.domain.assistant.model.ChatMessage;
import com.flowforge.domain.assistant.model.Conversation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository {

    Conversation save(Conversation conversation);

    ChatMessage saveMessage(ChatMessage message);

    /** Une conversation n'est visible que de son propriétaire (jamais d'un autre administrateur). */
    Optional<Conversation> findByIdAndOwner(UUID id, String tenantId, String ownerId);

    List<Conversation> listByOwner(String tenantId, String ownerId, int limit);

    List<ChatMessage> messages(UUID conversationId);

    /** Jetons consommés par le tenant ce jour-là (compteur propre, que la suppression d'une conversation n'efface pas). */
    long tokensOn(String tenantId, LocalDate day);

    void addTokens(String tenantId, LocalDate day, long tokens);

    boolean deleteByIdAndOwner(UUID id, String tenantId, String ownerId);

    int deleteOlderThan(String tenantId, Instant cutoff);
}
