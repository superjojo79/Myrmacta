package com.flowforge.infrastructure.assistant;

import com.flowforge.domain.assistant.model.AssistantExceptions.ModelUnavailableException;
import com.flowforge.domain.assistant.model.ModelExchange;
import com.flowforge.domain.assistant.model.ToolSpec;
import com.flowforge.domain.assistant.port.out.LanguageModelPort;
import com.flowforge.infrastructure.config.AssistantProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §86 (LC3, D-ASSIST-PROVIDER) — adaptateur de l'API Messages d'Anthropic (appel d'outils inclus), par {@code java.net.http}
 * : aucune dépendance de plus. La clé n'est envoyée que dans l'en-tête {@code x-api-key} ; aucune erreur ne la reprend, et le
 * corps d'une réponse d'erreur n'est jamais relayé à l'utilisateur (il peut citer la requête).
 */
public final class AnthropicLanguageModel implements LanguageModelPort {

    private final AssistantProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public AnthropicLanguageModel(AssistantProperties properties, ObjectMapper mapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER) // une redirection ne doit jamais emporter la clé ailleurs
                .build();
    }

    @Override
    public ModelExchange.Reply complete(ModelExchange.Request request) {
        String body = toJson(request);
        HttpRequest http = HttpRequest.newBuilder(URI.create(properties.baseUrl().replaceAll("/+$", "") + "/v1/messages"))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("content-type", "application/json")
                .header("x-api-key", properties.apiKey())
                .header("anthropic-version", properties.anthropicVersion())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(http, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new ModelUnavailableException("the language model provider is unreachable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ModelUnavailableException("the call to the language model provider was interrupted", ex);
        }
        if (response.statusCode() / 100 != 2) {
            throw new ModelUnavailableException("the language model provider answered HTTP " + response.statusCode());
        }
        return parse(response.body());
    }

    String toJson(ModelExchange.Request request) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("model", properties.model());
        root.put("max_tokens", request.maxOutputTokens());
        root.put("system", request.system());
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolSpec spec : request.tools()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("name", spec.name());
            t.put("description", spec.description());
            t.put("input_schema", spec.inputSchema());
            tools.add(t);
        }
        if (!tools.isEmpty()) {
            root.put("tools", tools);
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ModelExchange.Message m : request.messages()) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", m.role() == ModelExchange.Message.Role.USER ? "user" : "assistant");
            List<Map<String, Object>> blocks = new ArrayList<>();
            if (!m.text().isBlank()) {
                blocks.add(Map.of("type", "text", "text", m.text()));
            }
            for (ModelExchange.ToolCall call : m.toolCalls()) {
                blocks.add(Map.of("type", "tool_use", "id", call.id(), "name", call.name(), "input", readObject(call.argumentsJson())));
            }
            for (ModelExchange.ToolResult r : m.toolResults()) {
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "tool_result");
                block.put("tool_use_id", r.toolCallId());
                block.put("content", r.content());
                if (r.error()) {
                    block.put("is_error", true);
                }
                blocks.add(block);
            }
            if (blocks.isEmpty()) {
                blocks.add(Map.of("type", "text", "text", "(vide)"));
            }
            msg.put("content", blocks);
            messages.add(msg);
        }
        root.put("messages", messages);
        return mapper.writeValueAsString(root);
    }

    private Object readObject(String json) {
        try {
            return mapper.readValue(json, Object.class);
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    ModelExchange.Reply parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (RuntimeException ex) {
            throw new ModelUnavailableException("the language model provider returned an unreadable answer", ex);
        }
        StringBuilder text = new StringBuilder();
        List<ModelExchange.ToolCall> calls = new ArrayList<>();
        for (JsonNode block : root.path("content")) {
            String type = block.path("type").asString("");
            if ("text".equals(type)) {
                text.append(block.path("text").asString(""));
            } else if ("tool_use".equals(type)) {
                calls.add(new ModelExchange.ToolCall(
                        block.path("id").asString(""), block.path("name").asString(""), block.path("input").toString()));
            }
        }
        JsonNode usage = root.path("usage");
        return new ModelExchange.Reply(text.toString(), calls, usage.path("input_tokens").asLong(0), usage.path("output_tokens").asLong(0));
    }
}
