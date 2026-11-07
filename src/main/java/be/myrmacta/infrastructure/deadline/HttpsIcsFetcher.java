package be.myrmacta.infrastructure.deadline;

import be.myrmacta.domain.deadline.port.out.IcsCalendarParser;
import be.myrmacta.domain.deadline.port.out.IcsFetcher;
import be.myrmacta.infrastructure.connector.builtin.PinnedHttpClient;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Objects;
import javax.net.ssl.SSLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TODO §121 (D-CAL-ICS-URL) — téléchargement d'un calendrier ICS par URL. L'URL passe par {@link OutboundUrlGuard}
 * (https, jamais une adresse interne ou de métadonnées, adresse résolue et figée : §44) ; l'appel est un {@code GET}
 * sans en-tête d'authentification ni redirection, borné à 1 Mio et à 10 s au total. L'URL est un secret : aucun
 * message ne la reprend, au plus son hôte.
 */
@Component
class HttpsIcsFetcher implements IcsFetcher {

    private final OutboundUrlGuard guard;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration totalTimeout;

    @Autowired
    HttpsIcsFetcher(OutboundUrlGuard guard) {
        this(guard, Duration.ofSeconds(10), Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    HttpsIcsFetcher(OutboundUrlGuard guard, Duration connectTimeout, Duration readTimeout, Duration totalTimeout) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.totalTimeout = totalTimeout;
    }

    @Override
    public byte[] fetch(String url) {
        OutboundUrlGuard.PinnedEndpoint endpoint;
        try {
            endpoint = guard.resolveSafe(url);
        } catch (IllegalArgumentException refused) {
            throw new IllegalArgumentException("calendar URL is refused: only an https address on the public internet is accepted");
        }
        String host = endpoint.originalHost();
        PinnedHttpClient.Fetched fetched;
        try {
            fetched = PinnedHttpClient.get(url.strip(), endpoint.address(), connectTimeout, readTimeout, totalTimeout,
                    IcsCalendarParser.MAX_BYTES);
        } catch (IOException failure) {
            throw new IllegalArgumentException(describe(host, failure));
        }
        int status = fetched.status();
        if (status == 200) {
            return fetched.body();
        }
        if (status >= 300 && status < 400) {
            throw new IllegalArgumentException("calendar host " + host + " redirected (HTTP " + status + "): redirections are not followed");
        }
        throw new IllegalArgumentException("calendar host " + host + " answered HTTP " + status);
    }

    /** Une phrase par nature de panne, sans reprendre le message de la bibliothèque (il peut contenir l'URL). */
    private static String describe(String host, IOException failure) {
        if ("response too large".equals(failure.getMessage())) {
            return "calendar host " + host + " sent more than 1 MiB";
        }
        if (failure instanceof InterruptedIOException || "timed out".equals(failure.getMessage())) {
            return "calendar host " + host + " timed out";
        }
        if (failure instanceof SSLException) {
            return "calendar host " + host + " has a TLS certificate that is not trusted";
        }
        return "calendar host " + host + " cannot be reached";
    }
}
