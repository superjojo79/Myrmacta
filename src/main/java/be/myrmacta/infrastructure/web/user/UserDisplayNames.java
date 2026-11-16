package be.myrmacta.infrastructure.web.user;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * TODO §145 / §146 — noms affichés depuis l'annuaire local (D-ASSIGN-DIR) : partagé par le journal des brouillons
 * et les décisions de pré-vérification. Repli sur le sujet OIDC si l'utilisateur est inconnu.
 */
public final class UserDisplayNames {

    private final String tenantId;
    private final KnownUserRepository knownUsers;

    public UserDisplayNames(TenantProperties tenant, KnownUserRepository knownUsers) {
        this.tenantId = Objects.requireNonNull(tenant, "tenant").id();
        this.knownUsers = Objects.requireNonNull(knownUsers, "knownUsers");
    }

    /** Carte sujet → nom ; un seul chargement pour enrichir une liste. */
    public Map<String, String> asMap() {
        return knownUsers.findAll(tenantId).stream()
                .collect(Collectors.toMap(KnownUser::userId, KnownUser::displayName, (first, second) -> first));
    }

    /** Nom pour un sujet, ou le sujet lui-même si absent de l'annuaire ; {@code null} reste {@code null}. */
    public String resolve(String subject, Map<String, String> names) {
        if (subject == null) {
            return null;
        }
        return names.getOrDefault(subject, subject);
    }
}
