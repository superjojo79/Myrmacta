package be.myrmacta.infrastructure.security;

import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.config.DocumentGenerationProperties;
import be.myrmacta.infrastructure.config.M2mSecurityProperties;
import be.myrmacta.infrastructure.config.NotificationTemplateProperties;
import be.myrmacta.infrastructure.config.OidcGroupsProperties;
import be.myrmacta.infrastructure.config.SecurityCookieProperties;
import be.myrmacta.infrastructure.config.SecurityHeadersProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.security.oidc.KnownUserRecordingSuccessHandler;
import be.myrmacta.infrastructure.security.oidc.RealmAccessAuthoritiesMapper;
import be.myrmacta.infrastructure.tenant.TenantResolutionFilter;
import be.myrmacta.infrastructure.web.connector.WebhookBodySizeLimitFilter;
import be.myrmacta.infrastructure.web.document.DocumentGenerationBodySizeLimitFilter;
import be.myrmacta.infrastructure.web.dossier.TenantCalendarIcsBodySizeLimitFilter;
import be.myrmacta.infrastructure.web.security.CspReportBodySizeLimitFilter;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({
    SecurityCookieProperties.class,
    OidcGroupsProperties.class,
    M2mSecurityProperties.class,
    SecurityHeadersProperties.class
})
public class SecurityConfig {

    /** Autorité M2M requise sur {@code /api/m2m/**} (DD-M2M). */
    static final String M2M_AUTHORITY = "API_SERVICE";

    /** Corps max d'un rapport CSP -- quelques centaines d'octets en usage réel (TODO §64). */
    private static final long CSP_REPORT_MAX_BODY_BYTES = 8_192L;

    /**
     * Empêche le double enregistrement servlet de {@link TenantResolutionFilter}
     * ({@code @Component}) — la garde ne doit vivre que dans la chaîne Security.
     */
    @Bean
    FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilterRegistration(
            TenantResolutionFilter tenantResolutionFilter) {
        FilterRegistrationBean<TenantResolutionFilter> registration =
                new FilterRegistrationBean<>(tenantResolutionFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Décodeur JWT M2M : issuer resource-server si configuré, sinon HMAC HS256 local
     * (tests / local sans IdP), sinon décodeur qui refuse tout.
     */
    @Bean
    JwtDecoder m2mJwtDecoder(M2mSecurityProperties properties) {
        if (properties.issuerUri() != null && !properties.issuerUri().isBlank()) {
            return JwtDecoders.fromIssuerLocation(properties.issuerUri());
        }
        if (properties.jwtSecret() != null && !properties.jwtSecret().isBlank()) {
            SecretKeySpec key =
                    new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            return NimbusJwtDecoder.withSecretKey(key).build();
        }
        return token -> {
            throw new BadJwtException("M2M JWT resource server is not configured");
        };
    }

    @Bean
    JwtAuthenticationConverter m2mJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new M2mJwtAuthoritiesConverter());
        return converter;
    }

    @Bean
    @Order(0)
    SecurityFilterChain actuatorSecurityFilterChain(
            HttpSecurity http, SecurityHeadersCustomizer securityHeadersCustomizer) throws Exception {
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        securityHeadersCustomizer.apply(http);
        return http.build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain m2mSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder m2mJwtDecoder,
            JwtAuthenticationConverter m2mJwtAuthenticationConverter,
            TenantProperties tenantProperties,
            SecurityHeadersCustomizer securityHeadersCustomizer)
            throws Exception {
        http.securityMatcher("/api/m2m/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(M2M_AUTHORITY))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(m2mJwtDecoder)
                        .jwtAuthenticationConverter(m2mJwtAuthenticationConverter)))
                .addFilterAfter(
                        new M2mTenantValidationFilter(tenantProperties),
                        BearerTokenAuthenticationFilter.class);
        securityHeadersCustomizer.apply(http);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain cspReportSecurityFilterChain(
            HttpSecurity http, SecurityHeadersCustomizer securityHeadersCustomizer) throws Exception {
        // Endpoint anonyme, internet-facing (les navigateurs postent sans identifiants) --
        // même raisonnement que D-CALLBACK-ROUTE ci-dessous : absent d'ici, il tomberait dans le
        // chaînage BFF (anyRequest().authenticated()) et le rapport serait rejeté en 302/401
        // avant d'atteindre CspReportController (TODO §64, revue adversariale post-merge PR #128).
        http.securityMatcher(SecurityHeadersCustomizer.CSP_REPORT_PATH)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(
                        new CspReportBodySizeLimitFilter(CSP_REPORT_MAX_BODY_BYTES),
                        SecurityContextHolderFilter.class);
        securityHeadersCustomizer.apply(http);
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain webhookSecurityFilterChain(
            HttpSecurity http,
            ConnectorProperties connectorProperties,
            SecurityHeadersCustomizer securityHeadersCustomizer)
            throws Exception {
        // Auth métier (HMAC / token) réalisée dans les contrôleurs — pas d'OIDC ici.
        // D-CALLBACK-ROUTE : /api/callbacks/** doit être anonyme comme /api/webhooks/** --
        // absent d'ici, il tombe dans le chaînage BFF (@Order(5), anyRequest().authenticated())
        // et n'atteint jamais CallbackWebhookController (trouvé en revue de sécurité
        // adversariale post-merge, TODO §51).
        http.securityMatcher("/api/webhooks/**", "/api/callbacks/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // Rejette une requête trop volumineuse avant que Spring ne bufférise le corps
                // en mémoire (voir WebhookBodySizeLimitFilter) — endpoint anonyme exposé sur internet.
                .addFilterBefore(
                        new WebhookBodySizeLimitFilter(connectorProperties.webhook().maxBodyBytes()),
                        SecurityContextHolderFilter.class);
        securityHeadersCustomizer.apply(http);
        return http.build();
    }

    /**
     * {@code @Order(5)} : dernière chaîne, {@code anyRequest().authenticated()}. {@code @Order(3)} est
     * la chaîne publique du portail externe (TODO §68, {@code PortalSecurityConfig}, présente
     * seulement si le portail est activé).
     */
    @Bean
    @Order(5)
    SecurityFilterChain bffSecurityFilterChain(
            HttpSecurity http,
            SecurityCookieProperties cookieProperties,
            RealmAccessAuthoritiesMapper authoritiesMapper,
            KnownUserRecordingSuccessHandler loginSuccessHandler,
            TenantResolutionFilter tenantResolutionFilter,
            ClientRegistrationRepository clientRegistrationRepository,
            DocumentGenerationProperties documentGenerationProperties,
            NotificationTemplateProperties notificationTemplateProperties,
            SecurityHeadersCustomizer securityHeadersCustomizer)
            throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .sameSite("Lax")
                .secure(cookieProperties.secure()));

        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // SPA lit le cookie brut — pas de XOR (Spring Security 6+)
        csrfHandler.setCsrfRequestAttributeName("_csrf");

        AuthenticationEntryPoint loginEntryPoint =
                new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/oidc");

        http.csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfHandler))
                .addFilterAfter(new CsrfCookieEagerLoadFilter(), BasicAuthenticationFilter.class)
                .addFilterAfter(tenantResolutionFilter, SecurityContextHolderFilter.class)
                // Rejette un body trop volumineux avant bufférisation —
                // POST /api/documents/generate, /preview et /preview/pdf (voir shouldNotFilter).
                .addFilterBefore(
                        new DocumentGenerationBodySizeLimitFilter(documentGenerationProperties.maxBodyBytes()),
                        SecurityContextHolderFilter.class)
                // Même garde pour POST /api/notification-templates/preview (gabarit et modèle fournis par l'appelant).
                .addFilterBefore(
                        new DocumentGenerationBodySizeLimitFilter(
                                notificationTemplateProperties.previewRequestMaxBytes(),
                                Set.of("/api/notification-templates/preview")),
                        SecurityContextHolderFilter.class)
                // TODO §124 (D-CAL-ICS-SIZE) — sources ICS : 1 Mio + marge, avant le multipart Spring (25 Mo).
                .addFilterBefore(new TenantCalendarIcsBodySizeLimitFilter(), SecurityContextHolderFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(authoritiesMapper))
                        // D-ASSIGN-DIR : enregistre l'utilisateur dans l'annuaire local, puis
                        // redirige comme le gestionnaire par défaut.
                        .successHandler(loginSuccessHandler))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .authorizeHttpRequests(auth -> auth
                        // C1 (revue adversariale du portail) — le dispatch d'erreur vers /error
                        // d'une requête déjà admise par une chaîne publique (portail, webhooks,
                        // rejet du pare-feu HTTP) retombait ici : redirection vers la connexion
                        // Keycloak et session créée pour un anonyme. Une requête anonyme vers une
                        // route protégée est refusée au dispatch initial, avant tout /error.
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                        .hasAnyRole("TENANT_ADMIN", "WORKFLOW_ADMIN")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new ApiAuthenticationEntryPoint(loginEntryPoint)));
        securityHeadersCustomizer.apply(http);

        return http.build();
    }

    private static OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository clients) {
        var handler = new OidcClientInitiatedLogoutSuccessHandler(clients);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }
}
