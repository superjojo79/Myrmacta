package be.myrmacta.infrastructure.security;

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

    /** Même événement que {@link #sensitiveFieldChanged}, pour une écriture faite par un process (aucune requête HTTP). */
    public static void sensitiveFieldChangedByWorkflow(
            UUID dossierId, String processInstanceId, String activityId, String field) {
        LOGGER.info(
                "event=sensitive_field_changed source=workflow processInstanceId={} activityId={} dossierId={} field={}",
                processInstanceId,
                activityId,
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

    /**
     * A browser reported a CSP violation on {@code /api/security/csp-reports} (adversarial
     * review follow-up on PR #128, TODO §64). Anonymous, internet-facing endpoint : callers
     * pass in report fields already truncated by {@code CspReportController}, so an attacker
     * cannot use this to write unbounded or unbounded-count log lines.
     */
    public static void cspViolationReported(
            HttpServletRequest request, String documentUri, String violatedDirective, String blockedUri) {
        LOGGER.warn(
                "event=csp_violation_reported remoteAddr={} documentUri={} violatedDirective={} blockedUri={}",
                request.getRemoteAddr(),
                documentUri,
                violatedDirective,
                blockedUri);
    }

    /**
     * TODO §94 — un administrateur a rejoué un incident (job en dead letter). INFO : action légitime, tracée. Jamais le
     * message de l'exception (il peut contenir des données métier).
     */
    public static void incidentRetried(
            String method, String path, Authentication authentication, UUID linkId, String jobId) {
        LOGGER.info(
                "event=incident_retried method={} path={} principal={} linkId={} jobId={}",
                method,
                path,
                authentication == null ? "anonymous" : authentication.getName(),
                linkId,
                jobId);
    }

    /**
     * TODO §106 (D-PIECE-CLASSIFY) — le type d'une pièce jointe a été attribué, changé ou retiré. INFO : action légitime,
     * tracée ; seules les clés de type sont écrites, jamais le libellé ni le nom du fichier.
     */
    public static void attachmentClassified(HttpServletRequest request, Authentication authentication, UUID dossierId,
            UUID elementId, String previousPieceKey, String pieceKey) {
        LOGGER.info(
                "event=attachment_classified method={} path={} principal={} dossierId={} elementId={} from={} to={}",
                request.getMethod(),
                request.getRequestURI(),
                authentication == null ? "anonymous" : authentication.getName(),
                dossierId,
                elementId,
                previousPieceKey == null ? "-" : previousPieceKey,
                pieceKey == null ? "-" : pieceKey);
    }

    /**
     * TODO §106 (revue adversariale de #211, F5) — pré-vérification IA : relance, décision sur un constat (avec la décision
     * précédente, une annulation restant tracée), export du journal. Identifiants seulement, jamais un texte de constat.
     *
     * @param channel {@code rest} ou {@code mcp}
     */
    public static void precheckRerunRequested(String channel, String principal, UUID dossierId, UUID runId) {
        LOGGER.info("event=precheck_rerun_requested channel={} principal={} dossierId={} runId={}", channel, principal,
                dossierId, runId);
    }

    public static void precheckFindingDecided(String principal, UUID dossierId, UUID runId, UUID findingId,
            String previousDecision, String decision) {
        LOGGER.info("event=precheck_finding_decided principal={} dossierId={} runId={} findingId={} from={} to={}",
                principal, dossierId, runId, findingId, previousDecision == null ? "-" : previousDecision,
                decision == null ? "-" : decision);
    }

    public static void precheckLogExported(String principal, UUID dossierId, UUID runId) {
        LOGGER.info("event=precheck_log_exported principal={} dossierId={} runId={}", principal, dossierId, runId);
    }
}
