package be.myrmacta.infrastructure.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.myrmacta.domain.user.port.in.RecordUserLoginUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

class KnownUserRecordingSuccessHandlerTest {

    private RecordUserLoginUseCase recordUserLogin;
    private AuthenticationSuccessHandler delegate;
    private KnownUserRecordingSuccessHandler handler;
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        recordUserLogin = mock(RecordUserLoginUseCase.class);
        delegate = mock(AuthenticationSuccessHandler.class);
        handler = new KnownUserRecordingSuccessHandler(recordUserLogin, new TenantProperties("tenant_dev"), delegate);
    }

    @Test
    void recordsOidcUserWithMappedRolesThenDelegates() throws Exception {
        var authentication = oidcAuthentication(Map.of("name", "Demo User", "email", "demo@myrmacta.local"));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(recordUserLogin).record("sub-1", "Demo User", "demo@myrmacta.local", Set.of("TENANT_ADMIN"));
        verify(delegate).onAuthenticationSuccess(request, response, authentication);
    }

    @Test
    void fallsBackToPreferredUsernameWhenNameClaimIsAbsent() throws Exception {
        handler.onAuthenticationSuccess(request, response, oidcAuthentication(Map.of("preferred_username", "demo")));

        verify(recordUserLogin).record("sub-1", "demo", null, Set.of("TENANT_ADMIN"));
    }

    @Test
    void recordingFailureNeverBlocksLogin() throws Exception {
        doThrow(new IllegalStateException("db down")).when(recordUserLogin).record(any(), any(), any(), any());
        var authentication = oidcAuthentication(Map.of("name", "Demo User"));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(delegate).onAuthenticationSuccess(request, response, authentication);
    }

    /**
     * TODO §104 (issue #210, A6) — l'annuaire était alimenté au retour de connexion, avant toute vérification du
     * {@code tenant_id} : un compte créé par FAS sans {@code tenant_id}, ou un utilisateur d'un autre tenant du realm
     * partagé, y entrait. Il n'est plus enregistré, sa session est fermée et la réponse est un 403.
     */
    @Test
    void aUserWithoutTheTenantOfThisInstanceIsNeitherRecordedNorLoggedIn() throws Exception {
        for (Map<String, Object> claims : List.<Map<String, Object>>of(
                Map.of("name", "Inconnu"), Map.of("name", "Autre", "tenant_id", "tenant_b"))) {
            var req = new MockHttpServletRequest();
            var res = new MockHttpServletResponse();
            req.getSession(true);

            handler.onAuthenticationSuccess(req, res, oidcAuthentication(claims, false));

            assertThat(res.getStatus()).isEqualTo(403);
            assertThat(req.getSession(false)).isNull();
        }
        verifyNoInteractions(recordUserLogin, delegate);
    }

    @Test
    void ignoresNonOidcPrincipal() throws Exception {
        var authentication = new TestingAuthenticationToken("user", "pw", "ROLE_TENANT_ADMIN");

        handler.onAuthenticationSuccess(request, response, authentication);

        verifyNoInteractions(recordUserLogin);
        verify(delegate).onAuthenticationSuccess(request, response, authentication);
    }

    private static OAuth2AuthenticationToken oidcAuthentication(Map<String, Object> extraClaims) {
        return oidcAuthentication(extraClaims, true);
    }

    private static OAuth2AuthenticationToken oidcAuthentication(Map<String, Object> extraClaims, boolean thisTenant) {
        var claims = new java.util.HashMap<String, Object>(extraClaims);
        claims.put("sub", "sub-1");
        if (thisTenant) {
            claims.put("tenant_id", "tenant_dev");
        }
        var idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), claims);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"), new SimpleGrantedAuthority("OIDC_USER"));
        var user = new DefaultOidcUser(authorities, idToken);
        return new OAuth2AuthenticationToken(user, authorities, "oidc");
    }
}
