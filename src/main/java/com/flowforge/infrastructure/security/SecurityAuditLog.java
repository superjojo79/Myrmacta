package com.flowforge.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

/**
 * Structured WARN-level log for security-relevant denials — tenant claim mismatches and
 * authorization failures — kept separate from the request's own (often generic) 403 response so
 * these events can be aggregated or alerted on independently. Logger name is fixed
 * ({@code SECURITY_AUDIT}, not the calling class) so log shipping/alerting can target it
 * regardless of which filter or exception handler triggers it.
 */
public final class SecurityAuditLog {

    private static final Logger LOGGER = LoggerFactory.getLogger("SECURITY_AUDIT");

    private SecurityAuditLog() {
    }

    /** A request's {@code tenant_id} claim did not match this instance's configured tenant. */
    public static void tenantMismatch(HttpServletRequest request, String claimedTenantId) {
        LOGGER.warn(
                "event=tenant_mismatch method={} path={} claimedTenantId={}",
                request.getMethod(),
                request.getRequestURI(),
                claimedTenantId);
    }

    /** A request was denied by an {@code AccessDeniedException} raised from a use case. */
    public static void accessDenied(HttpServletRequest request, Authentication authentication, String reason) {
        LOGGER.warn(
                "event=access_denied method={} path={} principal={} reason={}",
                request.getMethod(),
                request.getRequestURI(),
                authentication == null ? "anonymous" : authentication.getName(),
                reason);
    }
}
