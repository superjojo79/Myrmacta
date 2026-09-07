package com.flowforge.infrastructure.security.oidc;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.stereotype.Component;

/**
 * Mappe {@code realm_access.roles} (Keycloak) vers des autorités {@code ROLE_*}.
 */
@Component
public class RealmAccessAuthoritiesMapper implements GrantedAuthoritiesMapper {

    /**
     * Human BFF roles only. {@code API_SERVICE} is a machine role (TODO §57 M1) and must not
     * elevate an OIDC browser session.
     */
    private static final Set<String> HUMAN_ROLES = Set.of(
            "TENANT_ADMIN", "WORKFLOW_ADMIN", "DOSSIER_MANAGER", "DOSSIER_VIEWER");

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new HashSet<>();
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidcUserAuthority) {
                mapped.addAll(rolesFromRealmAccess(oidcUserAuthority));
            } else {
                mapped.add(authority);
            }
        }
        return mapped;
    }

    private static Collection<GrantedAuthority> rolesFromRealmAccess(OidcUserAuthority authority) {
        Object realmAccess = authority.getIdToken().getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
            return List.of();
        }
        Object roles = realmAccessMap.get("roles");
        if (!(roles instanceof Collection<?> roleNames)) {
            return List.of();
        }
        Set<GrantedAuthority> result = new HashSet<>();
        for (Object role : roleNames) {
            if (role == null) {
                continue;
            }
            String name = role.toString();
            if (name.isBlank()) {
                continue;
            }
            if (name.startsWith("ROLE_")) {
                name = name.substring("ROLE_".length());
            }
            if (!HUMAN_ROLES.contains(name)) {
                continue;
            }
            result.add(new SimpleGrantedAuthority("ROLE_" + name));
        }
        return result;
    }
}
