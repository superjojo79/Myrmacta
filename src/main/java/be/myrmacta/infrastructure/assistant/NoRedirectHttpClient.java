package be.myrmacta.infrastructure.assistant;

import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpClient;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpRequestBody;
import com.anthropic.core.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * D-ASSIST-PROVIDER — transport du SDK Anthropic (utilisé par le starter Spring AI) sur {@code java.net.http}, redirections
 * <b>jamais</b> suivies : le client OkHttp du starter les suit et réémettrait l'en-tête {@code x-api-key} vers l'hôte désigné
 * par un {@code Location}. Une réponse 3xx remonte telle quelle (le SDK la traite comme une erreur du fournisseur).
 */
final class NoRedirectHttpClient implements HttpClient {

    /** En-têtes que {@code java.net.http} pose lui-même et refuse qu'on lui fournisse. */
    private static final Set<String> RESTRICTED = Set.of("host", "content-length", "connection", "expect", "upgrade");

    /** Réponse bornée : une passerelle défaillante ne doit pas pouvoir saturer la mémoire. */
    static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private final java.net.http.HttpClient client;
    private final Duration timeout;

    NoRedirectHttpClient(Duration timeout) {
        this.timeout = timeout;
        this.client = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions options) {
        java.net.http.HttpRequest.Builder http = java.net.http.HttpRequest.newBuilder(URI.create(request.url())).timeout(timeout);
        for (String name : request.headers().names()) {
            if (!RESTRICTED.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                for (String value : request.headers().values(name)) {
                    http.header(name, value);
                }
            }
        }
        HttpRequestBody body = request.body();
        byte[] payload = new byte[0];
        if (body != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            body.writeTo(out);
            payload = out.toByteArray();
            if (body.contentType() != null) {
                http.setHeader("Content-Type", body.contentType());
            }
        }
        http.method(request.method().name(), body == null ? BodyPublishers.noBody() : BodyPublishers.ofByteArray(payload));
        java.net.http.HttpResponse<InputStream> response;
        try {
            // Délai global : celui de java.net.http ne couvre que l'attente des en-têtes (une passerelle pourrait distiller le corps).
            response = client.sendAsync(http.build(), BodyHandlers.ofInputStream())
                    .orTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .join();
        } catch (java.util.concurrent.CompletionException ex) {
            throw new UncheckedIOException(new IOException("the provider call failed", ex.getCause()));
        }
        Headers.Builder headers = Headers.builder();
        response.headers().map().forEach((name, values) -> headers.put(name, values));
        return new HttpResponse() {
            @Override
            public int statusCode() {
                return response.statusCode();
            }

            @Override
            public Headers headers() {
                return headers.build();
            }

            @Override
            public InputStream body() {
                return new BoundedInputStream(response.body(), MAX_RESPONSE_BYTES);
            }

            @Override
            public void close() {
                try {
                    response.body().close();
                } catch (IOException ignored) {
                    // rien à rattraper : la connexion est abandonnée
                }
            }
        };
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options) {
        return CompletableFuture.supplyAsync(() -> execute(request, options));
    }

    @Override
    public void close() {
        // le client java.net.http n'a pas de ressource à libérer explicitement (JDK 21)
    }

    /** Échoue au-delà de {@code limit} octets lus plutôt que de laisser le SDK tout mettre en mémoire. */
    private static final class BoundedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        BoundedInputStream(InputStream in, long limit) {
            this.in = in;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, (int) Math.min(len, remaining + 1));
            if (n > 0) {
                remaining -= n;
                if (remaining < 0) {
                    throw new IOException("the provider answer is too large");
                }
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
