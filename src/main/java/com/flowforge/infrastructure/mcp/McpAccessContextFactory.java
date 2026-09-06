package com.flowforge.infrastructure.mcp;

import com.flowforge.domain.dossier.model.AccessContext;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * JWT M2M → {@link AccessContext.User} avec le rôle {@code API_SERVICE} (D-ACTOR). Jamais
 * {@link AccessContext.System} ni {@link AccessContext.Service} : un appel MCP est toujours
 * attribuable à un acteur, même par défaut ({@code "api-service"}).
 *
 * <p><b>Risque accepté (revue adversariale, extension-mcp-flux-metier)</b> : {@code ROLES} est une
 * constante — tout porteur d'un JWT M2M valide (déjà authentifié par la chaîne Spring Security
 * {@code /api/m2m/**}) reçoit {@code API_SERVICE}, sans distinction entre clients M2M ni lecture
 * de scopes réels du token. Combiné à {@code TenantAdminGuard}, qui traite {@code API_SERVICE}
 * comme équivalent à {@code TENANT_ADMIN}/{@code WORKFLOW_ADMIN} sur les use cases connecteurs et
 * questions de tâche, cela signifie que <b>tout</b> client M2M autorisé devient de facto
 * administrateur complet de ces ressources — il n'existe aujourd'hui aucun moyen d'émettre un
 * identifiant M2M à privilège réduit (ex. lecture seule) qui ne bénéficie pas de ce niveau
 * d'accès dès qu'il passe par MCP. Décision assumée pour cette itération (un seul client M2M de
 * confiance est prévu) plutôt que de dériver les rôles des claims réels du JWT (scope
 * — changement plus large, hors périmètre) : si plusieurs clients M2M à portées différentes sont
 * introduits, ce point doit être revisité avant de leur donner accès à {@code /api/m2m/mcp}.
 */
@Component
public class McpAccessContextFactory {

    private static final String DEFAULT_ACTOR_ID = "api-service";
    private static final Set<String> ROLES = Set.of("API_SERVICE");

    public AccessContext.User from(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        return AccessContext.user(actorId(jwt), ROLES);
    }

    /**
     * Reads the JWT of the current request from Spring Security's context — the M2M security
     * chain ({@code /api/m2m/**}, Order 1) authenticates the MCP Streamable HTTP request before
     * the tool method runs, on the same thread (WebMVC, {@code type: SYNC}).
     */
    public AccessContext.User currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("MCP tool invoked without an authenticated M2M JWT");
        }
        return from(jwtAuth.getToken());
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
