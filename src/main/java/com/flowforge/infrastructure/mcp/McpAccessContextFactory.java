package com.flowforge.infrastructure.mcp;

import com.flowforge.domain.dossier.model.AccessContext;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * JWT M2M → {@link AccessContext.User} avec le rôle {@code API_SERVICE} (D-ACTOR). Jamais
 * {@link AccessContext.System} ni {@link AccessContext.Service} : un appel MCP est toujours
 * attribuable à un acteur, même par défaut ({@code "api-service"}).
 */
@Component
public class McpAccessContextFactory {

    private static final String DEFAULT_ACTOR_ID = "api-service";
    private static final Set<String> ROLES = Set.of("API_SERVICE");

    public AccessContext.User from(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        return AccessContext.user(actorId(jwt), ROLES);
    }

    private static String actorId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        String clientId = jwt.getClaimAsString("client_id");
        if (clientId != null && !clientId.isBlank()) {
            return clientId;
        }
        return DEFAULT_ACTOR_ID;
    }
}
