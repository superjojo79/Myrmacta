package be.myrmacta.infrastructure.persistence.assistant;

import be.myrmacta.domain.assistant.model.Conversation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_conversation")
class AssistantConversationJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "owner_id", nullable = false, length = 128)
    private String ownerId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "turns", nullable = false)
    private int turns;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "busy_until")
    private Instant busyUntil;

    protected AssistantConversationJpaEntity() {}

    static AssistantConversationJpaEntity fromDomain(Conversation c) {
        AssistantConversationJpaEntity e = new AssistantConversationJpaEntity();
        e.id = c.id();
        e.tenantId = c.tenantId();
        e.ownerId = c.ownerId();
        e.title = c.title();
        e.createdAt = c.createdAt();
        e.updatedAt = c.updatedAt();
        e.turns = c.turns();
        e.inputTokens = c.inputTokens();
        e.outputTokens = c.outputTokens();
        return e;
    }

    Conversation toDomain() {
        return new Conversation(id, tenantId, ownerId, title, createdAt, updatedAt, turns, inputTokens, outputTokens);
    }
}
