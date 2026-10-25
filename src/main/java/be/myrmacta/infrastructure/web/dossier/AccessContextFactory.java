package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.dossier.model.AccessContext;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class AccessContextFactory {

    public AccessContext.User user(Authentication authentication) {
        OidcUser oidcUser = oidcUser(authentication);
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .filter(role -> !role.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        return AccessContext.user(oidcUser.getSubject(), roles);
    }

    /**
     * Ensures the request carries an OIDC principal (same check as {@link #user}).
     * Use when the actor is not needed by the use case (e.g. workflow-type-config).
     */
    public void requireOidcPrincipal(Authentication authentication) {
        user(authentication);
    }

    private static OidcUser oidcUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new IllegalArgumentException("OIDC user required");
        }
        return oidcUser;
    }
}
