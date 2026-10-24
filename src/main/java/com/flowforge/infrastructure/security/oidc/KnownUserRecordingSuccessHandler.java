package com.flowforge.infrastructure.security.oidc;

import com.flowforge.domain.user.port.in.RecordUserLoginUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.security.SecurityAuditLog;
import com.flowforge.infrastructure.tenant.TenantMembership;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Alimente l'annuaire local (D-ASSIGN-DIR) à chaque connexion OIDC, puis redirige comme le
 * gestionnaire par défaut d'{@code oauth2Login}. Un échec d'enregistrement ne bloque jamais la
 * connexion : l'annuaire n'est qu'une aide à la saisie des assignés.
 *
 * <p>TODO §103 (A6) — ce gestionnaire s'exécute avant que {@code TenantResolutionFilter} ne voie l'utilisateur : il
 * applique donc lui-même {@link TenantMembership}. Un utilisateur sans le {@code tenant_id} de l'instance (compte créé
 * par un fournisseur d'identité, pas encore validé par un admin, ou utilisateur d'un autre tenant) n'est pas inscrit,
 * sa session est fermée et la réponse est un 403, comme celle du filtre.
 */
@Component
public class KnownUserRecordingSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnownUserRecordingSuccessHandler.class);

    private final RecordUserLoginUseCase recordUserLogin;
    private final TenantMembership tenantMembership;
    private final AuthenticationSuccessHandler delegate;

    @Autowired
    public KnownUserRecordingSuccessHandler(RecordUserLoginUseCase recordUserLogin, TenantProperties tenantProperties) {
        this(recordUserLogin, tenantProperties, new SavedRequestAwareAuthenticationSuccessHandler());
    }

    KnownUserRecordingSuccessHandler(
            RecordUserLoginUseCase recordUserLogin, TenantProperties tenantProperties, AuthenticationSuccessHandler delegate) {
        this.recordUserLogin = Objects.requireNonNull(recordUserLogin, "recordUserLogin");
        this.tenantMembership = new TenantMembership(tenantProperties);
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof OidcUser user) {
            if (!tenantMembership.belongs(user)) {
                SecurityAuditLog.tenantMismatch(request, TenantMembership.claimedTenantId(user));
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
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
