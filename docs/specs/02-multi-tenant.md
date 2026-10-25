# Multi-tenant

> Isolation par schéma PostgreSQL. Chaque tenant est servi par sa propre instance Spring Boot,
> configurée avec son identifiant au démarrage — décision n° 13, [registre des décisions](../decisions/013-modele-de-deploiement-multi-tenant-instance-dediee.md).

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

Bon équilibre entre isolation des données, simplicité opérationnelle et support natif Flowable.

### Modèle de déploiement — une instance dédiée par tenant

**Décision retenue (n° 13).** Chaque tenant est servi par sa **propre instance Spring Boot**,
démarrée séparément — port ou serveur distinct — et configurée avec son identifiant de tenant en dur
au démarrage. Toutes les instances partagent la même base PostgreSQL multi-schéma, mais chaque
instance n'ouvre jamais qu'une connexion vers son propre schéma, fixé une fois pour toutes. Il
n'existe pas de mode alternatif où une même instance servirait plusieurs tenants.

Le tenant devient une **propriété de configuration**, lue une fois au démarrage de la JVM, jamais
une donnée résolue dynamiquement par requête :

```java
@ConfigurationProperties(prefix = "app.tenant")
public record TenantProperties(String id) {}
```

```yaml
app:
  tenant:
    id: tenant_a   # unique par instance déployée — jamais résolu dynamiquement
```

`TenantProperties` est un bean singleton ordinaire : il est disponible sans distinction dans les
threads de requête HTTP, les `JavaDelegate` exécutés par le job executor asynchrone de Flowable, le
scheduler du relais outbox, ou tout autre composant — il n'y a plus de contexte à propager, puisqu'il
n'y a plus rien qui varie d'une exécution à l'autre au sein d'un même process.

**Pourquoi ce choix plutôt qu'un mécanisme de propagation (`ThreadLocal`, `ScopedValue`).** Le
problème initial — certains composants (relais outbox, `JavaDelegate` Flowable, listeners
d'événements, job de migration) s'exécutent hors requête HTTP et ne pouvaient pas résoudre leur
tenant via un bean `@RequestScope` — n'est pas résolu en rendant la propagation plus sûre, mais en
supprimant le besoin de propager quoi que ce soit : le tenant ne varie plus au sein d'un process, il
n'y a donc plus d'endroit où un oubli de propagation peut se reproduire.

Le routage vers la bonne instance se fait par **chemin**, via une configuration statique de
reverse-proxy — voir « Routage vers l'instance » ci-dessous.

**L'onboarding d'un nouveau tenant** devient une opération de provisioning à part entière — création
du schéma, génération de la configuration de la nouvelle instance, démarrage de celle-ci, ajout de la
règle de routage correspondante — plutôt qu'un simple appel applicatif. L'outillage de ce
provisioning n'est pas encore spécifié (voir point 31, découpage en lots).

**Scaling horizontal d'un tenant — chemin d'évolution, pas un défaut.** Si la charge d'un tenant
donné le justifie, plusieurs répliques de son instance dédiée peuvent être démarrées derrière un
répartiteur de charge propre à ce tenant. Cela ne concerne alors que ce tenant : aucune coordination
inter-tenants n'est nécessaire. Ce scaling réintroduit, mais seulement dans le pool de répliques de
ce tenant, les questions de session HTTP partagée (voir
[03-authentification.md](03-authentification.md)) et de diffusion SSE inter-répliques (voir
[09-notifications.md](09-notifications.md)) — non traitées par défaut, à activer au cas par cas.

### Routage vers l'instance — chemin par tenant, reverse-proxy statique

**Décision retenue (point 14, [registre des décisions](../decisions/014-resolution-du-tenant-pour-les-appels-non.md)).** Le routage vers la bonne instance se fait
par **chemin** — chaque tenant est adressé sous un préfixe d'URL qui lui est propre
(`https://example.com/tenant_a/...`) — plutôt que par sous-domaine ou par port. La configuration du
reverse-proxy est **statique** : un bloc par tenant, ajouté à la configuration du serveur lors du
provisioning, sans annuaire consulté dynamiquement à chaque requête.

```apache
# httpd.conf — un bloc par tenant, ajouté au provisioning
ProxyPass        "/tenant_a" "http://127.0.0.1:8081/tenant_a"
ProxyPassReverse "/tenant_a" "http://127.0.0.1:8081/tenant_a"

ProxyPass        "/tenant_b" "http://127.0.0.1:8082/tenant_b"
ProxyPassReverse "/tenant_b" "http://127.0.0.1:8082/tenant_b"
```

Chaque instance sert nativement sous ce même préfixe, via le mécanisme standard de Spring Boot — pas
de logique applicative dédiée au multi-tenant dans le routage :

```yaml
server:
  servlet:
    context-path: /${app.tenant.id}    # ex. /tenant_a — identique au préfixe du reverse-proxy
  forward-headers-strategy: framework  # honore X-Forwarded-* pour les URLs absolues générées
                                        # (redirect_uri OAuth2, cookies) — voir 03-authentification.md
```

Le préfixe n'est donc jamais réécrit ni retiré par le proxy : la même URL, préfixe inclus, désigne
la ressource côté client comme côté serveur. Cela couvre uniformément le trafic humain (SPA,
redirection OAuth2 — voir [03-authentification.md](03-authentification.md)) et les appels non
authentifiés (webhooks, API machine-to-machine — voir [06-connecteurs.md](06-connecteurs.md)), sans
mécanisme de routage distinct pour chacun.

**Pourquoi ce choix plutôt qu'un sous-domaine ou un port dédié.** Un sous-domaine par tenant
(`tenant_a.example.com`) impose une gestion de certificat (wildcard ou émission automatisée par
tenant) ; un port dédié se heurte aux pare-feux des systèmes tiers, qui n'autorisent souvent que
80/443. Le chemin conserve un hostname et un certificat uniques, avec une configuration de
reverse-proxy volontairement simple — un bloc statique par tenant, sans composant de routage
dynamique supplémentaire à opérer.

**Ce que ça coûte.** L'onboarding d'un tenant ajoute une étape à l'outillage de provisioning :
générer et déployer le bloc de configuration du reverse-proxy correspondant, en plus de la création
du schéma et du démarrage de l'instance. Le rechargement du reverse-proxy (`apachectl graceful` ou
équivalent) doit être inclus dans ce processus, sans interrompre le trafic des autres tenants.

### Résolution du tenant — vérification d'identité, pas sélection de schéma

Le `tenant_id` reste porté comme **claim custom dans le token OIDC**, ajouté par le provider (mapper
Keycloak ou équivalent) :

```json
{
  "sub": "user-123",
  "email": "user@tenant-a.com",
  "tenant_id": "tenant_a",
  "realm_access": { "roles": ["WORKFLOW_ADMIN"] }
}
```

Contrairement au modèle envisagé avant la décision n° 13, ce claim ne sert plus à **sélectionner**
un schéma — le schéma est fixé une fois pour toutes par la configuration de l'instance
(`app.tenant.id`). Il sert à **vérifier** que l'utilisateur authentifié appartient bien au tenant que
cette instance sert, et à rejeter dans le cas contraire — un utilisateur du tenant B ne doit jamais
être traité comme faisant partie des données du tenant A, même si son authentification auprès du
realm partagé a réussi.

```java
// Filtre — vérifie que l'utilisateur authentifié appartient au tenant de CETTE instance.
// Ne sélectionne plus de schéma : la connexion est déjà fixée par la configuration.
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantProperties tenantProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
            HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder
            .getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof OidcUser user) {
            String claimedTenantId = user.getClaimAsString("tenant_id");

            // Décision — le claim tenant_id doit être présent et correspondre au tenant
            // configuré de cette instance, sinon rejet. Pas de fallback silencieux.
            if (!tenantProperties.id().equals(claimedTenantId)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "tenant_id claim absent ou ne correspond pas à cette instance");
                return;
            }
        }

        chain.doFilter(req, res);
    }
}
```

### Provider OIDC — Keycloak (realm partagé ou realm par tenant)

Un realm partagé avec un claim `tenant_id` par utilisateur. Évolutif vers des realms dédiés si un
tenant exige une configuration SSO spécifique (Azure AD, Google Workspace…).

**Realm par tenant (TODO §103, D-BEAUTH-REALM).** C'est désormais le modèle par défaut en production :
`scripts/provision-tenant.sh --realm-per-tenant` génère le realm `flowforge-<tenant>` (client BFF, rôles, profil,
fournisseurs FAS et itsme), et l'instance pointe son `OIDC_ISSUER_URI` dessus. Chaque tenant a ses propres clés
(`private_key_jwt` signé avec la clé du realm), sa page de connexion et ses comptes, ce qu'exigent des contrats FAS ou
itsme propres à chaque commune. Le claim `tenant_id` reste vérifié de la même façon : un compte créé par un fournisseur
d'identité n'en a pas tant qu'un admin ne l'a pas validé, et il est refusé. Le realm partagé reste possible (dev,
déploiements existants). Voir [DEPLOIEMENT.md §19](../guides/DEPLOIEMENT.md#19-fournisseurs-didentité-belges-fas-itsme--todo-103).

### Connexion à la base — schéma fixé à la configuration

Puisqu'une instance ne dessert jamais qu'un seul schéma, le SPI multi-tenant d'Hibernate
(`CurrentTenantIdentifierResolver`, `MultiTenantConnectionProvider`) n'est plus nécessaire : le
`search_path` est fixé une fois à l'ouverture du pool de connexions, jamais rebasculé.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db-host:5432/flowforge
    hikari:
      connection-init-sql: SET search_path TO ${app.tenant.id}, public
```

Chaque connexion du pool porte donc le même `search_path` pendant toute la durée de vie du pool —
il n'existe plus de scénario où une connexion pourrait être rendue avec le `search_path` d'un autre
tenant, puisqu'aucun autre tenant n'est jamais atteint par cette instance. La configuration Hibernate
redevient une configuration JPA ordinaire, sans multi-tenancy.

`search_path` n'est **pas** une barrière d'autorisation. Un rôle PostgreSQL superuser (ou un rôle
avec `USAGE` sur tous les schémas) peut lire un autre schéma même si son `search_path` pointe
ailleurs. **D-41 :** chaque tenant a un rôle **LOGIN** dédié, avec `GRANT` uniquement sur son
schéma. Le superuser du conteneur (`POSTGRES_USER`, défaut `flowforge` en local) sert au bootstrap
(init SQL, éventuellement ops) ; l'application se connecte avec `DB_USERNAME`
(`flowforge_tenant_dev` en local). Voir
[`docker/postgres/init/01-init-tenant-schema.sql`](../../docker/postgres/init/01-init-tenant-schema.sql).
Un volume Postgres déjà initialisé **ne rejoue pas** ce script : `docker compose down -v` puis
`up -d`. Le provisioning d'un second tenant (CLI / orchestrateur) reste hors V1 (point 31).

### Validation du `tenantId` — au provisioning, plus à chaque requête

**Contexte.** Dans le modèle abandonné, le `tenantId` porté par le claim JWT était utilisé pour
sélectionner dynamiquement un schéma à chaque requête, ce qui imposait une double barrière
(validation + registre des tenants actifs consulté à chaque résolution) contre le risque d'injection
dans le `search_path`. Avec la décision n° 13, le nom du schéma n'est plus jamais dérivé d'une
requête utilisateur : il est fixé une fois par la configuration de déploiement de l'instance.

**Ce qui reste nécessaire.** La validation par expression régulière reste pertinente **au moment du
provisioning** — quand un outil crée un nouveau schéma pour un nouveau tenant et génère la
configuration de la future instance — pour garantir un identifiant conforme aux contraintes
PostgreSQL et éviter qu'une valeur malformée ne se retrouve dans la configuration de déploiement.

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

L'échappement via `Connection.enquoteIdentifier()` reste une bonne pratique lorsque l'identifiant est
composé dans une instruction SQL (bootstrap, `CREATE SCHEMA` — voir ci-dessous), mais ne défend plus
contre une entrée utilisateur arrivant par requête : cette classe de risque a disparu avec
l'abandon de la sélection dynamique de schéma.

### Flowable — `tenantId` natif

```java
// Déploiement d'un processus — le tenantId est désormais une constante de configuration
repositoryService.createDeployment()
    .tenantId(tenantProperties.id())
    .addClasspathResource("processes/mon-workflow.bpmn20.xml")
    .deploy();

// Démarrage d'une instance de processus
runtimeService.createProcessInstanceBuilder()
    .processDefinitionKey("mon-workflow")
    .tenantId(tenantProperties.id())
    .start();
```

Cet usage est désormais redondant avec l'isolation par schéma — un moteur Flowable donné ne gère
jamais que les processus d'un seul tenant, quel que soit le `tenantId` qu'on lui déclare. Il est
conservé par défense en profondeur (cohérence des requêtes historiques, clarté des logs Flowable),
sans être porteur de l'isolation elle-même.

### Bootstrap et migration Liquibase — une étape de démarrage par instance

**Décision retenue.** Chaque instance migre son propre schéma à son propre démarrage, avant
d'accepter du trafic. Il n'y a plus de Job Kubernetes distinct orchestrant plusieurs schémas, ni de
distinction entre un contexte `tenant-init` et un contexte `tenant-migration` : le suivi natif de
Liquibase (`DATABASECHANGELOG`) applique déjà uniquement les changesets manquants, que le schéma soit
tout juste créé ou déjà à jour — les deux cas sont couverts par la même exécution.

```java
@Component
@RequiredArgsConstructor
public class TenantSchemaBootstrap implements ApplicationRunner {

    private final TenantProperties tenantProperties;
    private final DataSource       dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String tenantId = tenantProperties.id();
        TenantIdValidator.validate(tenantId);

        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute(
                "CREATE SCHEMA IF NOT EXISTS " + conn.enquoteIdentifier(tenantId, true));
        }

        Liquibase liquibase = new Liquibase(
            "db/changelog/tenant/changelog-master.xml",
            new ClassLoaderResourceAccessor(),
            dataSource.getConnection());
        liquibase.update(new Contexts(), new LabelExpression());
    }
}
```

Si la migration échoue, l'instance ne démarre pas — l'orchestrateur de déploiement (readiness probe,
health check) ne la met jamais en service, et son redémarrage rejoue naturellement la migration.
Il n'y a plus besoin d'une table de suivi inter-tenants pour coordonner cet aspect : chaque instance
ne rend compte que d'elle-même, via ses propres logs et son propre statut de santé. Une table
`platform.tenant_migration_status` peut néanmoins être conservée à titre d'observabilité inter-
tenants pour un tableau de bord d'exploitation (voir point 29 du [registre des décisions](../decisions/029-observabilite.md)), sans rôle de
coordination.

### Synthèse des choix multi-tenant

| Couche | Choix |
|---|---|
| Isolation données | Schéma PostgreSQL par tenant |
| Modèle de déploiement | Une instance Spring Boot dédiée par tenant (décision n° 13) |
| Résolution tenant | Propriété de configuration (`app.tenant.id`), fixée au démarrage |
| Vérification tenant | Claim `tenant_id` du token OIDC comparé au tenant configuré — rejet (403) sinon |
| Validation `tenantId` | Regex au provisioning (création du schéma) |
| Provider OIDC | Realm Keycloak partagé + claim custom |
| Connexion base | `search_path` fixé une fois via `connectionInitSql` — pas de SPI multi-tenant Hibernate |
| Flowable | `tenantId` natif conservé par défense en profondeur, redondant avec le schéma |
| Migration Liquibase | Étape de démarrage (`ApplicationRunner`) par instance, idempotente par nature |
| Scaling d'un tenant | Plusieurs répliques de sa propre instance — chemin d'évolution, pas un défaut |

---
