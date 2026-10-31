package be.myrmacta.infrastructure.connector.builtin;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.Timeout;

/**
 * TODO §116 (D-HTTP-PATCH) — client des appels {@code http-rest} et des jetons OAuth2 qui passent par le même
 * {@link HttpRestConnector.HttpSender}.
 *
 * <p>L'URL garde le nom d'hôte d'origine : le SNI et la vérification du certificat sont ceux du client, sans vérificateur
 * réécrit. Le {@link DnsResolver} ne renvoie que l'adresse déjà validée par {@code OutboundUrlGuard} (D-44) : pas de
 * second DNS, donc pas de rebinding entre le contrôle et la connexion. Les redirections sont refusées : une cible 3xx
 * ne repasse pas par la garde. Aucun proxy : un proxy résoudrait le nom de son côté (D-44). Le délai borne la connexion
 * et la lecture du corps.
 */
final class PinnedHttpClient {

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private PinnedHttpClient() {}

    static HttpRestConnector.HttpSender sender() {
        return sender(null);
    }

    /** {@code sslContext} nul : trust store et vérification du nom de la JVM. */
    static HttpRestConnector.HttpSender sender(SSLContext sslContext) {
        return request -> send(request, sslContext);
    }

    private static HttpRestConnector.HttpCallResponse send(
            HttpRestConnector.HttpCallRequest request, SSLContext sslContext) throws IOException {
        InetAddress pin = request.pinnedAddress();
        if (pin == null) {
            throw new IOException("http-rest call has no pinned address");
        }
        String expectedHost = hostOf(request.url());
        DnsResolver resolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                if (!expectedHost.equalsIgnoreCase(host)) {
                    throw new UnknownHostException("host is not the pinned target");
                }
                return new InetAddress[] {pin};
            }

            @Override
            public String resolveCanonicalHostname(String host) throws UnknownHostException {
                resolve(host);
                return host;
            }
        };
        Timeout connect = Timeout.of(request.connectTimeout());
        Timeout read = Timeout.of(request.requestTimeout());
        PoolingHttpClientConnectionManagerBuilder connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(resolver)
                .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(read).build())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(connect)
                        .setSocketTimeout(read)
                        .build());
        if (sslContext != null) {
            connections.setTlsSocketStrategy(ClientTlsStrategyBuilder.create()
                    .setSslContext(sslContext)
                    .buildClassic());
        }
        ClassicHttpRequest httpRequest = requestOf(request);
        try (CloseableHttpClient client = HttpClients.custom()
                        .setConnectionManager(connections.build())
                        // D-44. HttpClient 5 ne consulte le ProxySelector de la JVM que via
                        // useSystemProperties() ou setProxySelector. Le planificateur direct le fixe :
                        // un proxy referait le DNS et sortirait de l'adresse figée.
                        .setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE))
                        .disableRedirectHandling()
                        .disableAutomaticRetries()
                        .disableCookieManagement()
                        .setDefaultRequestConfig(RequestConfig.custom()
                                .setResponseTimeout(read)
                                .setConnectionRequestTimeout(connect)
                                .build())
                        .build();
                ClassicHttpResponse response = client.executeOpen(null, httpRequest, null)) {
            int status = response.getCode();
            InputStream stream = response.getEntity() == null ? null : response.getEntity().getContent();
            byte[] bytes = readBounded(stream, request.maxResponseBytes());
            return new HttpRestConnector.HttpCallResponse(status, new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static ClassicHttpRequest requestOf(HttpRestConnector.HttpCallRequest request) {
        ClassicRequestBuilder builder = ClassicRequestBuilder.create(request.method()).setUri(request.url());
        // Le client pose Host depuis l'URL (nom d'origine, port compris). L'en-tête recopié par le connecteur
        // n'a pas le port : le laisser écraserait celui du client.
        request.headers().forEach((name, value) -> {
            if (!"host".equalsIgnoreCase(name)) {
                builder.setHeader(name, value);
            }
        });
        if (BODY_METHODS.contains(request.method())) {
            byte[] body = request.body() == null ? new byte[0] : request.body();
            builder.setEntity(new ByteArrayEntity(body, null));
        }
        return builder.build();
    }

    private static String hostOf(String url) throws IOException {
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException ex) {
            throw new IOException("http-rest URL is malformed", ex);
        }
        if (host == null || host.isBlank()) {
            throw new IOException("http-rest URL has no host");
        }
        return host;
    }

    private static byte[] readBounded(InputStream stream, long maxResponseBytes) throws IOException {
        if (stream == null) {
            return new byte[0];
        }
        int limit = Math.toIntExact(Math.min(maxResponseBytes, Integer.MAX_VALUE - 1) + 1);
        byte[] bytes = stream.readNBytes(limit);
        if (bytes.length > maxResponseBytes) {
            throw new IOException("http-rest response exceeds maxResponseBytes");
        }
        return bytes;
    }
}
