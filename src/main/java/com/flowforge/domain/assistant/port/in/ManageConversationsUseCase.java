package com.flowforge.domain.assistant.port.in;

import com.flowforge.domain.assistant.model.ChatMessage;
import com.flowforge.domain.assistant.model.Conversation;
import com.flowforge.domain.dossier.model.AccessContext;
import java.util.List;
import java.util.UUID;

public interface ManageConversationsUseCase {

    List<Conversation> list(AccessContext.User actor);

    ConversationView get(AccessContext.User actor, UUID id);

    void delete(AccessContext.User actor, UUID id);

    record ConversationView(Conversation conversation, List<ChatMessage> messages) {}
}
