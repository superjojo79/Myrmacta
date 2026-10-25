package be.myrmacta.infrastructure.persistence.assistant;

import be.myrmacta.domain.assistant.model.ChatMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_message")
class AssistantMessageJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "role", nullable = false, length = 16)
    private String role;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AssistantMessageJpaEntity() {}

    static AssistantMessageJpaEntity fromDomain(ChatMessage m) {
        AssistantMessageJpaEntity e = new AssistantMessageJpaEntity();
        e.id = m.id();
        e.conversationId = m.conversationId();
        e.seq = m.seq();
        e.role = m.role().name();
        e.content = m.content();
        e.toolName = m.toolName();
        e.createdAt = m.createdAt();
        return e;
    }

    ChatMessage toDomain() {
        return new ChatMessage(id, conversationId, seq, ChatMessage.Role.valueOf(role), content, toolName, createdAt);
    }
}
