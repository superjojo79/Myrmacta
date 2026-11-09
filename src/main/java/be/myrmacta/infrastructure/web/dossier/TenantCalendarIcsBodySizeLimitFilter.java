package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.deadline.port.out.IcsCalendarParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * TODO §124 (D-CAL-ICS-SIZE) — refuse un POST trop gros vers les sources ICS avant que Spring n'écrive le multipart
 * sur disque (plafond global 25 Mo). Le contrôle applicatif à 1 Mio sur le fichier reste.
 */
public class TenantCalendarIcsBodySizeLimitFilter extends OncePerRequestFilter {

    /** Marge pour les champs du formulaire multipart autour du fichier ICS. */
    public static final long FORM_OVERHEAD_BYTES = 64L * 1024;

    public static final long MAX_REQUEST_BYTES = IcsCalendarParser.MAX_BYTES + FORM_OVERHEAD_BYTES;

    private final long maxBodyBytes;

    public TenantCalendarIcsBodySizeLimitFilter() {
        this(MAX_REQUEST_BYTES);
    }

    TenantCalendarIcsBodySizeLimitFilter(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        int sources = path.indexOf("/api/tenant/calendar/sources");
        return sources < 0;
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
