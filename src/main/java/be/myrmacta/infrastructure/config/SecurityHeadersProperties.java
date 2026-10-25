package be.myrmacta.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Kill switch et réglages du paquet d'en-têtes HTTP A4 (CSP / HSTS / XFO / nosniff /
 * Referrer-Policy).
 *
 * @param trustedProxyCidrs pairs TCP immédiats considérés comme le reverse-proxy de confiance
 *     pour l'émission HSTS (A4-HSTS-001) -- défaut = loopback + RFC1918, valable pour le modèle
 *     Docker+Apache mono-host documenté dans docs/guides/DEPLOIEMENT.md. À restreindre à l'IP réelle du
 *     proxy avant toute migration vers un environnement où d'autres pairs partagent ce même
 *     espace d'adresses (ex. pods Kubernetes).
 * @param cspReportRateLimitPerMinute plafond par pair TCP sur {@code /api/security/csp-reports},
 *     endpoint anonyme exposé aux navigateurs des utilisateurs.
 */
@ConfigurationProperties(prefix = "app.security.headers")
public record SecurityHeadersProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue({"127.0.0.0/8", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"})
                List<String> trustedProxyCidrs,
        @DefaultValue("60") int cspReportRateLimitPerMinute) {}
