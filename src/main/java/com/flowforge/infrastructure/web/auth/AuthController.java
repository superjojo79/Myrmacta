package com.flowforge.infrastructure.web.auth;

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

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(AuthController::stripRolePrefix)
                .distinct()
                .sorted()
                .toList();
        return ResponseEntity.ok(new AuthUserResponse(user.getFullName(), user.getEmail(), roles));
    }

    private static String stripRolePrefix(String authority) {
        return authority != null && authority.startsWith("ROLE_")
                ? authority.substring("ROLE_".length())
                : authority;
    }
}
