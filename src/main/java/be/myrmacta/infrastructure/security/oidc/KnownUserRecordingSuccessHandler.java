package be.myrmacta.infrastructure.security.oidc;

import be.myrmacta.domain.user.port.in.RecordUserLoginUseCase;
import be.myrmacta.infrastructure.config.OidcGroupsProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.security.SecurityAuditLog;
import be.myrmacta.infrastructure.tenant.TenantMembership;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collection;
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
 * <p>TODO §104 (A6) — ce gestionnaire s'exécute avant que {@code TenantResolutionFilter} ne voie l'utilisateur : il
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
    private final String groupsClaim;

    @Autowired
    public KnownUserRecordingSuccessHandler(
            RecordUserLoginUseCase recordUserLogin, TenantProperties tenantProperties, OidcGroupsProperties groups) {
        this(recordUserLogin, tenantProperties, new SavedRequestAwareAuthenticationSuccessHandler(), groups.groupsClaim());
    }

    KnownUserRecordingSuccessHandler(
            RecordUserLoginUseCase recordUserLogin,
            TenantProperties tenantProperties,
            AuthenticationSuccessHandler delegate,
            String groupsClaim) {
        this.recordUserLogin = Objects.requireNonNull(recordUserLogin, "recordUserLogin");
        this.tenantMembership = new TenantMembership(tenantProperties);
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.groupsClaim = Objects.requireNonNull(groupsClaim, "groupsClaim");
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
                        roles(authentication),
                        groups(user));
            } catch (RuntimeException ex) {
                // Ni sujet ni e-mail dans le journal : seule la nature de l'échec est utile.
                LOGGER.warn("event=known_user_record_failed error={}", ex.getClass().getSimpleName());
            }
        }
        delegate.onAuthenticationSuccess(request, response, authentication);
    }

    /** TODO §114 (D-GROUP-SOURCE) — groupes de la claim configurée : liste de textes, ou un seul texte. Bornés par le cas d'usage. */
    private Set<String> groups(OidcUser user) {
        Object claim = user.getClaims().get(groupsClaim);
        if (claim instanceof String single) {
            return Set.of(single);
        }
        if (claim instanceof Collection<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .limit(1_000)
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
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
