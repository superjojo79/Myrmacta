package com.flowforge.infrastructure.security.oidc;

import com.flowforge.domain.user.port.in.RecordUserLoginUseCase;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Alimente l'annuaire local (D-ASSIGN-DIR) à chaque connexion OIDC, puis redirige comme le
 * gestionnaire par défaut d'{@code oauth2Login}. Un échec d'enregistrement ne bloque jamais la
 * connexion : l'annuaire n'est qu'une aide à la saisie des assignés.
 */
@Component
public class KnownUserRecordingSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnownUserRecordingSuccessHandler.class);

    private final RecordUserLoginUseCase recordUserLogin;
    private final AuthenticationSuccessHandler delegate;

    @Autowired
    public KnownUserRecordingSuccessHandler(RecordUserLoginUseCase recordUserLogin) {
        this(recordUserLogin, new SavedRequestAwareAuthenticationSuccessHandler());
    }

    KnownUserRecordingSuccessHandler(RecordUserLoginUseCase recordUserLogin, AuthenticationSuccessHandler delegate) {
        this.recordUserLogin = Objects.requireNonNull(recordUserLogin, "recordUserLogin");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof OidcUser user) {
            try {
                recordUserLogin.record(
                        user.getSubject(),
                        user.getFullName() != null ? user.getFullName() : user.getPreferredUsername(),
                        user.getEmail(),
                        roles(authentication));
            } catch (RuntimeException ex) {
                // Ni sujet ni e-mail dans le journal : seule la nature de l'échec est utile.
                LOGGER.warn("event=known_user_record_failed error={}", ex.getClass().getSimpleName());
            }
        }
        delegate.onAuthenticationSuccess(request, response, authentication);
    }

    private static Set<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
