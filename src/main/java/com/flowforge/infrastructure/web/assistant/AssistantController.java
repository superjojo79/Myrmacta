package com.flowforge.infrastructure.web.assistant;

import com.flowforge.domain.assistant.model.ChatMessage;
import com.flowforge.domain.assistant.model.Conversation;
import com.flowforge.domain.assistant.port.in.ChatWithAssistantUseCase;
import com.flowforge.domain.assistant.port.in.ManageConversationsUseCase;
import com.flowforge.infrastructure.config.AssistantProperties;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §86 (LC3, D-ASSIST-ROLES) — assistant de conception : {@code TENANT_ADMIN} et {@code WORKFLOW_ADMIN} (les rôles qui
 * conçoivent la configuration). Une conversation n'est visible que de son auteur. L'assistant agit avec l'identité de
 * l'utilisateur de la session ; ce qu'il écrit pour un processus ou une table DMN est un brouillon à relire (Admin → Brouillons).
 */
@RestController
@RequestMapping("/api/assistant")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
class AssistantController {

    private final ChatWithAssistantUseCase chat;
    private final ManageConversationsUseCase conversations;
    private final AccessContextFactory accessContextFactory;
    private final AssistantProperties properties;

    AssistantController(
            ChatWithAssistantUseCase chat,
            ManageConversationsUseCase conversations,
            AccessContextFactory accessContextFactory,
            AssistantProperties properties) {
        this.chat = chat;
        this.conversations = conversations;
        this.accessContextFactory = accessContextFactory;
        this.properties = properties;
    }

    @GetMapping("/status")
    StatusResponse status() {
        return new StatusResponse(properties.enabled(), properties.enabled() ? properties.model() : null);
    }

    @PostMapping("/messages")
    ChatResponse send(@RequestBody MessageRequest request, Authentication authentication) {
        var result = chat.chat(accessContextFactory.user(authentication), request.conversationId(), request.message());
        return new ChatResponse(result.conversationId(), result.reply(), result.activity(), result.producedDrafts());
    }

    @GetMapping("/conversations")
    List<ConversationSummary> list(Authentication authentication) {
        return conversations.list(accessContextFactory.user(authentication)).stream().map(ConversationSummary::from).toList();
    }

    @GetMapping("/conversations/{id}")
    ConversationDetail get(@PathVariable UUID id, Authentication authentication) {
        var view = conversations.get(accessContextFactory.user(authentication), id);
        return new ConversationDetail(ConversationSummary.from(view.conversation()),
                view.messages().stream().map(MessageView::from).toList());
    }

    @DeleteMapping("/conversations/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        conversations.delete(accessContextFactory.user(authentication), id);
        return ResponseEntity.noContent().build();
    }

    record StatusResponse(boolean enabled, String model) {}

    record MessageRequest(UUID conversationId, String message) {}

    record ChatResponse(UUID conversationId, String reply, List<ChatWithAssistantUseCase.ToolActivity> activity, boolean producedDrafts) {}

    record ConversationSummary(UUID id, String title, Instant createdAt, Instant updatedAt, int turns) {
        static ConversationSummary from(Conversation c) {
            return new ConversationSummary(c.id(), c.title(), c.createdAt(), c.updatedAt(), c.turns());
        }
    }

    record MessageView(int seq, String role, String content, String toolName, Instant createdAt) {
        static MessageView from(ChatMessage m) {
            return new MessageView(m.seq(), m.role().name(), m.content(), m.toolName(), m.createdAt());
        }
    }

    record ConversationDetail(ConversationSummary conversation, List<MessageView> messages) {}
}
