package com.flowforge.infrastructure.security;

import com.flowforge.infrastructure.config.SecurityCookieProperties;
import com.flowforge.infrastructure.security.oidc.RealmAccessAuthoritiesMapper;
import com.flowforge.infrastructure.tenant.TenantResolutionFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
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
@EnableConfigurationProperties(SecurityCookieProperties.class)
public class SecurityConfig {

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

    @Bean
    @Order(3)
    SecurityFilterChain bffSecurityFilterChain(
            HttpSecurity http,
            SecurityCookieProperties cookieProperties,
            RealmAccessAuthoritiesMapper authoritiesMapper,
            TenantResolutionFilter tenantResolutionFilter,
            ClientRegistrationRepository clientRegistrationRepository)
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
                .oauth2Login(oauth2 -> oauth2.userInfoEndpoint(
                        userInfo -> userInfo.userAuthoritiesMapper(authoritiesMapper)))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new ApiAuthenticationEntryPoint(loginEntryPoint)));

        return http.build();
    }

    private static OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository clients) {
        var handler = new OidcClientInitiatedLogoutSuccessHandler(clients);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }
}
