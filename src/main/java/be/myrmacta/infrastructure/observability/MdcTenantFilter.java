package be.myrmacta.infrastructure.observability;

import be.myrmacta.infrastructure.config.TenantProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcTenantFilter extends OncePerRequestFilter {

    private final TenantProperties tenantProperties;

    public MdcTenantFilter(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try (MdcScope ignored = MdcScope.tenant(tenantProperties.id())) {
            filterChain.doFilter(request, response);
        }
    }
}
