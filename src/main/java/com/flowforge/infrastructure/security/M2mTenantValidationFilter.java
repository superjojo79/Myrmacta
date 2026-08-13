package com.flowforge.infrastructure.security;

import com.flowforge.infrastructure.config.TenantProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chaîne M2M ({@code /api/m2m/**}) : refuse les JWT dont le claim {@code tenant_id}
 * ne correspond pas à {@link TenantProperties#id()} (DD-M2M). Mismatch / absent → 403.
 */
public class M2mTenantValidationFilter extends OncePerRequestFilter {

    private final TenantProperties tenantProperties;

    public M2mTenantValidationFilter(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String claimedTenantId = jwt.getClaimAsString("tenant_id");
            if (!tenantProperties.id().equals(claimedTenantId)) {
                SecurityAuditLog.tenantMismatch(request, claimedTenantId);
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
