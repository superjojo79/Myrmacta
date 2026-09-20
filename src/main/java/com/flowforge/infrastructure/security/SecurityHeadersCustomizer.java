package com.flowforge.infrastructure.security;

import com.flowforge.infrastructure.config.SecurityHeadersProperties;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.stereotype.Component;

/**
 * Paquet d'en-têtes A4 (D-MECHANISM = B) : CSP / XFO / nosniff / Referrer-Policy / HSTS.
 * Kill switch {@code enabled=false} : no-op (D-SWITCH-OFF = A).
 */
@Component
public class SecurityHeadersCustomizer {

    public static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';"
                    + " img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self';"
                    + " frame-src 'self' blob:; worker-src 'self' blob:;"
                    + " frame-ancestors 'none'; object-src 'none'; base-uri 'self'";

    static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;
    static final boolean HSTS_INCLUDE_SUBDOMAINS = true;
    static final boolean HSTS_PRELOAD = false;

    private final SecurityHeadersProperties properties;
    private final Environment environment;

    public SecurityHeadersCustomizer(SecurityHeadersProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public void apply(HttpSecurity http) throws Exception {
        if (!properties.enabled()) {
            return;
        }
        boolean hsts = hstsEnabled();
        http.headers(headers -> {
            headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY));
            headers.frameOptions(frame -> frame.deny());
            headers.contentTypeOptions(Customizer.withDefaults());
            headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
            if (hsts) {
                headers.httpStrictTransportSecurity(hstsConfig -> hstsConfig
                        .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)
                        .includeSubDomains(HSTS_INCLUDE_SUBDOMAINS)
                        .preload(HSTS_PRELOAD));
            } else {
                headers.httpStrictTransportSecurity(hstsConfig -> hstsConfig.disable());
            }
        });
    }

    boolean hstsEnabled() {
        return !environment.matchesProfiles("local", "test");
    }
}
