package be.myrmacta.domain.user.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Utilisateur déjà connecté à cette instance. {@code userId} est le sujet OIDC — la valeur qu'un
 * assigné Flowable porte ; {@code roles} et {@code idpGroups} sont ceux de la dernière connexion.
 */
public record KnownUser(
        String tenantId,
        String userId,
        String displayName,
        String email,
        Set<String> roles,
        /** Groupes portés par le jeton à la dernière connexion (TODO §113, D-GROUP-SOURCE). */
        Set<String> idpGroups,
        Instant lastLoginAt) {

    public KnownUser {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(lastLoginAt, "lastLoginAt");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        idpGroups = idpGroups == null ? Set.of() : Set.copyOf(idpGroups);
    }

    public KnownUser(String tenantId, String userId, String displayName, String email, Set<String> roles, Instant lastLoginAt) {
        this(tenantId, userId, displayName, email, roles, Set.of(), lastLoginAt);
    }
}
