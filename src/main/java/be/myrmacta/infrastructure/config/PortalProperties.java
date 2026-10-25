package be.myrmacta.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TODO §68 (portail-externe). Opt-in (D-PORTAL-ACTIVATION) : désactivé, aucune route publique
 * n'existe et un BPMN à tâche externe est refusé au déploiement.
 *
 * <p>{@code publicBaseUrl} est <strong>obligatoire</strong> dès que le portail est activé
 * (D-PORTAL-PUBLIC-URL, patron fail-fast de TODO §43) : derrière le reverse-proxy (routage par
 * chemin, décision n° 14), le serveur ne connaît pas son URL publique, et la reconstruire depuis
 * l'en-tête {@code Host} laisserait un client choisir le domaine écrit dans l'e-mail
 * d'invitation.
 */
@ConfigurationProperties(prefix = "app.portal")
public record PortalProperties(
        boolean enabled,
        String publicBaseUrl,
        Duration defaultLinkTtl,
        Duration maxLinkTtl,
        Send send,
        Expiry expiry,
        RateLimit rateLimit,
        long maxBodyBytes) {

    public PortalProperties {
        if (defaultLinkTtl == null) {
            defaultLinkTtl = Duration.ofDays(7);
        }
        if (maxLinkTtl == null) {
            maxLinkTtl = Duration.ofDays(30);
        }
        if (send == null) {
            send = new Send(null, 0, 0);
        }
        if (expiry == null) {
            expiry = new Expiry(null, 0);
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(0);
        }
        if (maxBodyBytes <= 0) {
            maxBodyBytes = 64 * 1024;
        }
        if (defaultLinkTtl.toDays() < 1 || maxLinkTtl.toDays() < 1) {
            throw new IllegalStateException("app.portal link TTLs must be at least one day");
        }
        if (defaultLinkTtl.compareTo(maxLinkTtl) > 0) {
            throw new IllegalStateException("app.portal.default-link-ttl must not exceed app.portal.max-link-ttl");
        }
        if (publicBaseUrl != null) {
            publicBaseUrl = publicBaseUrl.trim();
            while (publicBaseUrl.endsWith("/")) {
                publicBaseUrl = publicBaseUrl.substring(0, publicBaseUrl.length() - 1);
            }
        }
        if (enabled) {
            requireValidBaseUrl(publicBaseUrl);
        }
    }

    public int defaultLinkTtlDays() {
        return (int) defaultLinkTtl.toDays();
    }

    public int maxLinkTtlDays() {
        return (int) maxLinkTtl.toDays();
    }

    private static void requireValidBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "APP_PORTAL_PUBLIC_BASE_URL is required when APP_PORTAL_ENABLED=true "
                            + "(public URL of this tenant instance, e.g. https://myrmacta.example.org/tenant-a)");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("APP_PORTAL_PUBLIC_BASE_URL is not a valid URL", ex);
        }
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || uri.getHost() == null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getRawUserInfo() != null) {
            throw new IllegalStateException(
                    "APP_PORTAL_PUBLIC_BASE_URL must be an absolute http(s) URL without query, fragment or credentials");
        }
    }

    public record Send(Duration pollDelay, int maxAttempts, int batchSize) {
        public Send {
            if (pollDelay == null) {
                pollDelay = Duration.ofSeconds(10);
            }
            if (maxAttempts <= 0) {
                maxAttempts = 5;
            }
            if (batchSize <= 0) {
                batchSize = 50;
            }
        }
    }

    public record Expiry(Duration pollDelay, int batchSize) {
        public Expiry {
            if (pollDelay == null) {
                pollDelay = Duration.ofMinutes(15);
            }
            if (batchSize <= 0) {
                batchSize = 50;
            }
        }
    }

    public record RateLimit(int perIpPerMinute) {
        public RateLimit {
            if (perIpPerMinute <= 0) {
                perIpPerMinute = 30;
            }
        }
    }
}
