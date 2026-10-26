package be.myrmacta.infrastructure.precheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openai.core.RequestOptions;
import com.openai.core.http.HttpMethod;
import com.openai.core.http.HttpRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** TODO §106 (revue adversariale de #211, F1) — un fournisseur qui distille sa réponse ne retient plus le thread. */
class OpenAiNoRedirectHttpClientTest {

    @Test
    void theTimeoutCoversTheBody() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
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
        server.start();
        try {
            var client = new OpenAiNoRedirectHttpClient(Duration.ofSeconds(2));
            long start = System.nanoTime();
            assertThatThrownBy(() -> client.execute(HttpRequest.builder().method(HttpMethod.POST)
                            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build(), RequestOptions.none()))
                    .isInstanceOf(UncheckedIOException.class)
                    .hasMessageContaining("did not answer within 2 s");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(6));
        } finally {
            server.stop(0);
        }
    }
}
