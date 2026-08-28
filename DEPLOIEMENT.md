# Déploiement — au-delà du poste de développement

Ce guide prend le relais de [GETTING-STARTED.md](GETTING-STARTED.md) (environnement de dev local,
infrastructure jetable, un seul tenant `tenant_dev`) pour couvrir ce qui change quand on sort de ce
cadre : construire un artefact déployable, exposer une instance derrière un reverse-proxy, faire
tourner plusieurs tenants, et gérer les comptes réels côté Keycloak.

> **Portée honnête.** Le modèle cible (une instance Spring Boot dédiée par tenant, routage par
> chemin) est **décidé** (décisions n° 13–14, [analysis doc/02-multi-tenant.md](analysis%20doc/02-multi-tenant.md)),
> mais son **outillage** ne l'est pas : il n'existe ni Dockerfile, ni CLI de provisioning tenant à ce
> jour ([TODO.md](TODO.md) points 31/41 — « pas de CLI provisioning tenant »). Ce guide documente donc
> une procédure **manuelle**, pas un outil clé en main. Ne pas lire ce document comme la preuve qu'un
> pipeline de déploiement existe déjà.

------

## 1. Construire l'artefact

```bash
./mvnw clean package
```

Le build Maven embarque le frontend automatiquement (`frontend-maven-plugin` exécute `npm ci` +
`npm run build` en phase `generate-resources`, puis `maven-resources-plugin` copie `frontend/dist`
dans `target/classes/static/`) — pas d'étape manuelle séparée comme en dev local (§7-8 de
[GETTING-STARTED.md](GETTING-STARTED.md)). Le résultat est un jar Spring Boot exécutable unique :

```bash
java -jar target/flowforge-*.jar
```

**Pas de `Dockerfile` fourni par ce dépôt.** Le déploiement conteneurisé est une cible affichée dans
[README.md](README.md#modèle-de-déploiement), mais aucune décision (image de base, utilisateur non
root, taille de heap par défaut) n'a été actée dans `TODO.md` — en produire un revient à trancher ces
points sans passer par le processus de décision du projet. Si un déploiement conteneurisé est
nécessaire dès maintenant, traiter ce Dockerfile comme n'importe quelle décision technique ouverte
(cf. `flowforge-conventions`, §« Points ouverts ») plutôt que de l'ajouter silencieusement.

------

## 2. Variables d'environnement — ce qui change par rapport au `.env.example` de dev

`.env.example` est calibré pour le dev local (`SPRING_PROFILES_ACTIVE=local`), qui autorise des
secrets de démo. Hors profils `local`/`test`, le boot **refuse de démarrer** (fail-fast, décision
D-43, [TODO.md](TODO.md) point 43) si :

- `DB_PASSWORD` est vide ou dans la denylist (`flowforge`, `postgres`, `admin`, `password`) ;
- `M2M_JWT_SECRET` (HMAC M2M) est renseigné — ce mécanisme est **interdit** hors dev/test, voir §7.

Checklist des variables à revoir pour un environnement réel :

| Variable | Dev local | Environnement réel |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | un profil dédié (≠ `local`/`test`) pour activer les garde-fous D-43 |
| `DB_PASSWORD` | `flowforge` | mot de passe fort, hors denylist |
| `APP_SECURITY_COOKIE_SECURE` | `false` (HTTP) | `true` (défaut) — exige HTTPS de bout en bout ou une terminaison TLS au reverse-proxy ; `server.forward-headers-strategy: framework` est déjà positionné pour honorer les en-têtes `X-Forwarded-*` |
| `OIDC_CLIENT_SECRET` | `dev-secret-change-me` | secret généré par le client Keycloak réel (§5) |
| `OAUTH2_ISSUER_URI` / `M2M_JWT_SECRET` | vides (M2M désactivé) ou HMAC de test | `OAUTH2_ISSUER_URI` pointant le realm Keycloak réel — jamais `M2M_JWT_SECRET` (§7) |
| `app.tenant.id` (pas une variable `.env`, propriété `application.yml`) | `tenant_dev` | un identifiant par instance, propre à chaque tenant (§4) |

------

## 3. Activer le routage par chemin (décision n° 14)

En dev local, `server.servlet.context-path` reste **commenté** dans `application.yml` (l'instance
sert sur `/`, pas de reverse-proxy). Pour la cible multi-tenant, le décommenter :

```yaml
server:
  servlet:
    context-path: /${app.tenant.id}    # ex. /tenant_a — identique au préfixe du reverse-proxy
```

puis router chaque instance depuis un reverse-proxy Apache à configuration **statique** (un bloc par
tenant, ajouté au provisioning — voir [analysis doc/02-multi-tenant.md](analysis%20doc/02-multi-tenant.md#routage-vers-linstance--chemin-par-tenant-reverse-proxy-statique)
pour le détail et la justification du choix) :

```apache
# httpd.conf — un bloc par tenant, ajouté au provisioning
ProxyPass        "/tenant_a" "http://127.0.0.1:8081/tenant_a"
ProxyPassReverse "/tenant_a" "http://127.0.0.1:8081/tenant_a"
```

Le préfixe n'est jamais réécrit par le proxy : la même URL (préfixe inclus) désigne la ressource
côté client et côté serveur. Ce mécanisme couvre uniformément le trafic humain (SPA, redirection
OAuth2), les webhooks entrants et l'API machine-to-machine (§7) — pas de routage distinct par flux.

------

## 4. Provisionner un nouveau tenant — runbook manuel

Comme rappelé en tête de ce guide, **aucun outil n'automatise cette procédure aujourd'hui** ; elle se
fait à la main, étape par étape.

1. **Choisir un identifiant de tenant** (ex. `tenant_b`), unique parmi les instances déployées.
2. **Créer le schéma et le rôle PostgreSQL dédiés.** `docker/postgres/init/01-init-tenant-schema.sql`
   sert de gabarit pour le dev local (`tenant_dev`) — le rejouer en remplaçant le nom de schéma et le
   mot de passe :
   ```sql
   CREATE SCHEMA IF NOT EXISTS tenant_b;
   CREATE ROLE flowforge_tenant_b LOGIN PASSWORD '<mot-de-passe-fort>';
   GRANT CONNECT ON DATABASE flowforge TO flowforge_tenant_b;
   REVOKE ALL ON SCHEMA tenant_b FROM PUBLIC;
   REVOKE CREATE ON SCHEMA public FROM flowforge_tenant_b;
   GRANT USAGE ON SCHEMA public TO flowforge_tenant_b;
   GRANT USAGE, CREATE ON SCHEMA tenant_b TO flowforge_tenant_b;
   GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA tenant_b TO flowforge_tenant_b;
   GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA tenant_b TO flowforge_tenant_b;
   ALTER DEFAULT PRIVILEGES IN SCHEMA tenant_b GRANT ALL ON TABLES TO flowforge_tenant_b;
   ALTER DEFAULT PRIVILEGES IN SCHEMA tenant_b GRANT ALL ON SEQUENCES TO flowforge_tenant_b;
   ```
   C'est l'option retenue par la décision D-41 (**un rôle PostgreSQL par tenant**, [TODO.md](TODO.md)
   point 41) — un seul rôle superutilisateur partagé entre tenants romprait l'isolation par schéma.
3. **Démarrer une nouvelle instance** avec sa propre configuration : `app.tenant.id=tenant_b`,
   `DB_USERNAME=flowforge_tenant_b` / `DB_PASSWORD` correspondant, un port dédié, `server.servlet.context-path=/tenant_b` (§3).
4. **Ajouter le bloc reverse-proxy** de ce tenant (§3).
5. **Si ce tenant a besoin de l'API machine-to-machine** (connecteurs, serveur MCP) : provisionner son
   client Keycloak `client_credentials` dédié (§7). Le flux humain (BFF), lui, n'a besoin d'aucun
   provisioning Keycloak supplémentaire par tenant — voir §5.

------

## 5. Keycloak en production

Le realm importé par `docker-compose.yml` ([`docker/keycloak/import/flowforge-realm.json`](docker/keycloak/import/flowforge-realm.json))
est explicitement un realm de **dev local** : `sslRequired: none`, secret client en clair
(`dev-secret-change-me`), utilisateur `demo`/`demo` pré-chargé. Ne jamais le réutiliser tel quel en
production — créer un realm dédié avec :

- **Un client BFF** (`standardFlowEnabled: true`, `publicClient: false`, secret généré) — un seul
  client suffit pour **tous** les tenants : son `redirectUris` / `post.logout.redirect.uris` en
  caractère joker (`https://votre-domaine.example/*`) couvre chaque préfixe `/tenant_x` sans
  provisioning Keycloak par tenant, le routage par chemin (§3) faisant déjà varier l'URL de callback.
- **Les 4 rôles réalm** consommés par l'UI/API FlowForge : `TENANT_ADMIN`, `WORKFLOW_ADMIN`,
  `DOSSIER_MANAGER`, `DOSSIER_VIEWER` (voir le tableau de rôles de [GUIDE-UTILISATEUR.md](GUIDE-UTILISATEUR.md#0-rôles-et-navigation)
  pour ce que chacun débloque). Le rôle `API_SERVICE` est traité séparément — ce n'est pas un rôle
  attribué à un utilisateur humain, voir §7.

------

## 6. Gérer les utilisateurs et leurs rôles

FlowForge ne fournit **aucun écran de gestion des comptes** — toute création d'utilisateur ou
attribution de rôle se fait côté Keycloak (console admin ou API Admin Keycloak), jamais dans l'UI
FlowForge elle-même. Mécanisme : le token OIDC transmis au BFF porte les rôles dans la structure
**standard** Keycloak `realm_access.roles` ; `RealmAccessAuthoritiesMapper` les convertit en
autorités `ROLE_<nom>`, ce que l'API vérifie ensuite (`hasRole('TENANT_ADMIN')`, etc.) — aucun
protocol mapper particulier à ajouter pour ce flux, contrairement au flux M2M (§7).

Procédure pour un nouvel utilisateur :

1. Console admin Keycloak → realm FlowForge → **Users** → **Add user** (username, email).
2. Onglet **Credentials** → définir un mot de passe (décocher *Temporary* pour éviter un changement
   forcé, ou le laisser coché selon la politique du client).
3. Onglet **Role mapping** → **Assign role** → cocher un ou plusieurs des 4 rôles réalm (§5). Un
   utilisateur peut cumuler plusieurs rôles (ex. l'utilisateur `demo` du realm dev a
   `TENANT_ADMIN` + `WORKFLOW_ADMIN`).
4. L'utilisateur peut se connecter dès son prochain login — pas de propagation différée côté
   FlowForge, aucun cache de session à invalider.

**Droits fins par type de dossier (ACL `VIEW`/`EDIT`)** restent, eux, gérés **dans** FlowForge
(`/admin/dossier-types`, voir §1 de [GUIDE-UTILISATEUR.md](GUIDE-UTILISATEUR.md)) — par `userId`, qui
est le `sub` Keycloak de l'utilisateur (visible dans la console admin Keycloak sur la fiche
utilisateur, ou via `GET /api/auth/me` une fois connecté).

------

## 7. Authentification machine-to-machine en production (connecteurs, serveur MCP)

`/api/m2m/**` (exécution de connecteur, serveur MCP) exige un JWT `Bearer` portant l'autorité
`API_SERVICE` et un claim `tenant_id` égal à l'`app.tenant.id` de l'instance appelée
(`M2mTenantValidationFilter`) — même garde d'appartenance que pour le flux humain, appliquée
différemment.

Deux décodeurs sont supportés (`app.security.m2m.*`, un seul actif à la fois) :

| Mécanisme | Variable | Portée |
|---|---|---|
| HMAC HS256 | `M2M_JWT_SECRET` | **dev/test uniquement** — refusé au démarrage hors profils `local`/`test` (D-43) |
| Émetteur OIDC réel | `OAUTH2_ISSUER_URI` | seul mécanisme valide en production |

Pour la production, provisionner **un client Keycloak `client_credentials` dédié par tenant** (pas le
client BFF partagé) :

1. Créer le client : `serviceAccountsEnabled: true`, `standardFlowEnabled: false`,
   `clientAuthenticatorType: client-secret`.
2. Ajouter **deux protocol mappers non standard** sur ce client (le convertisseur M2M,
   `M2mJwtAuthoritiesConverter`, ne lit pas la même structure que le flux humain §6) :
   - un mapper **Hardcoded Claim** : nom `tenant_id`, valeur = l'identifiant de ce tenant, ajouté à
     l'access token — c'est ce qui rend ce client strictement scopé à un seul tenant ;
   - un mapper qui projette les rôles dans un claim **plat** nommé `roles` (type *User Realm Role*,
     *Token Claim Name* `roles`, *Multivalued* activé, ajouté à l'access token) — `realm_access.roles`
     (structure du flux humain) n'est **pas** lu par ce convertisseur.
3. Créer le rôle réalm `API_SERVICE` (absent de l'import dev, §5) et l'assigner au compte de service
   de ce client (**Service Account Roles**, pas **Role Mapping** utilisateur).
4. Récupérer un token pour tester :
   ```bash
   curl -s -X POST "https://keycloak.example/realms/flowforge/protocol/openid-connect/token" \
     -d "grant_type=client_credentials" \
     -d "client_id=<client>" \
     -d "client_secret=<secret>"
   ```

En local, [GETTING-STARTED.md](GETTING-STARTED.md#12-serveur-mcp-outillage-agent-ia) documente le
raccourci HMAC (`M2M_JWT_SECRET`) — pratique pour tester sans Keycloak, mais à ne jamais reproduire
en production.

------

## Ce qui n'est pas (encore) couvert par ce guide

- **Aucune image ni CLI de déploiement** — voir §1 et l'avertissement de portée en tête de document.
- **Aucun outillage de provisioning tenant** — le runbook §4 est manuel ; `TODO.md` (points 31, 41)
  suit ce manque comme un point ouvert du projet, pas une simplification volontaire de ce guide.
- **Sauvegarde/restauration PostgreSQL par schéma et stratégie de montée de version** ne sont pas
  documentées ici — à traiter comme toute décision technique ouverte du projet plutôt qu'improvisées
  à la première mise en production.
- **Canal email (SMTP)** — voir §6 de [GETTING-STARTED.md](GETTING-STARTED.md).

------

## Pour aller plus loin

- Modèle multi-tenant détaillé : [analysis doc/02-multi-tenant.md](analysis%20doc/02-multi-tenant.md)
- Authentification, BFF, machine-to-machine : [analysis doc/03-authentification.md](analysis%20doc/03-authentification.md)
- Autorisation et conformité : [analysis doc/12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md)
- Décisions ouvertes (dont le provisioning tenant, points 31/41) : [TODO.md](TODO.md)
- Prise en main de l'UI : [GUIDE-UTILISATEUR.md](GUIDE-UTILISATEUR.md)
