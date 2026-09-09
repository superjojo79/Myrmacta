package com.flowforge.infrastructure.mcp;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.infrastructure.config.M2mSecurityProperties;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
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
 * <p><b>Risque accepté, refermé partiellement (revue adversariale A3-M2)</b> : {@code ROLES} est
 * une constante — tout porteur d'un JWT M2M valide (déjà authentifié par la chaîne Spring Security
 * {@code /api/m2m/**}) reçoit {@code API_SERVICE}, sans lecture de scopes réels du token. Combiné à
 * {@code TenantAdminGuard} et à {@code DossierAccessPolicy}, qui traitent {@code API_SERVICE}
 * comme équivalent à {@code TENANT_ADMIN}/{@code WORKFLOW_ADMIN}, cela signifie que <b>tout</b>
 * client M2M autorisé devient de facto administrateur complet de ces ressources (configuration
 * <b>et</b>, depuis le lot A3, écritures métier). {@link M2mSecurityProperties#trustedClientIds()}
 * referme le risque qu'un futur second client M2M à portée réduite hérite silencieusement de ce
 * niveau d'accès : laissée vide (défaut, comportement historique), elle ne change rien — un seul
 * client M2M de confiance est prévu pour cette itération (L1 / TODO §56). Avant d'accorder l'accès
 * {@code /api/m2m/mcp} à un second client, soit renseigner cette allowlist pour restreindre
 * {@code API_SERVICE} aux {@code client_id}/{@code sub} de confiance, soit dériver le rôle effectif
 * des scopes réels du JWT (changement plus large, non fait ici).
 */
@Component
public class McpAccessContextFactory {

    private static final String DEFAULT_ACTOR_ID = "api-service";
    private static final Set<String> ROLES = Set.of("API_SERVICE");

    private final List<String> trustedClientIds;

    public McpAccessContextFactory() {
        this(new M2mSecurityProperties(null, null, List.of()));
    }

    @Autowired
    public McpAccessContextFactory(M2mSecurityProperties m2mSecurityProperties) {
        this.trustedClientIds =
                Objects.requireNonNull(m2mSecurityProperties, "m2mSecurityProperties").trustedClientIds();
    }

    public AccessContext.User from(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        assertTrustedClient(jwt);
        return AccessContext.user(actorId(jwt), ROLES);
    }

    private void assertTrustedClient(Jwt jwt) {
        if (trustedClientIds.isEmpty()) {
            return;
        }
        String clientId = jwt.getClaimAsString("client_id");
        String candidate = clientId != null && !clientId.isBlank() ? clientId : jwt.getSubject();
        if (candidate == null || !trustedClientIds.contains(candidate)) {
            throw new AccessDeniedException("M2M client not authorized for API_SERVICE role: " + candidate);
        }
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
