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

            // Décision — le claim tenant_id est obligatoire : pas de fallback
            // silencieux vers le schéma public. Voir "Résolution du tenant"
            // et "Routing du schéma PostgreSQL" ci-dessous.
            if (tenantId == null || tenantId.isBlank()) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "tenant_id claim manquant");
                return;
            }

            tenantContext.setTenantId(tenantId);
        }

        chain.doFilter(req, res);
        // Pas de clear() nécessaire — le bean est détruit en fin de requête
    }
}
```

### Décision — claim `tenant_id` absent

**Problème identifié en revue :** sans rejet explicite, une requête authentifiée mais dépourvue du claim `tenant_id` retombait sur le fallback `"public"` du `TenantIdentifierResolver`, rendant le schéma public accessible depuis l'API.

**Décision retenue :** le `TenantResolutionFilter` rejette la requête avec un `403` dès que le claim est absent ou vide (voir code ci-dessus). Le schéma `public` est réservé à un usage strictement technique — bootstrap Liquibase, jobs internes exécutés hors contexte HTTP — et n'est jamais atteint par déduction d'un tenant `null`. C'est pourquoi `TenantIdentifierResolver` (ci-dessous) lève une exception plutôt que de retourner `"public"` par défaut.

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
        // Décision — pas de fallback silencieux vers "public" : un contexte
        // manquant est une erreur de programmation ou une requête qui n'aurait
        // pas dû atteindre cette couche (voir TenantResolutionFilter).
        return Optional.ofNullable(tenantContext.getTenantId())
            .orElseThrow(MissingTenantContextException::new);
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
    private final TenantRegistry tenantRegistry; // registre des tenants actifs, en cache

    @Override
    public Connection getConnection(String tenantId) throws SQLException {
        // Décision — défense en profondeur contre l'injection SQL via tenantId :
        // 1. le tenantId est déjà validé par regex à sa création (voir plus bas) ;
        // 2. on revérifie ici qu'il correspond à un tenant actif connu (pas de
        //    confiance aveugle dans le claim JWT, même signé) ;
        // 3. l'identifiant est échappé via enquoteIdentifier() plutôt que concaténé.
        if (!tenantRegistry.isActiveTenant(tenantId)) {
            throw new UnknownTenantException(tenantId);
        }

        Connection conn = dataSource.getConnection();
        String schema = conn.enquoteIdentifier(tenantId, true);
        conn.createStatement()
            .execute("SET search_path TO " + schema + ", public");
        return conn;
    }

    @Override
    public void releaseConnection(String tenantId, Connection conn)
            throws SQLException {
        try {
            conn.createStatement().execute("SET search_path TO public");
        } finally {
            conn.close(); // toujours fermer, même si le reset échoue
        }
    }
}
```

### Décision — validation du `tenantId`

**Problème identifié en revue :** le `tenantId` était concaténé tel quel dans `SET search_path TO {tenantId}, public`, sans sanitisation — un `tenantId` malformé ou malveillant pouvait constituer une injection SQL.

**Décision retenue — double barrière :**

1. **À la création du tenant** (source unique de vérité) : validation par expression régulière `^[a-z][a-z0-9_]{2,62}$`, alignée sur les contraintes d'identifiant PostgreSQL (minuscules, chiffres, underscore, 63 caractères maximum).
2. **À chaque résolution de requête** (défense en profondeur) : vérification que le `tenantId` porté par le claim OIDC correspond à un tenant actif connu du `TenantRegistry` (registre en cache, rafraîchi périodiquement depuis la table des tenants) — un claim signé n'est pas une garantie suffisante si le mapping du claim côté IdP venait à être mal configuré.

De plus, l'identifiant est échappé via `Connection.enquoteIdentifier()` (JDBC 4.3+) avant d'être injecté dans le `SET search_path`, plutôt qu'une simple concaténation de chaîne — les identifiants SQL ne pouvant pas être paramétrés via `PreparedStatement`, l'échappement explicite est la protection appropriée en complément de la validation.

```java
public final class TenantIdValidator {

    private static final Pattern TENANT_ID_PATTERN =
        Pattern.compile("^[a-z][a-z0-9_]{2,62}$");

    public static void validate(String tenantId) {
        if (tenantId == null || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new InvalidTenantIdException(tenantId);
        }
    }
}
```

### Robustesse du `search_path` face au pool de connexions

Le `search_path` est un état de **session PostgreSQL**, pas de la transaction JDBC. Deux risques doivent être traités explicitement, faute de quoi une connexion peut être rendue au pool avec le `search_path` d'un autre tenant :

| Risque | Cause | Mitigation retenue |
|---|---|---|
| Connexion rendue au pool sans reset | Exception levée entre `getConnection()` et `releaseConnection()` (ex. requête en erreur) avant que Hibernate n'appelle le reset | `releaseConnection()` doit toujours exécuter le `SET search_path TO public` dans un bloc `try/finally`, y compris en cas d'échec de la requête métier |
| Connexion physique réutilisée par Hikari sans repasser par `releaseConnection()` | Fermeture anormale de la connexion applicative (timeout, crash de thread) sans que Hibernate ne rende la main au provider | `HikariConfig.connectionInitSql` / `connectionTestQuery` positionné à `SET search_path TO public` pour garantir un état neutre à chaque emprunt du pool, en complément du reset applicatif |
| Connexion validée par Hikari sans revalidation du schéma | `dataSource.getConnection()` peut retourner une connexion physique déjà utilisée par un tenant précédent | `getConnection(tenantId)` **doit toujours réémettre** `SET search_path` avant tout usage, jamais supposer un état neutre par défaut (déjà le cas dans l'implémentation ci-dessus — à ne jamais optimiser en le rendant conditionnel) |

> **Recommandation de test** — ce comportement doit être couvert par un test d'intégration dédié : ouvrir/fermer des connexions pour deux tenants en boucle serrée (y compris avec des requêtes en échec) et vérifier qu'aucune donnée croisée n'est lisible. Un test manuel ou une revue de code ne suffisent pas à garantir l'absence de fuite.

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

### Bootstrap et migration Liquibase multi-tenant

**Décision retenue :** deux contextes Liquibase distincts partagent le même changelog maître (`db/changelog/tenant/changelog-master.xml`) :

| Contexte | Déclenché par | Portée |
|---|---|---|
| `tenant-init` | Onboarding d'un nouveau tenant | Changelog complet, exécuté une seule fois sur le nouveau schéma |
| `tenant-migration` | Déploiement applicatif | Changesets incrémentaux, appliqués à tous les schémas de tenants existants |

```java
// Onboarding — création du schéma + changelog complet en contexte tenant-init
public void onboardTenant(String tenantId) {
    TenantIdValidator.validate(tenantId);
    createSchemaIfAbsent(tenantId);

    Liquibase liquibase = new Liquibase(
        "db/changelog/tenant/changelog-master.xml",
        new ClassLoaderResourceAccessor(),
        jdbcConnectionFor(tenantId));
    liquibase.update(new Contexts("tenant-init"), new LabelExpression());

    tenantRegistry.registerActive(tenantId);
}
```

**Exécution en Job dédié, pas depuis les pods applicatifs.** La migration de l'ensemble des tenants existants (contexte `tenant-migration`) est déclenchée par un Job Kubernetes (hook Helm `pre-upgrade`), exécuté **avant** le démarrage des nouveaux pods applicatifs. Cela élimine tout risque de migrations concurrentes lancées par plusieurs instances au démarrage, chaque schéma ayant son propre verrou Liquibase (`DATABASECHANGELOGLOCK`) mais celui-ci ne protégeant pas contre des instances migrant des tenants différents en parallèle de façon incontrôlée.

**Gestion des erreurs partielles.** Une table de contrôle `platform.tenant_migration_status(tenant_id, target_version, status, error_message, updated_at)` trace l'état de chaque tenant. Si la migration d'un tenant échoue :

- le tenant est marqué `MIGRATING_ERROR` ;
- les requêtes API pour ce tenant sont bloquées (503) via une vérification dans le `TenantResolutionFilter`, le temps de la correction ;
- les autres tenants ne sont pas impactés — le Job continue sur les tenants suivants ;
- le Job est idempotent et peut être rejoué en ciblant uniquement les tenants en `MIGRATING_ERROR`.

### Synthèse des choix multi-tenant

| Couche | Choix |
|---|---|
| Isolation données | Schéma PostgreSQL par tenant |
| Résolution tenant | Claim `tenant_id` dans le token OIDC — obligatoire, requête rejetée (403) si absent |
| Validation `tenantId` | Regex à la création + vérification contre le registre des tenants actifs à chaque requête + `enquoteIdentifier()` |
| Provider OIDC | Realm Keycloak partagé + claim custom |
| Propagation Spring | Bean `@RequestScope` — compatible Virtual Threads |
| Flowable | `tenantId` natif sur déploiements et instances |
| Migration Liquibase | Contexts `tenant-init` / `tenant-migration`, Job Kubernetes dédié, suivi par tenant en base |

> **Évolution future** — pour les traitements hors contexte HTTP (batch Flowable, jobs asynchrones), `ScopedValue` (Java 22+) est la voie naturelle pour propager le tenant sans `@RequestScope`.

---
