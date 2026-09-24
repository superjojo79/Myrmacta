package com.flowforge.domain.dossier.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public sealed interface AccessContext
        permits AccessContext.User, AccessContext.System, AccessContext.Service, AccessContext.External {

    static User user(String userId, Set<String> roles) {
        return new User(userId, roles);
    }

    static System system(String reason) {
        return new System(reason);
    }

    static Service service(String clientId) {
        return new Service(clientId);
    }

    static External external(UUID portalLinkId) {
        return new External(portalLinkId);
    }

    record User(String userId, Set<String> roles) implements AccessContext {
        public User {
            Objects.requireNonNull(userId, "userId");
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }

        public boolean hasRole(String role) {
            return roles.contains(role);
        }
    }

    record System(String reason) implements AccessContext {
        public System {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Service(String clientId) implements AccessContext {
        public Service {
            Objects.requireNonNull(clientId, "clientId");
        }
    }

    /**
     * Tiers externe authentifié par un lien portail (TODO §68, D-PORTAL-ACTOR). Aucun rôle V1 (H5) :
     * chaque politique d'accès doit le refuser explicitement — seul le use case portail, qui a déjà
     * validé le lien, l'utilise pour tracer {@code addedBy = portal:<linkId>}.
     */
    record External(UUID portalLinkId) implements AccessContext {
        public External {
            Objects.requireNonNull(portalLinkId, "portalLinkId");
        }

        public String actorId() {
            return "portal:" + portalLinkId;
        }
    }
}
