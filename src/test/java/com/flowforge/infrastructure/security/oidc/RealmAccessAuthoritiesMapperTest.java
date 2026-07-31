package com.flowforge.infrastructure.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class RealmAccessAuthoritiesMapperTest {

    private final RealmAccessAuthoritiesMapper mapper = new RealmAccessAuthoritiesMapper();

    @Test
    void mapsRealmAccessRoles_toRolePrefixedAuthorities() {
        var authorities = mapper.mapAuthorities(List.of(oidcAuthority(
                Map.of("roles", List.of("WORKFLOW_ADMIN", "DOSSIER_VIEWER")))));

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_WORKFLOW_ADMIN", "ROLE_DOSSIER_VIEWER");
    }

    @Test
    void missingRealmAccess_yieldsNoRoleAuthorities() {
        var authorities = mapper.mapAuthorities(List.of(oidcAuthority(null)));

        assertThat(authorities).isEmpty();
    }

    @Test
    void doesNotDoublePrefixAlreadyPrefixedRoles() {
        var authorities = mapper.mapAuthorities(List.of(oidcAuthority(
                Map.of("roles", List.of("ROLE_TENANT_ADMIN")))));

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_TENANT_ADMIN");
    }

    @Test
    void preservesNonOidcAuthorities() {
        var authorities = mapper.mapAuthorities(List.of(
                new SimpleGrantedAuthority("SCOPE_openid"),
                oidcAuthority(Map.of("roles", List.of("WORKFLOW_ADMIN")))));

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_openid", "ROLE_WORKFLOW_ADMIN");
    }

    @Test
    void ignoresNullAndBlankRoleEntries() {
        var roles = new java.util.ArrayList<>();
        roles.add(null);
        roles.add("  ");
        roles.add("DOSSIER_VIEWER");
        var authorities = mapper.mapAuthorities(List.of(oidcAuthority(Map.of("roles", roles))));

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_DOSSIER_VIEWER");
    }

    @Test
    void rolesClaimNotACollection_yieldsNoRoleAuthorities() {
        var authorities = mapper.mapAuthorities(List.of(oidcAuthority(Map.of("roles", "WORKFLOW_ADMIN"))));

        assertThat(authorities).isEmpty();
    }

    private static OidcUserAuthority oidcAuthority(Map<String, Object> realmAccess) {
        var claims = new java.util.HashMap<String, Object>();
        claims.put("sub", "user-1");
        if (realmAccess != null) {
            claims.put("realm_access", realmAccess);
        }
        OidcIdToken idToken = new OidcIdToken(
                "token", Instant.now(), Instant.now().plusSeconds(60), claims);
        return new OidcUserAuthority(idToken);
    }
}
