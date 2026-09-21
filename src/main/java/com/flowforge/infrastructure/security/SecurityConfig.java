package com.flowforge.infrastructure.security;

import com.flowforge.infrastructure.config.ConnectorProperties;
import com.flowforge.infrastructure.config.DocumentGenerationProperties;
import com.flowforge.infrastructure.config.M2mSecurityProperties;
import com.flowforge.infrastructure.config.SecurityCookieProperties;
import com.flowforge.infrastructure.config.SecurityHeadersProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.security.oidc.RealmAccessAuthoritiesMapper;
import com.flowforge.infrastructure.tenant.TenantResolutionFilter;
import com.flowforge.infrastructure.web.connector.WebhookBodySizeLimitFilter;
import com.flowforge.infrastructure.web.document.DocumentGenerationBodySizeLimitFilter;
import com.flowforge.infrastructure.web.security.CspReportBodySizeLimitFilter;
import java.nio.charset.StandardCharsets;
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
    @Order(3)
    SecurityFilterChain webhookSecurityFilterChain(
            HttpSecurity http,
            ConnectorProperties connectorProperties,
            SecurityHeadersCustomizer securityHeadersCustomizer)
            throws Exception {
        // Auth métier (HMAC / token) réalisée dans les contrôleurs — pas d'OIDC ici.
        // D-CALLBACK-ROUTE : /api/callbacks/** doit être anonyme comme /api/webhooks/** --
        // absent d'ici, il tombe dans le chaînage BFF (@Order(4), anyRequest().authenticated())
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

    @Bean
    @Order(4)
    SecurityFilterChain bffSecurityFilterChain(
            HttpSecurity http,
            SecurityCookieProperties cookieProperties,
            RealmAccessAuthoritiesMapper authoritiesMapper,
            TenantResolutionFilter tenantResolutionFilter,
            ClientRegistrationRepository clientRegistrationRepository,
            DocumentGenerationProperties documentGenerationProperties,
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
                .oauth2Login(oauth2 -> oauth2.userInfoEndpoint(
                        userInfo -> userInfo.userAuthoritiesMapper(authoritiesMapper)))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .authorizeHttpRequests(auth -> auth
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
