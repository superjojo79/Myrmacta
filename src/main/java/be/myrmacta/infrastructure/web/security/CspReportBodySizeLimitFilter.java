package be.myrmacta.infrastructure.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejette un rapport CSP trop volumineux avant que Spring ne bufférise le corps en mémoire --
 * endpoint anonyme, internet-facing. Un rapport CSP légitime tient en quelques centaines
 * d'octets ; un seul contrôle sur {@code Content-Length} déclaré suffit ici (contrairement à
 * {@code WebhookBodySizeLimitFilter}, pas de flux applicatif volumineux à défendre contre un
 * corps chunké mentant sur sa taille).
 */
public class CspReportBodySizeLimitFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;

    public CspReportBodySizeLimitFilter(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBodyBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
