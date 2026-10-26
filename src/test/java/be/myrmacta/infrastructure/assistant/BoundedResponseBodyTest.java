package be.myrmacta.infrastructure.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §106 (revue adversariale de #211, F1) — le délai couvre le corps ; la taille est bornée. */
class BoundedResponseBodyTest {

    private HttpServer server;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        // En-têtes tout de suite, puis un octet par seconde : le délai doit couvrir le corps.
        server.createContext("/drip", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (int i = 0; i < 30; i++) {
                    out.write('{');
                    out.flush();
                    Thread.sleep(1000);
                }
            } catch (InterruptedException | IOException ignored) {
                // client parti
            }
        });
        server.createContext("/big", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(new byte[64 * 1024]);
            } catch (IOException ignored) {
                // client parti
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path)).build();
    }

    @Test
    void aNormalAnswerIsReturnedWhole() throws IOException {
        var response = BoundedResponseBody.send(client, get("/ok"), Duration.ofSeconds(5), 1024);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
    }

    @Test
    void aDrippedBodyFailsWithinTheTimeout() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> BoundedResponseBody.send(client, get("/drip"), Duration.ofMillis(1500), 1024))
                .isInstanceOf(HttpTimeoutException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void anAnswerLargerThanTheBoundIsRefused() {
        assertThatThrownBy(() -> BoundedResponseBody.send(client, get("/big"), Duration.ofSeconds(5), 1024))
                .isInstanceOf(IOException.class)
                .hasRootCauseMessage("the provider answer is too large");
    }
}
