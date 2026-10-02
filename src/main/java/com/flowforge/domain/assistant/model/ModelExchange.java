package com.flowforge.domain.assistant.model;

import java.util.List;
import java.util.Objects;

/** Échange avec le modèle de langage : requête, messages, réponse. Aucun type propre à un fournisseur. */
public final class ModelExchange {

    private ModelExchange() {}

    public record ToolCall(String id, String name, String argumentsJson) {
        public ToolCall {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            argumentsJson = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
        }
    }

    public record ToolResult(String toolCallId, String content, boolean error) {
        public ToolResult {
            Objects.requireNonNull(toolCallId, "toolCallId");
            Objects.requireNonNull(content, "content");
        }
    }

    /** {@code text} peut être vide ; un message ASSISTANT porte des {@code toolCalls}, le message USER suivant les {@code toolResults}. */
    public record Message(Role role, String text, List<ToolCall> toolCalls, List<ToolResult> toolResults) {
        public enum Role {
            USER,
            ASSISTANT
        }

        public Message {
            Objects.requireNonNull(role, "role");
            text = text == null ? "" : text;
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
            toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        }

        public static Message user(String text) {
            return new Message(Role.USER, text, null, null);
        }

        public static Message assistant(String text, List<ToolCall> calls) {
            return new Message(Role.ASSISTANT, text, calls, null);
        }

        public static Message toolResults(List<ToolResult> results) {
            return new Message(Role.USER, "", null, results);
        }
    }

    public record Request(String system, List<Message> messages, List<ToolSpec> tools, int maxOutputTokens) {
        public Request {
            Objects.requireNonNull(system, "system");
            messages = List.copyOf(messages);
            tools = List.copyOf(tools);
        }
    }

    public record Reply(String text, List<ToolCall> toolCalls, long inputTokens, long outputTokens) {
        public Reply {
            text = text == null ? "" : text;
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }
}
