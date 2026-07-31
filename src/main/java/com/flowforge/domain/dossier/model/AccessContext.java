package com.flowforge.domain.dossier.model;

import java.util.Objects;
import java.util.Set;

public sealed interface AccessContext
        permits AccessContext.User, AccessContext.System, AccessContext.Service {

    static User user(String userId, Set<String> roles) {
        return new User(userId, roles);
    }

    static System system(String reason) {
        return new System(reason);
    }

    static Service service(String clientId) {
        return new Service(clientId);
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
}
