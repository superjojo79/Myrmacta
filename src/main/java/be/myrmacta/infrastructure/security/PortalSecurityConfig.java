package be.myrmacta.infrastructure.security;

import be.myrmacta.infrastructure.config.PortalProperties;
import be.myrmacta.infrastructure.web.connector.WebhookBodySizeLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * TODO §68 (portail-externe) — chaîne publique {@code @Order(3)}, présente seulement si le portail
 * est activé (D-PORTAL-ACTIVATION) : désactivé, {@code /portal/**} et {@code /api/portal/**}
 * retombent dans la chaîne BFF {@code @Order(5)} (login exigé) et l'API n'existe pas.
 *
 * <p>Sans session ni cookie (D-PORTAL-CSRF-SESSION) : le jeton voyage dans l'en-tête
 * {@code X-Portal-Token}, qu'un site tiers ne peut pas poser sans pré-vol CORS (aucun CORS
 * applicatif, décision n° 27) — la protection CSRF n'a donc rien à protéger ici. Mêmes en-têtes
 * de sécurité que le reste de l'application (CSP enforce A4). Corps JSON plafonné avant
 * bufférisation ; le multipart reste borné par {@code spring.servlet.multipart.*}.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.portal", name = "enabled", havingValue = "true")
class PortalSecurityConfig {

    static final String ATTACHMENTS_PATH = "/api/portal/task/attachments";

    @Bean
    @Order(3)
    SecurityFilterChain portalSecurityFilterChain(
            HttpSecurity http, PortalProperties properties, SecurityHeadersCustomizer securityHeadersCustomizer)
            throws Exception {
        http.securityMatcher("/portal", "/portal/**", "/api/portal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(
                        new PortalBodySizeLimitFilter(properties.maxBodyBytes()), SecurityContextHolderFilter.class);
        securityHeadersCustomizer.apply(http);
        return http.build();
    }

    /** Même double garde que les webhooks (Content-Length déclaré + flux borné), sauf pour le multipart. */
    static final class PortalBodySizeLimitFilter extends WebhookBodySizeLimitFilter {

        PortalBodySizeLimitFilter(long maxBodyBytes) {
            super(maxBodyBytes);
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return request.getRequestURI().endsWith(ATTACHMENTS_PATH);
        }
    }
}
