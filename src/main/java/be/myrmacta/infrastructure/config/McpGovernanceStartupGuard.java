package be.myrmacta.infrastructure.config;

import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;

/**
 * TODO §82 (D6) — garde-fous de démarrage de la gouvernance MCP :
 *
 * <ul>
 *   <li>{@code app.environment} ∈ {@code local | staging | production} (défaut {@code production}, le plus strict) — une faute de
 *       frappe ne doit pas désactiver une protection ;</li>
 *   <li>publication directe par un agent refusée en production (D-MCP-LC-PUBLISH = C) ;</li>
 *   <li>hors local, {@code app.security.m2m.trusted-client-ids} est obligatoire dès que la chaîne M2M est active : sans elle, tout
 *       porteur d'un JWT M2M valide est administrateur de fait (A3-M2, D-MCP-LC-SCOPES = B).</li>
 * </ul>
 */
public final class McpGovernanceStartupGuard implements InitializingBean {

    static final Set<String> ENVIRONMENTS = Set.of("local", "staging", "production");

    private final String environment;
    private final McpPublicationProperties publication;
    private final M2mSecurityProperties m2m;

    public McpGovernanceStartupGuard(String environment, McpPublicationProperties publication, M2mSecurityProperties m2m) {
        this.environment = environment == null ? "production" : environment.strip().toLowerCase(Locale.ROOT);
        this.publication = publication;
        this.m2m = m2m;
    }

    @Override
    public void afterPropertiesSet() {
        if (!ENVIRONMENTS.contains(environment)) {
            throw new IllegalStateException("app.environment (APP_ENVIRONMENT) must be one of " + ENVIRONMENTS + ", got '"
                    + environment + "'");
        }
        if (publication.directAllowed() && "production".equals(environment)) {
            throw new IllegalStateException("app.mcp.publication.mode=direct is refused when app.environment=production: "
                    + "an agent must not publish configuration without human review in production "
                    + "(use mode=draft, or set APP_ENVIRONMENT=staging|local)");
        }
        boolean m2mActive = notBlank(m2m.issuerUri()) || notBlank(m2m.jwtSecret());
        if (m2mActive && !"local".equals(environment) && m2m.trustedClientIds().isEmpty()) {
            throw new IllegalStateException("app.security.m2m.trusted-client-ids (M2M_TRUSTED_CLIENT_IDS) is required when "
                    + "app.environment is not 'local' and the M2M chain is active: without it any holder of a valid M2M JWT "
                    + "is a full administrator (A3-M2). List the client_id/sub allowed to use /api/m2m/mcp");
        }
    }

    public String environment() {
        return environment;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
