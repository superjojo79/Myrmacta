package be.myrmacta.infrastructure.signature.eid;

import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.Timeout;

/**
 * Transport de révocation de production (TODO §138, D-EID-REVOCATION) : {@code http} ou {@code https} seulement,
 * aucune redirection ni nouvel essai, aucun proxy, délai total qui couvre la connexion <b>et</b> la lecture du corps
 * (précédent du dépôt), corps borné en taille, statut 200 exigé.
 *
 * <p>Adresse : le nom est résolu puis chaque adresse contrôlée par {@link OutboundUrlGuard} (boucle locale, plages
 * privées, lien local, multicast, métadonnées refusés), et la connexion part vers l'adresse ainsi figée (D-44 : pas de
 * second DNS, pas de rebinding). La garde ne sait contrôler qu'une URL {@code https} : l'hôte et le port lui sont
 * soumis sous cette forme, quel que soit le schéma réel (OCSP et CRL sont le plus souvent en {@code http}).
 *
 * <p>Client HTTP 5 d'Apache plutôt que {@code java.net.http.HttpClient} : ce dernier n'offre ni résolveur de noms
 * propre ni en-tête {@code Host} modifiable sans propriété système globale, donc pas de connexion à l'adresse figée.
 * Même motif que {@code PinnedHttpClient} (D-HTTP-PATCH).
 */
public final class PinnedRevocationTransport implements RevocationTransport {

    /** Adresse figée d'un hôte, ou refus ({@link IOException}). */
    @FunctionalInterface
    interface AddressPolicy {
        InetAddress pin(URI uri) throws IOException;
    }

    static final int DEFAULT_MAX_OCSP_BYTES = 64 * 1024;
    static final int DEFAULT_MAX_CRL_BYTES = 10 * 1024 * 1024;

    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "eid-revocation-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private final AddressPolicy addresses;
    private final Duration connectTimeout;
    private final Duration totalTimeout;
    private final int maxOcspBytes;
    private final int maxCrlBytes;

    /** Garde publique (aucune boucle locale), 5 s de connexion, 10 s au total, OCSP 64 Kio, CRL 10 Mio. */
    public PinnedRevocationTransport() {
        this(guarded(new OutboundUrlGuard(false)), Duration.ofSeconds(5), Duration.ofSeconds(10),
                DEFAULT_MAX_OCSP_BYTES, DEFAULT_MAX_CRL_BYTES);
    }

    /**
     * Réglages de l'exploitant ({@code app.signature.eid.revocation.*}) : délai total (connexion comprise, borné à
     * 5 s pour la seule connexion) et taille maximale d'une réponse OCSP ou d'une CRL.
     */
    public PinnedRevocationTransport(Duration timeout, long maxResponseBytes) {
        this(guarded(new OutboundUrlGuard(false)), shorter(Duration.ofSeconds(5), timeout), timeout,
                clamp(maxResponseBytes), clamp(maxResponseBytes));
    }

    /**
     * Client de l'autorité d'horodatage (D-EID-TIMESTAMP) : même transport borné, mais l'adresse vient de
     * l'exploitant, pas d'un certificat, et une TSA interne est légitime : le nom est résolu et figé sans refus des
     * plages privées.
     */
    static PinnedRevocationTransport forOperatorUrl(Duration timeout, long maxResponseBytes) {
        AddressPolicy resolveOnly = uri -> {
            try {
                return InetAddress.getByName(uri.getHost());
            } catch (UnknownHostException ex) {
                throw new IOException("host cannot be resolved");
            }
        };
        return new PinnedRevocationTransport(resolveOnly, shorter(Duration.ofSeconds(5), timeout), timeout,
                clamp(maxResponseBytes), clamp(maxResponseBytes));
    }

    private static Duration shorter(Duration a, Duration b) {
        Objects.requireNonNull(b, "timeout");
        if (b.isNegative() || b.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return a.compareTo(b) < 0 ? a : b;
    }

    private static int clamp(long bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("maximum response size must be positive");
        }
        return (int) Math.min(bytes, Integer.MAX_VALUE - 1L);
    }

    /** Taille maximale d'une réponse (OCSP ou CRL selon le constructeur). */
    public int maxOcspBytes() {
        return maxOcspBytes;
    }

    /** Délai total d'un appel, corps compris. */
    public Duration totalTimeout() {
        return totalTimeout;
    }

    /** POST borné générique (requête d'horodatage). */
    byte[] post(URI uri, byte[] body, String contentType, String accept) throws IOException {
        ClassicHttpRequest post = ClassicRequestBuilder.post(checked(uri))
                .setEntity(new ByteArrayEntity(body, ContentType.create(contentType)))
                .setHeader("Accept", accept)
                .build();
        return exchange(uri, post, maxOcspBytes);
    }

    PinnedRevocationTransport(AddressPolicy addresses, Duration connectTimeout, Duration totalTimeout, int maxOcspBytes,
            int maxCrlBytes) {
        this.addresses = Objects.requireNonNull(addresses, "addresses");
        this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout");
        this.totalTimeout = Objects.requireNonNull(totalTimeout, "totalTimeout");
        this.maxOcspBytes = maxOcspBytes;
        this.maxCrlBytes = maxCrlBytes;
    }

    /** Contrôle par {@link OutboundUrlGuard} : résolution DNS puis refus des adresses non publiques. */
    static AddressPolicy guarded(OutboundUrlGuard guard) {
        return uri -> {
            String authority = uri.getRawAuthority();
            try {
                return guard.resolveSafe("https://" + authority + "/").address();
            } catch (IllegalArgumentException refused) {
                throw new IOException("revocation address is not allowed");
            }
        };
    }

    @Override
    public byte[] postOcsp(URI responder, byte[] request) throws IOException {
        ClassicHttpRequest post = ClassicRequestBuilder.post(checked(responder))
                .setEntity(new ByteArrayEntity(request, ContentType.create("application/ocsp-request")))
                .setHeader("Accept", "application/ocsp-response")
                .build();
        return exchange(responder, post, maxOcspBytes);
    }

    @Override
    public byte[] getCrl(URI distributionPoint) throws IOException {
        return exchange(distributionPoint, ClassicRequestBuilder.get(checked(distributionPoint)).build(), maxCrlBytes);
    }

    private static URI checked(URI uri) throws IOException {
        String scheme = uri == null || uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IOException("revocation address must use http or https");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw new IOException("revocation address is malformed");
        }
        return uri;
    }

    private byte[] exchange(URI uri, ClassicHttpRequest request, int maxBytes) throws IOException {
        InetAddress pin = addresses.pin(uri);
        String host = uri.getHost();
        try (CloseableHttpClient client = client(pin, host)) {
            // Le délai de lecture ne borne que l'attente d'un octet : le chien de garde coupe tout à l'échéance.
            AtomicBoolean expired = new AtomicBoolean();
            ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
                expired.set(true);
                client.close(CloseMode.IMMEDIATE);
            }, totalTimeout.toMillis(), TimeUnit.MILLISECONDS);
            try {
                return read(client, request, maxBytes);
            } catch (IOException | RuntimeException failure) {
                if (expired.get()) {
                    throw new IOException("outbound call timed out");
                }
                throw failure instanceof IOException io ? io : new IOException("outbound call failed", failure);
            } finally {
                watchdog.cancel(false);
            }
        }
    }

    private static byte[] read(CloseableHttpClient client, ClassicHttpRequest request, int maxBytes) throws IOException {
        try (ClassicHttpResponse response = client.executeOpen(null, request, null)) {
            int status = response.getCode();
            if (status >= 300 && status < 400) {
                abort(response);
                throw new IOException("server redirected: redirections are not followed");
            }
            if (status != 200 || response.getEntity() == null) {
                abort(response);
                throw new IOException("server answered HTTP " + status);
            }
            if (response.getEntity().getContentLength() > maxBytes) {
                abort(response);
                throw new IOException("answer is too large");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            try (InputStream stream = response.getEntity().getContent()) {
                int read;
                while ((read = stream.read(chunk)) != -1) {
                    out.write(chunk, 0, read);
                    if (out.size() > maxBytes) {
                        abort(response);
                        throw new IOException("answer is too large");
                    }
                }
            }
            return out.toByteArray();
        }
    }

    /** Coupe la connexion sans lire le reste du corps (un serveur lent ne retient pas l'appel). */
    private static void abort(ClassicHttpResponse response) throws IOException {
        if (response instanceof ModalCloseable modal) {
            modal.close(CloseMode.IMMEDIATE);
        } else {
            response.close();
        }
    }

    /** Résolveur figé sur {@code pin} pour {@code expectedHost}, sans redirection, nouvel essai, cookie ni proxy. */
    private CloseableHttpClient client(InetAddress pin, String expectedHost) {
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
        Timeout connect = Timeout.of(connectTimeout);
        Timeout read = Timeout.of(totalTimeout);
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(resolver)
                        .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(read).build())
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(connect)
                                .setSocketTimeout(read)
                                .build())
                        .build())
                .setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE))
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(read)
                        .setConnectionRequestTimeout(connect)
                        .build())
                .build();
    }
}
