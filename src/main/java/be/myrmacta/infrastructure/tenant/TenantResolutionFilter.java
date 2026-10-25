package be.myrmacta.infrastructure.tenant;

import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.security.SecurityAuditLog;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Vérifie que le claim OIDC {@code tenant_id} correspond à {@link TenantProperties#id()}.
 * Mismatch / absent → invalidation session + 403 (body vide).
 */
@Component
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantMembership tenantMembership;

    public TenantResolutionFilter(TenantProperties tenantProperties) {
        this.tenantMembership = new TenantMembership(tenantProperties);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
            if (!tenantMembership.belongs(user)) {
                SecurityAuditLog.tenantMismatch(request, TenantMembership.claimedTenantId(user));
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
