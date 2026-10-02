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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §86 (LC3, D-ASSIST-PROVIDER) — adaptateur Spring AI : requête, réponse, erreurs, secret, redirections. Serveur local qui joue
 * l'API Messages d'Anthropic, aucun fournisseur réel.
 */
class SpringAiLanguageModelTest {

    private static final String KEY = "sk-test-SECRET-KEY";
    private final JsonMapper mapper = JsonMapper.builder().build();
    private HttpServer server;
    private HttpServer elsewhere;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<com.sun.net.httpserver.Headers> headers = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (elsewhere != null) {
            elsewhere.stop(0);
        }
    }

    private AssistantProperties props(String baseUrl) {
        return new AssistantProperties(true, baseUrl, KEY, "model-x", "2023-06-01", 30, 30, 12, 60, 400_000, 3_000_000, 4096);
    }

    private SpringAiLanguageModel start(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            headers.set(exchange.getRequestHeaders());
            exchange.getResponseHeaders().add("content-type", "application/json");
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return new SpringAiLanguageModel(AnthropicChatModels.create(props("http://127.0.0.1:" + server.getAddress().getPort())), "model-x", mapper);
    }

    private static String message(String contentBlocks) {
        return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"model-x\",\"content\":[" + contentBlocks
                + "],\"stop_reason\":\"end_turn\",\"stop_sequence\":null,\"usage\":{\"input_tokens\":11,\"output_tokens\":5}}";
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
        var model = start(200, message("{\"type\":\"text\",\"text\":\"Salut\"}"));

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
        assertThat(sent.path("system").toString()).contains("SYS");
        assertThat(sent.path("tools").get(0).path("name").asString()).isEqualTo("define_dossier_type");
        assertThat(sent.path("tools").get(0).path("input_schema").path("type").asString()).isEqualTo("object");
        JsonNode messages = sent.path("messages");
        assertThat(messages.get(1).path("role").asString()).isEqualTo("assistant");
        assertThat(messages.get(1).path("content").toString()).contains("\"tool_use\"").contains("\"tu_1\"").contains("\"code\":\"A\"");
        assertThat(messages.get(2).path("role").asString()).isEqualTo("user");
        assertThat(messages.get(2).path("content").toString()).contains("\"tool_result\"").contains("\"tool_use_id\":\"tu_1\"");
    }

    @Test
    void readsToolUseBlocksAsToolCalls_withoutExecutingThem() throws Exception {
        var model = start(200, message("{\"type\":\"text\",\"text\":\"Je crée\"},{\"type\":\"tool_use\",\"id\":\"tu_9\","
                + "\"name\":\"deploy_workflow\",\"input\":{\"key\":\"k\"}}"));
        var reply = model.complete(request());
        assertThat(reply.text()).isEqualTo("Je crée");
        assertThat(reply.toolCalls()).hasSize(1);
        assertThat(reply.toolCalls().getFirst().id()).isEqualTo("tu_9");
        assertThat(reply.toolCalls().getFirst().name()).isEqualTo("deploy_workflow");
        assertThat(mapper.readTree(reply.toolCalls().getFirst().argumentsJson()).path("key").asString()).isEqualTo("k");
    }

    @Test
    void aToolErrorIsMarkedForTheModel() throws Exception {
        var model = start(200, message("{\"type\":\"text\",\"text\":\"ok\"}"));
        model.complete(new ModelExchange.Request("S", List.of(
                ModelExchange.Message.user("x"),
                ModelExchange.Message.assistant("", List.of(new ModelExchange.ToolCall("tu_1", "t", "{}"))),
                ModelExchange.Message.toolResults(List.of(new ModelExchange.ToolResult("tu_1", "boom", true)))),
                List.of(), 100));
        assertThat(body.get()).contains("ERROR: boom");
    }

    @Test
    void aProviderErrorNeverLeaksItsBodyNorTheKey() throws Exception {
        var model = start(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key " + KEY + "\"}}");
        assertThatThrownBy(() -> model.complete(request()))
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("invalid");
    }

    @Test
    void anUnreadableAnswerIsAnUnavailableModel() throws Exception {
        var model = start(200, "not json");
        assertThatThrownBy(() -> model.complete(request())).isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void aTransientProviderErrorIsNotRetried_everyCallCountsInTheTokenCaps() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(529, -1);
            exchange.close();
        });
        server.start();
        var model = new SpringAiLanguageModel(AnthropicChatModels.create(props("http://127.0.0.1:" + server.getAddress().getPort())), "model-x", mapper);
        assertThatThrownBy(() -> model.complete(request())).isInstanceOf(ModelUnavailableException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void aRedirectIsNeverFollowed_theKeyNeverTravelsElsewhere() throws Exception {
        AtomicInteger hitElsewhere = new AtomicInteger();
        AtomicReference<String> keySeenElsewhere = new AtomicReference<>();
        elsewhere = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        elsewhere.createContext("/", exchange -> {
            hitElsewhere.incrementAndGet();
            keySeenElsewhere.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        elsewhere.start();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + elsewhere.getAddress().getPort() + "/elsewhere");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.start();
        var model = new SpringAiLanguageModel(AnthropicChatModels.create(props("http://127.0.0.1:" + server.getAddress().getPort())), "model-x", mapper);
        assertThatThrownBy(() -> model.complete(request())).isInstanceOf(ModelUnavailableException.class);
        assertThat(hitElsewhere.get()).as("redirect target must never be contacted").isZero();
        assertThat(keySeenElsewhere.get()).isNull();
    }

    @Test
    void anUnusableToolUseIdIsReplacedByAUniqueOne() throws Exception {
        var model = start(200, message("{\"type\":\"tool_use\",\"id\":\"x\",\"name\":\"a\",\"input\":{}},"
                + "{\"type\":\"tool_use\",\"id\":\"x\",\"name\":\"b\",\"input\":{}}"));
        var ids = model.complete(request()).toolCalls().stream().map(c -> c.id()).toList();
        assertThat(ids).hasSize(2).doesNotContain("").doesNotHaveDuplicates();
    }

    @Test
    void theKeyIsNeverInThePropertiesToString_andEnablingNeedsAKeyAndHttps() {
        var props = new AssistantProperties(true, "https://api.anthropic.com", KEY, "m", "v", 30, 30, 12, 60, 1, 1, 1);
        assertThat(props.toString()).doesNotContain(KEY);
        assertThatThrownBy(() -> new AssistantProperties(true, "https://api.anthropic.com", " ", "m", "v", 30, 30, 12, 60, 1, 1, 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("api-key");
        assertThatThrownBy(() -> new AssistantProperties(true, "http://evil.example", KEY, "m", "v", 30, 30, 12, 60, 1, 1, 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("https");
        new AssistantProperties(false, "http://whatever", "", "m", "v", 30, 30, 12, 60, 1, 1, 1);
    }

    @Test
    void anOversizedAnswerIsRefusedRatherThanBufferedWithoutLimit() throws Exception {
        var model = start(200, message("{\"type\":\"text\",\"text\":\"" + "x".repeat(NoRedirectHttpClient.MAX_RESPONSE_BYTES + 10) + "\"}"));
        assertThatThrownBy(() -> model.complete(request())).isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void anEmptyAnswerIsNeverCountedAsZeroTokens() throws Exception {
        var model = start(200, message(""));
        var reply = model.complete(request());
        assertThat(reply.text()).isEmpty();
        assertThat(reply.outputTokens()).isPositive();
    }

    @Test
    void aBlankAssistantTextBesideToolCallsIsNotSent() throws Exception {
        var model = start(200, message("{\"type\":\"text\",\"text\":\"ok\"}"));
        model.complete(new ModelExchange.Request("S", List.of(
                ModelExchange.Message.user("x"),
                ModelExchange.Message.assistant("\n", List.of(new ModelExchange.ToolCall("tu_1", "t", "{}"))),
                ModelExchange.Message.toolResults(List.of(new ModelExchange.ToolResult("tu_1", "r", false)))),
                List.of(), 100));
        assertThat(body.get()).doesNotContain("\"text\":\"\\n\"");
    }
}
