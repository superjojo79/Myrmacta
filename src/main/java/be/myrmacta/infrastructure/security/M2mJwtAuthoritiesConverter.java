package be.myrmacta.infrastructure.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Convertit un JWT M2M en autorités : chaque valeur du claim {@code roles} devient une
 * autorité (ex. {@code API_SERVICE} + {@code ROLE_API_SERVICE}) ; chaque {@code scope}
 * devient {@code SCOPE_<scope>} et l'autorité brute. Permet {@code hasAuthority("API_SERVICE")}.
 */
public class M2mJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        Object roles = jwt.getClaim("roles");
        if (roles instanceof Collection<?> roleValues) {
            for (Object role : roleValues) {
                addRole(authorities, role);
            }
        }
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            for (String token : scope.trim().split("\\s+")) {
                addScope(authorities, token);
            }
        }
        Object scp = jwt.getClaim("scp");
        if (scp instanceof Collection<?> scopeValues) {
            for (Object token : scopeValues) {
                addScope(authorities, token == null ? null : token.toString());
            }
        }
        return authorities;
    }

    private static void addRole(Set<GrantedAuthority> authorities, Object role) {
        if (role == null) {
            return;
        }
        String name = role.toString();
        if (name.isBlank()) {
            return;
        }
        authorities.add(new SimpleGrantedAuthority(name));
        if (!name.startsWith("ROLE_")) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + name));
        }
    }

    private static void addScope(Set<GrantedAuthority> authorities, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        authorities.add(new SimpleGrantedAuthority(token));
        if (!token.startsWith("SCOPE_")) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + token));
        }
    }
}
