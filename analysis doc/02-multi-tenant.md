# Multi-tenant

> Isolation par schéma PostgreSQL. Le tenant est résolu depuis le token OIDC
> et propagé via un bean @RequestScope compatible Virtual Threads.

---

### Stratégie d'isolation retenue : schéma PostgreSQL par tenant

```
PostgreSQL (instance unique)
├── schema: tenant_a
│   ├── ACT_RU_*, ACT_HI_*   (Flowable runtime + historique)
│   └── tables métier
└── schema: tenant_b
    ├── ACT_RU_*, ACT_HI_*
    └── tables métier
```

Bon équilibre entre isolation des données, simplicité opérationnelle et support natif Flowable. Migration Liquibase exécutée par schéma à la création de chaque tenant — les changesets idempotents et le système de `contexts` permettent de distinguer l'initialisation d'un nouveau tenant des migrations appliquées à l'ensemble des tenants existants lors d'un déploiement.

### Résolution du tenant — claim OIDC

Le `tenant_id` est porté comme **claim custom dans le token OIDC**, ajouté par le provider (mapper Keycloak ou équivalent). Il est résolu une fois à l'entrée de chaque requête et propagé via un bean `@RequestScope`.

```json
{
  "sub": "user-123",
  "email": "user@tenant-a.com",
  "tenant_id": "tenant_a",
  "realm_access": { "roles": ["WORKFLOW_ADMIN"] }
}
```

### Provider OIDC — Keycloak (realm partagé)

Un realm partagé avec un claim `tenant_id` par utilisateur. Évolutif vers des realms dédiés si un tenant exige une configuration SSO spécifique (Azure AD, Google Workspace…).

### Propagation du tenant — `@RequestScope`

`@RequestScope` est le choix retenu plutôt que `ThreadLocal` — bean Spring géré par le conteneur, compatible Virtual Threads Java 21 sans friction, cycle de vie garanti par la requête HTTP.

```java
// Bean request-scoped — une instance par requête HTTP
@Component
@RequestScope
public class TenantContext {
    private String tenantId;

    public String getTenantId()          { return tenantId; }
    public void   setTenantId(String id) { this.tenantId = id; }
}

// Filtre — résolution du tenant depuis le token OIDC en début de requête
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantContext tenantContext;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
            HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder
            .getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof OidcUser user) {
            String tenantId = user.getClaimAsString("tenant_id");
            tenantContext.setTenantId(tenantId);
        }

        chain.doFilter(req, res);
        // Pas de clear() nécessaire — le bean est détruit en fin de requête
    }
}
```

### Routing du schéma PostgreSQL

```java
// CurrentTenantIdentifierResolver Hibernate — lit le bean request-scoped
@Component
@RequiredArgsConstructor
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String> {

    private final TenantContext tenantContext;

    @Override
    public String resolveCurrentTenantIdentifier() {
        String id = tenantContext.getTenantId();
        return id != null ? id : "public"; // fallback schéma public
    }

    @Override
    public boolean validateExistingCurrentSessions() { return true; }
}

// MultiTenantConnectionProvider — switche le search_path PostgreSQL
@Component
@RequiredArgsConstructor
public class SchemaMultiTenantConnectionProvider
        implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    @Override
    public Connection getConnection(String tenantId) throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.createStatement()
            .execute("SET search_path TO " + tenantId + ", public");
        return conn;
    }

    @Override
    public void releaseConnection(String tenantId, Connection conn)
            throws SQLException {
        conn.createStatement().execute("SET search_path TO public");
        conn.close();
    }
}
```

### Flowable — `tenantId` natif

```java
// Déploiement d'un processus pour un tenant
repositoryService.createDeployment()
    .tenantId(tenantContext.getTenantId())
    .addClasspathResource("processes/mon-workflow.bpmn20.xml")
    .deploy();

// Démarrage d'une instance de processus
runtimeService.createProcessInstanceBuilder()
    .processDefinitionKey("mon-workflow")
    .tenantId(tenantContext.getTenantId())
    .start();
```

### Synthèse des choix multi-tenant

| Couche | Choix |
|---|---|
| Isolation données | Schéma PostgreSQL par tenant |
| Résolution tenant | Claim `tenant_id` dans le token OIDC |
| Provider OIDC | Realm Keycloak partagé + claim custom |
| Propagation Spring | Bean `@RequestScope` — compatible Virtual Threads |
| Flowable | `tenantId` natif sur déploiements et instances |

> **Évolution future** — pour les traitements hors contexte HTTP (batch Flowable, jobs asynchrones), `ScopedValue` (Java 22+) est la voie naturelle pour propager le tenant sans `@RequestScope`.

---
