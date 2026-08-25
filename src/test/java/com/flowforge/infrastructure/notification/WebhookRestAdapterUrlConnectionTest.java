package com.flowforge.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.notification.model.WebhookDeliveryCommand;
import com.flowforge.infrastructure.config.NotificationProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises the production {@link HttpURLConnection} sender (not the unit-test mock),
 * including Host header, HTTP success, HTTP ≥ 400, and I/O failure wrapping.
 */
class WebhookRestAdapterUrlConnectionTest {

    @Test
    void send_http200_postsJsonWithHostHeader() throws Exception {
        AtomicReference<String> receivedHost = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            receivedHost.set(exchange.getRequestHeaders().getFirst("Host"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ok);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            WebhookRestAdapter adapter = adapterPinnedTo(port);
            adapter.send(command());
            assertThat(receivedHost.get()).startsWith("hooks.example.com");
            assertThat(receivedBody.get()).contains("\"type\":\"notification\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void send_http400_throwsIllegalStateException() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            WebhookRestAdapter adapter = adapterPinnedTo(port);
            assertThatThrownBy(() -> adapter.send(command()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("400");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void send_connectionRefused_wrapsIoException() {
        WebhookRestAdapter adapter = adapterPinnedToUri(URI.create("http://127.0.0.1:1/hook"));
        assertThatThrownBy(() -> adapter.send(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Webhook delivery failed")
                .hasCauseInstanceOf(IOException.class);
    }

    private static WebhookRestAdapter adapterPinnedTo(int port) {
        return adapterPinnedToUri(URI.create("http://127.0.0.1:" + port + "/hook"));
    }

    private static WebhookRestAdapter adapterPinnedToUri(URI pinned) {
        OutboundUrlGuard stub = new OutboundUrlGuard(false) {
            @Override
            public PinnedEndpoint resolveSafe(String url) {
                return new PinnedEndpoint(pinned, "hooks.example.com");
            }
        };
        return new WebhookRestAdapter(stub, JsonMapper.builder().build(), new NotificationProperties(null, null, null));
    }

    private static WebhookDeliveryCommand command() {
        return new WebhookDeliveryCommand(
                "https://hooks.example.com/hook",
                null,
                UUID.randomUUID(),
                Instant.parse("2026-08-07T12:00:00Z"),
                Map.of("k", "v"));
    }
}
