package be.myrmacta.infrastructure.precheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.precheck.model.ModelExchange;
import be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckModelUnavailableException;
import be.myrmacta.domain.precheck.port.out.PrecheckModelPort;
import be.myrmacta.infrastructure.config.PrecheckProperties;
import be.myrmacta.support.InMemoryOpenAiCompatibleProvider;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** TODO §106 (lot A, C1, HC1) — appel au modèle de la pré-vérification, contre de faux fournisseurs. */
class SpringAiPrecheckModelTest {

    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"summary\":{\"type\":\"string\"}},"
            + "\"required\":[\"summary\"],\"additionalProperties\":false}";
    private static final ModelExchange.Request REQUEST =
            new ModelExchange.Request("Consignes", "<<<CHAMPS>>> commune = Schaerbeek", SCHEMA, 500);

    private final ObjectMapper mapper = new ObjectMapper();
    private InMemoryOpenAiCompatibleProvider provider;
    private HttpServer other;

    @AfterEach
    void stop() {
        if (provider != null) {
            provider.close();
        }
        if (other != null) {
            other.stop(0);
        }
    }

    private PrecheckModelPort openAi(String apiKey) {
        provider = provider == null ? new InMemoryOpenAiCompatibleProvider() : provider;
        return PrecheckModels.create(props(PrecheckProperties.OPENAI_COMPATIBLE, provider.baseUrl(), apiKey));
    }

    private static PrecheckProperties props(String kind, String baseUrl, String apiKey) {
        return new PrecheckProperties(true, kind, baseUrl, apiKey, "mistral-small", false, 30, 1_000_000, 2_000, 10, 30,
                30_000, 100_000, 20, "2023-06-01");
    }

    @Test
    void sendsOneCallWithoutToolsAndAStrictJsonSchema() {
        PrecheckModelPort model = openAi("secret-key");
        provider.respond("{\"summary\":\"Dossier complet\"}").usage(150, 40);

        ModelExchange.Reply reply = model.complete(REQUEST);

        assertThat(reply.json()).isEqualTo("{\"summary\":\"Dossier complet\"}");
        assertThat(reply.tokensIn()).isEqualTo(150);
        assertThat(reply.tokensOut()).isEqualTo(40);
        assertThat(reply.provider()).isEqualTo("openai-compatible");
        assertThat(reply.model()).isEqualTo("mistral-small");
        assertThat(provider.received()).hasSize(1);
        InMemoryOpenAiCompatibleProvider.Received sent = provider.received().getFirst();
        assertThat(sent.header("authorization")).isEqualTo("Bearer secret-key");
        assertThat(sent.header("user-agent")).isEqualTo("myrmacta-precheck");
        JsonNode body = mapper.readTree(sent.body());
        assertThat(body.path("model").asString()).isEqualTo("mistral-small");
        assertThat(body.has("tools")).isFalse();
        assertThat(body.path("temperature").asDouble()).isZero();
        assertThat(body.path("response_format").path("type").asString()).isEqualTo("json_schema");
        assertThat(body.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(body.path("messages").get(0).path("role").asString()).isEqualTo("system");
        assertThat(body.path("messages").get(1).path("content").toString()).contains("commune = Schaerbeek");
    }

    @Test
    void withoutKeyAPlaceholderTokenIsSent() {
        PrecheckModelPort model = openAi("");
        provider.respond("{\"summary\":\"\"}");

        model.complete(REQUEST);

        assertThat(provider.received().getFirst().header("authorization")).isEqualTo("Bearer unused");
    }

    @Test
    void aProviderErrorIsNeitherRelayedNorRetried() {
        PrecheckModelPort model = openAi("secret-key");
        provider.fail(500, "{\"error\":{\"message\":\"échec sur commune = Schaerbeek\"}}");

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SpringAiPrecheckModel.class);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            assertThatThrownBy(() -> model.complete(REQUEST))
                    .isInstanceOf(PrecheckModelUnavailableException.class)
                    .hasMessageNotContaining("Schaerbeek")
                    .hasMessageContaining("unavailable");
        } finally {
            logger.detachAppender(logs);
        }
        assertThat(provider.received()).hasSize(1);
        // F8 : la cause est journalisée (type, statut HTTP), jamais le message du fournisseur.
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("HTTP 500").doesNotContain("Schaerbeek");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    /** F9 — sans jetons déclarés par le fournisseur, une estimation garde le plafond du jour efficace. */
    @Test
    void missingUsageIsEstimatedSoTheDailyBudgetStillCounts() {
        PrecheckModelPort model = openAi("secret-key");
        provider.respond("{\"summary\":\"Dossier complet\"}").usage(0, 0);

        ModelExchange.Reply reply = model.complete(REQUEST);

        assertThat(reply.tokensIn()).isPositive();
        assertThat(reply.tokensOut()).isPositive();
    }

    @Test
    void aRedirectIsNeverFollowed_theKeyNeverTravelsElsewhere() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        other.createContext("/", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        other.start();
        PrecheckModelPort model = openAi("secret-key");
        provider.redirectTo("http://127.0.0.1:" + other.getAddress().getPort() + "/steal");

        assertThatThrownBy(() -> model.complete(REQUEST)).isInstanceOf(PrecheckModelUnavailableException.class);
        assertThat(hits.get()).isZero();
    }

    @Test
    void anthropicGetsTheSchemaInItsInstructionsAndNoTools() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        other.createContext("/v1/messages", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = ("{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude\",\"content\":"
                    + "[{\"type\":\"text\",\"text\":\"{\\\"summary\\\":\\\"ok\\\"}\"}],\"stop_reason\":\"end_turn\","
                    + "\"stop_sequence\":null,\"usage\":{\"input_tokens\":12,\"output_tokens\":4}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        other.start();
        PrecheckModelPort model = PrecheckModels.create(props(PrecheckProperties.ANTHROPIC,
                "http://127.0.0.1:" + other.getAddress().getPort(), "anthropic-key"));

        ModelExchange.Reply reply = model.complete(REQUEST);

        assertThat(reply.json()).isEqualTo("{\"summary\":\"ok\"}");
        assertThat(reply.tokensIn()).isEqualTo(12);
        JsonNode sent = mapper.readTree(body.get());
        assertThat(sent.has("tools")).isFalse();
        assertThat(sent.path("system").toString()).contains("additionalProperties");
        assertThat(sent.path("max_tokens").asInt()).isEqualTo(500);
    }
}
