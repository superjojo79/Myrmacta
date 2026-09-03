package com.flowforge.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

/**
 * Structured security-relevant events, kept separate from the request's own (often generic)
 * response so they can be aggregated or alerted on independently. WARN level for denials —
 * tenant claim mismatches and authorization failures ; INFO level for legitimate but
 * traceability-worthy actions, such as a {@code sensitive} field being changed. Logger name is
 * fixed ({@code SECURITY_AUDIT}, not the calling class) so log shipping/alerting can target it
 * regardless of which filter, exception handler, or controller triggers it.
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

    /**
     * A {@code sensitive} dossier field was changed via {@code PATCH .../fields}. INFO level —
     * this is a legitimate, authorized edit, not a denial — logged for traceability only. Never
     * logs the field's value, only its name.
     */
    public static void sensitiveFieldChanged(
            HttpServletRequest request, Authentication authentication, UUID dossierId, String field) {
        LOGGER.info(
                "event=sensitive_field_changed method={} path={} principal={} dossierId={} field={}",
                request.getMethod(),
                request.getRequestURI(),
                authentication == null ? "anonymous" : authentication.getName(),
                dossierId,
                field);
    }

    public static void dossierRead(HttpServletRequest request, Authentication authentication, UUID dossierId) {
        LOGGER.info(
                "event=dossier_read method={} path={} principal={} dossierId={}",
                request.getMethod(),
                request.getRequestURI(),
                authentication == null ? "anonymous" : authentication.getName(),
                dossierId);
    }

    public static void elementContentRead(
            HttpServletRequest request, Authentication authentication, UUID elementId) {
        LOGGER.info(
                "event=element_content_read method={} path={} principal={} elementId={}",
                request.getMethod(),
                request.getRequestURI(),
                authentication == null ? "anonymous" : authentication.getName(),
                elementId);
    }

    /**
     * More than one process execution matched a callback correlation value -- always a BPMN
     * modeling defect (D-CALLBACK-MULTI-MATCH), never resolved silently. Never logs
     * {@code correlationValue} itself (caller-supplied business data), only the config-level
     * {@code messageName}/{@code correlationKey} and how many executions matched.
     */
    public static void callbackAmbiguousCorrelation(
            HttpServletRequest request, String messageName, String correlationKey, int matchCount) {
        LOGGER.warn(
                "event=callback_ambiguous_correlation method={} path={} messageName={} correlationKey={} matchCount={}",
                request.getMethod(),
                request.getRequestURI(),
                messageName,
                correlationKey,
                matchCount);
    }

    /**
     * A webhook/callback request failed authentication (bad or missing HMAC signature / shared
     * token) -- found silent in adversarial security review : the anonymous, internet-facing
     * {@code /api/webhooks/**} and {@code /api/callbacks/**} chain had no audit trail at all for
     * this denial, so a sustained brute-force attempt against a connector instance's secret would
     * leave zero signal. Never logs the submitted secret/signature, only the connector instance.
     */
    public static void webhookAuthenticationFailed(HttpServletRequest request, UUID instanceId) {
        LOGGER.warn(
                "event=webhook_authentication_failed method={} path={} instanceId={}",
                request.getMethod(),
                request.getRequestURI(),
                instanceId);
    }
}
