package be.myrmacta.infrastructure.assistant;

import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpClient;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpRequestBody;
import com.anthropic.core.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
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
        java.net.http.HttpResponse<byte[]> response;
        try {
            // TODO §106 (F1) : le délai couvre l'échange entier, corps compris (une passerelle ne peut plus le distiller).
            response = BoundedResponseBody.send(client, http.build(), timeout, MAX_RESPONSE_BYTES);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
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
                return new ByteArrayInputStream(response.body());
            }

            @Override
            public void close() {
                // corps déjà lu en entier : rien à libérer
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
}
