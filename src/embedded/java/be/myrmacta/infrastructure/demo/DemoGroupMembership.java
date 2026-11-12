package be.myrmacta.infrastructure.demo;

import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.port.in.RecordUserLoginUseCase;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.tenant.TenantMembership;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * TODO §127 (D-DEMO-MEMBERS, lot 3 de #55) — à la connexion d'un compte de démo ({@code app.demo.group-members}, par
 * défaut {@code demo}), l'ajoute à tous les groupes de la démo : sa page « Tâches » montre alors les files des groupes
 * (tri du courrier, comptabilité…). L'identifiant d'un membre est le sujet OIDC, que Keycloak génère : la démo ne peut pas
 * le connaître à l'installation.
 * <p>
 * L'événement d'authentification précède l'inscription du compte dans l'annuaire (D-ASSIGN-DIR, faite ensuite par le
 * gestionnaire de succès) ; or un groupe n'accepte qu'un utilisateur connu. Le compte y est donc inscrit d'abord, par le
 * même cas d'usage et après le même contrôle de tenant. Un compte déjà membre n'est pas ré-ajouté ; un échec n'empêche
 * jamais la connexion. Build {@code embedded-db} seulement.
 */
@Component
@Profile({"demo", VideoMailRoomDemoPack.PROFILE})
public class DemoGroupMembership {

    private static final Logger log = LoggerFactory.getLogger(DemoGroupMembership.class);

    private final UserGroupsUseCase groups;
    private final RecordUserLoginUseCase directory;
    private final TenantMembership tenant;
    private final Set<String> members;

    public DemoGroupMembership(UserGroupsUseCase groups, RecordUserLoginUseCase directory, TenantProperties tenant,
            @Value("${app.demo.group-members:demo}") String members) {
        this.groups = groups;
        this.directory = directory;
        this.tenant = new TenantMembership(tenant);
        this.members = Arrays.stream(members.split(",")).map(String::strip).filter(m -> !m.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @EventListener
    public void onLogin(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        if (!(authentication.getPrincipal() instanceof OidcUser user) || !members.contains(user.getPreferredUsername())
                || !tenant.belongs(user)) {
            return;
        }
        try {
            directory.record(user.getSubject(), Objects.requireNonNullElse(user.getFullName(), user.getPreferredUsername()),
                    user.getEmail(), roles(authentication));
            for (UserGroup group : groups.list()) {
                if (!group.memberIds().contains(user.getSubject())) {
                    groups.addMember(group.code(), user.getSubject());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("demo: could not add the demo account to the demo groups ({})", ex.getClass().getSimpleName());
        }
    }

    private static Set<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                .filter(authority -> authority.startsWith("ROLE_")).map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
