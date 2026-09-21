package com.flowforge.infrastructure.security;

import com.flowforge.infrastructure.config.SecurityHeadersProperties;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.stereotype.Component;

/**
 * Paquet d'en-têtes A4 (D-MECHANISM = B) : CSP / XFO / nosniff / Referrer-Policy / HSTS.
 * Kill switch {@code enabled=false} : no-op (D-SWITCH-OFF = A).
 * HSTS (A4-HSTS-001) : requête secure <strong>et</strong> pair TCP immédiat
 * loopback / RFC1918 (unwrap des wrappers {@code X-Forwarded-*}).
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
                        .preload(HSTS_PRELOAD)
                        .requestMatcher(SecurityHeadersCustomizer::shouldWriteHsts));
            } else {
                headers.httpStrictTransportSecurity(hstsConfig -> hstsConfig.disable());
            }
        });
    }

    boolean hstsEnabled() {
        return !environment.matchesProfiles("local", "test");
    }

    /**
     * HSTS seulement si Spring voit une requête HTTPS (y compris via
     * {@code X-Forwarded-Proto}) <strong>et</strong> que le pair TCP immédiat
     * (avant unwrap {@code ForwardedHeaderFilter}) est loopback ou RFC1918.
     */
    static boolean shouldWriteHsts(HttpServletRequest request) {
        return request.isSecure() && isTrustedImmediatePeer(request);
    }

    static boolean isTrustedImmediatePeer(HttpServletRequest request) {
        String remoteAddr = immediateTcpPeer(request);
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            return address.isLoopbackAddress() || address.isSiteLocalAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    /**
     * Adresse du pair TCP : on descend les {@link HttpServletRequestWrapper}
     * pour ignorer {@code X-Forwarded-For} posé par {@code ForwardedHeaderFilter}.
     */
    static String immediateTcpPeer(HttpServletRequest request) {
        HttpServletRequest current = request;
        while (current instanceof HttpServletRequestWrapper wrapper) {
            ServletRequest inner = wrapper.getRequest();
            if (!(inner instanceof HttpServletRequest httpInner)) {
                break;
            }
            current = httpInner;
        }
        return current.getRemoteAddr();
    }
}
