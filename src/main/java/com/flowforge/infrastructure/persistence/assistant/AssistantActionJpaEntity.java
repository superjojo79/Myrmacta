package com.flowforge.infrastructure.persistence.assistant;

import com.flowforge.domain.assistant.model.AssistantAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_action")
class AssistantActionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "tool", nullable = false, length = 100)
    private String tool;

    @Column(name = "arguments", nullable = false)
    private String arguments;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected AssistantActionJpaEntity() {}

    static AssistantActionJpaEntity fromDomain(AssistantAction a) {
        AssistantActionJpaEntity e = new AssistantActionJpaEntity();
        e.id = a.id();
        e.conversationId = a.conversationId();
        e.tool = a.tool();
        e.arguments = a.argumentsJson();
        e.status = a.status().name();
        e.outcome = a.outcome();
        e.createdAt = a.createdAt();
        e.resolvedAt = a.resolvedAt();
        return e;
    }

    AssistantAction toDomain() {
        return new AssistantAction(id, conversationId, tool, arguments, AssistantAction.Status.valueOf(status), outcome, createdAt,
                resolvedAt);
    }
}
