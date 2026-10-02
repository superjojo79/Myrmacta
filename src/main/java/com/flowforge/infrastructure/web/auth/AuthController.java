package com.flowforge.infrastructure.web.auth;

import com.flowforge.infrastructure.config.I18nProperties;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final I18nProperties i18n;

    public AuthController(I18nProperties i18n) {
        this.i18n = i18n;
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .filter(role -> !role.isBlank())
                .distinct()
                .sorted()
                .toList();
        return ResponseEntity.ok(new AuthUserResponse(
                user.getSubject(), user.getFullName(), user.getEmail(), roles, i18n.defaultLocale()));
    }
}
