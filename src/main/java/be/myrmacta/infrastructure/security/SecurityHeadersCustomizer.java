package be.myrmacta.infrastructure.security;

import be.myrmacta.infrastructure.config.SecurityHeadersProperties;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.stereotype.Component;

/**
 * Paquet d'en-têtes A4 (D-MECHANISM = B) : CSP / XFO / nosniff / Referrer-Policy / HSTS.
 * Kill switch {@code enabled=false} : no-op (D-SWITCH-OFF = A) -- refusé au démarrage hors
 * profils {@code local}/{@code test} par {@link SecurityHeadersEnvironmentPostProcessor}.
 * HSTS (A4-HSTS-001) : requête secure <strong>et</strong> pair TCP immédiat de confiance
 * (unwrap des wrappers {@code X-Forwarded-*}), voir {@link TrustedProxyCidrMatcher}.
 */
@Component
public class SecurityHeadersCustomizer {

    /** Chemin de collecte des rapports CSP -- endpoint anonyme, voir {@code CspReportController}. */
    public static final String CSP_REPORT_PATH = "/api/security/csp-reports";

    static final String CSP_REPORT_GROUP = "csp-endpoint";

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
    private final TrustedProxyCidrMatcher trustedProxyMatcher;
    private final String reportUri;
    private final String effectiveContentSecurityPolicy;

    public SecurityHeadersCustomizer(SecurityHeadersProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
        this.trustedProxyMatcher = new TrustedProxyCidrMatcher(properties.trustedProxyCidrs());
        this.reportUri = environment.getProperty("server.servlet.context-path", "") + CSP_REPORT_PATH;
        this.effectiveContentSecurityPolicy = CONTENT_SECURITY_POLICY
                + "; report-uri " + reportUri
                + "; report-to " + CSP_REPORT_GROUP;
    }

    public void apply(HttpSecurity http) throws Exception {
        if (!properties.enabled()) {
            return;
        }
        boolean hsts = hstsEnabled();
        http.headers(headers -> {
            headers.contentSecurityPolicy(csp -> csp.policyDirectives(effectiveContentSecurityPolicy));
            headers.frameOptions(frame -> frame.deny());
            headers.contentTypeOptions(Customizer.withDefaults());
            headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
            headers.addHeaderWriter(new StaticHeadersWriter(
                    "Reporting-Endpoints", CSP_REPORT_GROUP + "=\"" + reportUri + "\""));
            if (hsts) {
                headers.httpStrictTransportSecurity(hstsConfig -> hstsConfig
                        .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)
                        .includeSubDomains(HSTS_INCLUDE_SUBDOMAINS)
                        .preload(HSTS_PRELOAD)
                        .requestMatcher(this::shouldWriteHsts));
            } else {
                headers.httpStrictTransportSecurity(hstsConfig -> hstsConfig.disable());
            }
        });
    }

    /** Valeur effective de la CSP (directives + {@code report-uri}/{@code report-to}). */
    String effectiveContentSecurityPolicy() {
        return effectiveContentSecurityPolicy;
    }

    boolean hstsEnabled() {
        return !environment.matchesProfiles("local", "test");
    }

    /**
     * HSTS seulement si Spring voit une requête HTTPS (y compris via
     * {@code X-Forwarded-Proto}) <strong>et</strong> que le pair TCP immédiat (avant unwrap
     * {@code ForwardedHeaderFilter}) est dans {@code app.security.headers.trusted-proxy-cidrs}.
     */
    boolean shouldWriteHsts(HttpServletRequest request) {
        return request.isSecure() && isTrustedImmediatePeer(request);
    }

    boolean isTrustedImmediatePeer(HttpServletRequest request) {
        return trustedProxyMatcher.matches(immediateTcpPeer(request));
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
