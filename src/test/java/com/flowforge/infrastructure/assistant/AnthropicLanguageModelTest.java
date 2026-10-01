package com.flowforge.infrastructure.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.assistant.model.AssistantExceptions.ModelUnavailableException;
import com.flowforge.domain.assistant.model.ModelExchange;
import com.flowforge.domain.assistant.model.ToolSpec;
import com.flowforge.infrastructure.config.AssistantProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** TODO §86 (LC3) — adaptateur Messages API : requête, réponse, erreurs, secret. Serveur local, aucun fournisseur réel. */
class AnthropicLanguageModelTest {

    private static final String KEY = "sk-test-SECRET-KEY";
    private final JsonMapper mapper = JsonMapper.builder().build();
    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<com.sun.net.httpserver.Headers> headers = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AnthropicLanguageModel start(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            headers.set(exchange.getRequestHeaders());
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        var props = new AssistantProperties(true, "http://127.0.0.1:" + server.getAddress().getPort(), KEY, "model-x", "2023-06-01",
                30, 30, 12, 60, 400_000, 3_000_000, 4096);
        return new AnthropicLanguageModel(props, mapper);
    }

    private static ModelExchange.Request request() {
        return new ModelExchange.Request("SYS", List.of(
                ModelExchange.Message.user("bonjour"),
                ModelExchange.Message.assistant("", List.of(new ModelExchange.ToolCall("tu_1", "define_dossier_type", "{\"code\":\"A\"}"))),
                ModelExchange.Message.toolResults(List.of(new ModelExchange.ToolResult("tu_1", "{\"ok\":true}", false)))),
                List.of(new ToolSpec("define_dossier_type", "desc", Map.of("type", "object"))), 777);
    }

    @Test
    void sendsTheKeyOnlyInTheHeaderAndMapsMessagesToolsAndResults() throws Exception {
        var model = start(200, "{\"content\":[{\"type\":\"text\",\"text\":\"Salut\"}],\"usage\":{\"input_tokens\":11,\"output_tokens\":5}}");

        var reply = model.complete(request());

        assertThat(reply.text()).isEqualTo("Salut");
        assertThat(reply.inputTokens()).isEqualTo(11);
        assertThat(reply.outputTokens()).isEqualTo(5);
        assertThat(headers.get().getFirst("x-api-key")).isEqualTo(KEY);
        assertThat(headers.get().getFirst("anthropic-version")).isEqualTo("2023-06-01");
        assertThat(body.get()).doesNotContain(KEY);
        JsonNode sent = mapper.readTree(body.get());
        assertThat(sent.path("model").asString()).isEqualTo("model-x");
        assertThat(sent.path("max_tokens").asInt()).isEqualTo(777);
        assertThat(sent.path("system").asString()).isEqualTo("SYS");
        assertThat(sent.path("tools").get(0).path("name").asString()).isEqualTo("define_dossier_type");
        assertThat(sent.path("tools").get(0).path("input_schema").path("type").asString()).isEqualTo("object");
        JsonNode messages = sent.path("messages");
        assertThat(messages.get(1).path("role").asString()).isEqualTo("assistant");
        assertThat(messages.get(1).path("content").get(0).path("type").asString()).isEqualTo("tool_use");
        assertThat(messages.get(1).path("content").get(0).path("input").path("code").asString()).isEqualTo("A");
        assertThat(messages.get(2).path("role").asString()).isEqualTo("user");
        assertThat(messages.get(2).path("content").get(0).path("type").asString()).isEqualTo("tool_result");
        assertThat(messages.get(2).path("content").get(0).path("tool_use_id").asString()).isEqualTo("tu_1");
    }

    @Test
    void readsToolUseBlocksAsToolCalls() throws Exception {
        var model = start(200, "{\"content\":[{\"type\":\"text\",\"text\":\"Je crée\"},{\"type\":\"tool_use\",\"id\":\"tu_9\","
                + "\"name\":\"deploy_workflow\",\"input\":{\"key\":\"k\"}}],\"usage\":{\"input_tokens\":1,\"output_tokens\":2}}");
        var reply = model.complete(request());
        assertThat(reply.toolCalls()).hasSize(1);
        assertThat(reply.toolCalls().getFirst().id()).isEqualTo("tu_9");
        assertThat(reply.toolCalls().getFirst().name()).isEqualTo("deploy_workflow");
        assertThat(mapper.readTree(reply.toolCalls().getFirst().argumentsJson()).path("key").asString()).isEqualTo("k");
    }

    @Test
    void aProviderErrorNeverLeaksItsBodyNorTheKey() throws Exception {
        var model = start(401, "{\"error\":{\"message\":\"invalid x-api-key " + KEY + "\"}}");
        assertThatThrownBy(() -> model.complete(request()))
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageContaining("401")
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("invalid");
    }

    @Test
    void anUnreadableAnswerIsAnUnavailableModel() throws Exception {
        var model = start(200, "not json");
        assertThatThrownBy(() -> model.complete(request())).isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void aRedirectIsNeverFollowed_theKeyNeverTravelsElsewhere() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:1/elsewhere");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.start();
        var props = new AssistantProperties(true, "http://127.0.0.1:" + server.getAddress().getPort(), KEY, "m", "2023-06-01",
                30, 30, 12, 60, 400_000, 3_000_000, 4096);
        assertThatThrownBy(() -> new AnthropicLanguageModel(props, mapper).complete(request()))
                .isInstanceOf(ModelUnavailableException.class).hasMessageContaining("307");
    }

    @Test
    void theKeyIsNeverInThePropertiesToString_andEnablingNeedsAKeyAndHttps() {
        var props = new AssistantProperties(true, "https://api.anthropic.com", KEY, "m", "v", 30, 30, 12, 60, 1, 1, 1);
        assertThat(props.toString()).doesNotContain(KEY);
        assertThatThrownBy(() -> new AssistantProperties(true, "https://api.anthropic.com", " ", "m", "v", 30, 30, 12, 60, 1, 1, 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("api-key");
        assertThatThrownBy(() -> new AssistantProperties(true, "http://evil.example", KEY, "m", "v", 30, 30, 12, 60, 1, 1, 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("https");
        // désactivé : aucune exigence
        new AssistantProperties(false, "http://whatever", "", "m", "v", 30, 30, 12, 60, 1, 1, 1);
    }
}
