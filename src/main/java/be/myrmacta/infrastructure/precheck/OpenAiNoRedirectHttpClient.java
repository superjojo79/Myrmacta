package be.myrmacta.infrastructure.precheck;

import be.myrmacta.infrastructure.assistant.BoundedResponseBody;
import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpRequestBody;
import com.openai.core.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * TODO §106 (lot A, HC1) — transport du SDK OpenAI sur {@code java.net.http}, redirections <b>jamais</b> suivies : le
 * client OkHttp par défaut les suit et réémettrait l'en-tête {@code Authorization} vers l'hôte désigné par un
 * {@code Location}. Même principe que le transport Anthropic de l'assistant ({@code NoRedirectHttpClient}) : réponse
 * bornée, délai global, une réponse 3xx remonte telle quelle (le SDK la traite comme une erreur du fournisseur).
 */
final class OpenAiNoRedirectHttpClient implements HttpClient {

    /** En-têtes que {@code java.net.http} pose lui-même et refuse qu'on lui fournisse. */
    private static final Set<String> RESTRICTED = Set.of("host", "content-length", "connection", "expect", "upgrade");

    /** Réponse bornée : une passerelle défaillante ne doit pas pouvoir saturer la mémoire. */
    static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private final java.net.http.HttpClient client;
    private final Duration timeout;

    OpenAiNoRedirectHttpClient(Duration timeout) {
        this.timeout = timeout;
        this.client = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions options) {
        java.net.http.HttpRequest.Builder http = java.net.http.HttpRequest.newBuilder(URI.create(request.url()))
                .timeout(timeout);
        for (String name : request.headers().names()) {
            if (!RESTRICTED.contains(name.toLowerCase(Locale.ROOT))) {
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
        response.headers().map().forEach(headers::put);
        Headers built = headers.build();
        return new HttpResponse() {
            @Override
            public int statusCode() {
                return response.statusCode();
            }

            @Override
            public Headers headers() {
                return built;
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
        // le client java.net.http n'a pas de ressource à libérer explicitement
    }
}
