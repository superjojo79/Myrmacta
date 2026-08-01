package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class TenantAdminGuard {

    void requireTenantAdmin(Authentication authentication) {
        boolean tenantAdmin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_TENANT_ADMIN"::equals);
        if (!tenantAdmin) {
            throw new AccessDeniedException("TENANT_ADMIN role required");
        }
    }
}
