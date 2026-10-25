package be.myrmacta.domain.assistant.port.in;

import be.myrmacta.domain.assistant.model.ChatMessage;
import be.myrmacta.domain.assistant.model.Conversation;
import be.myrmacta.domain.dossier.model.AccessContext;
import java.util.List;
import java.util.UUID;

public interface ManageConversationsUseCase {

    List<Conversation> list(AccessContext.User actor);

    ConversationView get(AccessContext.User actor, UUID id);

    void delete(AccessContext.User actor, UUID id);

    record ConversationView(Conversation conversation, List<ChatMessage> messages) {}
}
