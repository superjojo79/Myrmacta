package com.flowforge.infrastructure.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class AuthControllerTest {

    private final AuthController controller = new AuthController();

    @Test
    void me_nullUser_returns401() {
        ResponseEntity<AuthUserResponse> response = controller.me(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void me_stripsRolePrefixAndReturnsProfile() {
        OidcIdToken idToken = new OidcIdToken(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of(
                        "sub", "user-1",
                        "email", "ada@example.com",
                        "name", "Ada Lovelace"));
        var user = new DefaultOidcUser(
                List.of(
                        new SimpleGrantedAuthority("ROLE_WORKFLOW_ADMIN"),
                        new SimpleGrantedAuthority("SCOPE_openid")),
                idToken);

        ResponseEntity<AuthUserResponse> response = controller.me(user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new AuthUserResponse(
                "Ada Lovelace", "ada@example.com", List.of("WORKFLOW_ADMIN")));
    }

    @Test
    void me_ignoresBlankRoleAfterPrefixStrip() {
        OidcIdToken idToken = new OidcIdToken(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("sub", "user-1", "email", "ada@example.com", "name", "Ada"));
        var user = new DefaultOidcUser(
                List.of(
                        new SimpleGrantedAuthority("ROLE_"),
                        new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")),
                idToken);

        ResponseEntity<AuthUserResponse> response = controller.me(user);

        assertThat(response.getBody().roles()).containsExactly("TENANT_ADMIN");
    }
}
