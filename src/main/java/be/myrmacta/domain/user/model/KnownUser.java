package be.myrmacta.domain.user.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Utilisateur déjà connecté à cette instance. {@code userId} est le sujet OIDC — la valeur qu'un
 * assigné Flowable porte ; {@code roles} sont ceux de la dernière connexion.
 */
public record KnownUser(
        String tenantId,
        String userId,
        String displayName,
        String email,
        Set<String> roles,
        Instant lastLoginAt) {

    public KnownUser {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(lastLoginAt, "lastLoginAt");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
