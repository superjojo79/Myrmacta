package com.flowforge.infrastructure.web.security;

import com.flowforge.infrastructure.config.SecurityHeadersProperties;
import com.flowforge.infrastructure.security.ClientIpResolver;
import com.flowforge.infrastructure.security.SecurityAuditLog;
import com.flowforge.infrastructure.security.SecurityHeadersCustomizer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Collecte les rapports de violation CSP (D-CSP-REPORTING, revue adversariale post-merge
 * PR #128, TODO §64) -- endpoint anonyme, internet-facing : les navigateurs les envoient sans
 * identifiants. Ne fait donc jamais confiance au contenu au-delà d'une petite liste blanche de
 * champs, journalisés tronqués et structurés (jamais le corps brut) pour borner à la fois le
 * volume de logs et le risque d'injection via un texte contrôlé par l'appelant. Répond toujours
 * 204, corps parsable ou non -- le navigateur ignore la réponse dans les deux cas, et une erreur
 * distinguable n'aiderait qu'un attaquant sondant le parseur.
 *
 * <p>Supporte les deux formats en usage : l'ancien {@code report-uri} ({@code
 * application/csp-report}, objet {@code {"csp-report": {...}}}) et la Reporting API moderne
 * ({@code report-to}, {@code application/reports+json}, tableau de {@code {"body": {...}}}).
 */
@RestController
class CspReportController {

    private static final int MAX_FIELD_LENGTH = 300;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CspReportRateLimiter rateLimiter;
    private final SecurityHeadersProperties properties;
    private final ClientIpResolver clientIpResolver;

    CspReportController(
            CspReportRateLimiter rateLimiter, SecurityHeadersProperties properties, ClientIpResolver clientIpResolver) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping(SecurityHeadersCustomizer.CSP_REPORT_PATH)
    ResponseEntity<Void> receive(HttpServletRequest request, @RequestBody(required = false) byte[] rawBody) {
        if (!rateLimiter.tryConsume(clientIpResolver.resolve(request), properties.cspReportRateLimitPerMinute())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (rawBody != null && rawBody.length > 0) {
            try {
                logViolations(request, objectMapper.readTree(rawBody));
            } catch (RuntimeException ex) {
                // Malformed report body -- nothing to log, never surfaced to the caller.
            }
        }
        return ResponseEntity.noContent().build();
    }

    private void logViolations(HttpServletRequest request, JsonNode root) {
        if (root.isArray()) {
            root.forEach(entry -> logIfPresent(request, entry.path("body"), "documentURL", "effectiveDirective", "blockedURL"));
            return;
        }
        logIfPresent(request, root.path("csp-report"), "document-uri", "violated-directive", "blocked-uri");
    }

    private void logIfPresent(
            HttpServletRequest request, JsonNode report, String documentUriField, String directiveField, String blockedUriField) {
        if (report.isMissingNode() || report.isNull()) {
            return;
        }
        SecurityAuditLog.cspViolationReported(
                request,
                text(report, documentUriField),
                text(report, directiveField),
                text(report, blockedUriField));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        String text = value.asText();
        return text.length() > MAX_FIELD_LENGTH ? text.substring(0, MAX_FIELD_LENGTH) + "..." : text;
    }
}
