package be.myrmacta.infrastructure.web.document;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects an oversized POST on generate / preview / preview PDF via {@code Content-Length}
 * before Spring reads the body into memory. PDF rendering is the costliest operation exposed to
 * the least-privileged role allowed here (DOSSIER_MANAGER), and unlike template creation
 * ({@code DocumentTemplateSupport.assertHtmlSize}) the caller-supplied {@code variables} map had
 * no size bound at all. A request that lies about its length via chunked transfer-encoding still
 * reaches Jackson's deserialization — the same residual gap already accepted for
 * {@code WebhookBodySizeLimitFilter}, and smaller here since this endpoint requires an
 * authenticated session and CSRF, not anonymous internet access.
 */
public class DocumentGenerationBodySizeLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PATHS = Set.of(
            "/api/documents/generate", "/api/documents/preview", "/api/documents/preview/pdf");

    private final long maxBodyBytes;

    public DocumentGenerationBodySizeLimitFilter(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && PATHS.contains(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > maxBodyBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
