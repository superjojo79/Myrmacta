# Déploiement — au-delà du poste de développement

Ce guide prend le relais de [GETTING-STARTED.md](GETTING-STARTED.md) (environnement de dev local,
infrastructure jetable, un seul tenant `tenant_dev`) pour couvrir ce qui change quand on sort de ce
cadre : construire un artefact déployable, exposer une instance derrière un reverse-proxy, faire
tourner plusieurs tenants, et gérer les comptes réels côté Keycloak.

> **Portée.** Le modèle cible (une instance Spring Boot dédiée par tenant, routage par chemin) est
> **décidé** (décisions n° 13–14, [docs/specs/02-multi-tenant.md](docs/specs/02-multi-tenant.md))
> et désormais **outillé** ([TODO.md](TODO.md) points 31/41 — lot `provisioning-tenant`) : un
> `Dockerfile` à la racine du dépôt et un script `scripts/provision-tenant.sh` pour le schéma/rôle
> PostgreSQL par tenant. Le démarrage de l'instance et le bloc reverse-proxy restent des étapes
> documentées (commande / gabarit de config), pas automatisées de bout en bout — voir §4.

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

### Construire l'image conteneur

Le `Dockerfile` à la racine du dépôt construit une image exécutable en 2 stages :

```bash
docker build -t flowforge:<tag> .
```

Décisions actées pour cette image (lot `provisioning-tenant`, voir
[docs/ai/requirements/2026-09-08-feature-provisioning-tenant.md](docs/ai/requirements/2026-09-08-feature-provisioning-tenant.md)) :

| Décision | Choix |
|---|---|
| Image de base | `eclipse-temurin:25-jre` (stage runtime) — build avec `eclipse-temurin:25-jdk` (stage build) |
| Utilisateur du conteneur | Non-root dédié (`flowforge`, UID/GID 10001) |
| Heap JVM par défaut | Dynamique (`-XX:MaxRAMPercentage=75.0`), pas de `-Xmx` fixe — s'adapte à la limite mémoire du conteneur |

```bash
docker run -d --name flowforge-<tenant_id> -p 127.0.0.1:8081:8080 \
  -e APP_TENANT_ID=<tenant_id> \
  -e DB_URL=jdbc:postgresql://<host>:5432/flowforge \
  -e DB_USERNAME=flowforge_<tenant_id> -e DB_PASSWORD=<mot-de-passe-du-role> \
  -e SPRING_PROFILES_ACTIVE=<profil-non-local> \
  -e OAUTH2_ISSUER_URI=<realm-keycloak-reel> \
  flowforge:<tag>
```

Lier le port applicatif **uniquement à loopback** (`127.0.0.1:8081:8080`).
Ne pas publier `:8080` / `:8081` sur toutes les interfaces (`-p 8081:8080`) :
`server.forward-headers-strategy: framework` honore `X-Forwarded-Proto` tel quel,
et un client distant pourrait forcer HSTS 1 an + `includeSubDomains` sur du HTTP
clair (A4-HSTS-001). Le reverse-proxy local est le seul à joindre l’instance.

`JAVA_OPTS` permet d'ajuster les options JVM sans reconstruire l'image (ex. `-e
JAVA_OPTS="-Dhttps.proxyHost=..."`). `APP_TENANT_ID` (relaxed-binding Spring Boot standard vers
`app.tenant.id`) est la seule variable propre au tenant à passer au conteneur — voir §4 pour le
provisioning du schéma/rôle PostgreSQL correspondant.

> Validation de ce `Dockerfile` dans le cycle qui l'a produit : revue statique + `hadolint` (propre),
> pas de `docker build`/`docker run` réel (environnement d'exécution du cycle sans accès registre
> Docker Hub) — à vérifier par un build réel avant un premier déploiement.

------

## 2. Variables d'environnement — ce qui change par rapport au `.env.local.example` de dev

[`.env.example`](.env.example) est le gabarit d'un environnement réel : `APP_ENVIRONMENT=production`,
un profil dédié et des secrets **vides**, si bien que l'instance refuse de démarrer tant qu'ils ne
sont pas renseignés. [`.env.local.example`](.env.local.example) est calibré pour le dev local
(`SPRING_PROFILES_ACTIVE=local`), qui autorise des secrets de démo.

`APP_ENVIRONMENT` est le **seul interrupteur « mode dev »** (TODO §95) : le profil `local` est refusé
au démarrage si `APP_ENVIRONMENT` n'est pas `local`, et le profil `test` est refusé quand
`APP_ENVIRONMENT=production` (valeur par défaut). Un `.env` de dev recopié sur un serveur ne peut
donc plus relâcher les contrôles en silence.

Hors profils `local`/`test`, le boot **refuse de démarrer** (fail-fast, décision
D-43, [TODO.md](TODO.md) point 43) si :

- `DB_PASSWORD` est vide ou dans la denylist (`flowforge`, `postgres`, `admin`, `password`) ;
- `M2M_JWT_SECRET` (HMAC M2M) est renseigné — ce mécanisme est **interdit** hors dev/test, voir §7.

Checklist des variables à revoir pour un environnement réel :

| Variable | Dev local | Environnement réel |
|---|---|---|
| `APP_ENVIRONMENT` | `local` | `production` (défaut) ou `staging` — refuse le profil `local`, et le profil `test` en production (TODO §95) |
| `SPRING_PROFILES_ACTIVE` | `local` | un profil dédié (≠ `local`/`test`, ex. `prod`) pour activer les garde-fous D-43 |
| `APP_DOSSIER_ATTACHMENT_SCAN_TYPE` | `noop` | `clamav` (§8). `noop` est refusé au démarrage en `production`, sauf `APP_DOSSIER_ATTACHMENT_SCAN_NOOP_ACKNOWLEDGED=true` (pièces jointes acceptées sans analyse, risque assumé) ; en `staging`, il est signalé au démarrage (TODO §95) |
| `DB_PASSWORD` | `flowforge` | mot de passe fort, hors denylist |
| `APP_SECURITY_COOKIE_SECURE` | `false` (HTTP) | `true` (défaut) — exige HTTPS de bout en bout ou une terminaison TLS au reverse-proxy ; `server.forward-headers-strategy: framework` est déjà positionné pour honorer les en-têtes `X-Forwarded-*` **posés par le proxy de confiance** |
| `APP_SECURITY_HEADERS_ENABLED` | `true` (défaut, à laisser) | `true` — l’app émet HSTS `max-age=31536000; includeSubDomains` (pas de `preload`) hors profils `local`/`test`, seulement si la requête est HTTPS **et** que le pair TCP immédiat est dans `APP_SECURITY_HEADERS_TRUSTED_PROXY_CIDRS`. Ne pas poser un HSTS reverse-proxy **plus court** qui le contredirait. **Depuis TODO §64, `false` hors profils `local`/`test` empêche le démarrage** (fail-fast, même doctrine que `DB_PASSWORD`/§2) — ce n'est plus un coupe-circuit d'urgence. |
| `APP_SECURITY_HEADERS_TRUSTED_PROXY_CIDRS` | défaut (loopback + RFC1918) | Défaut = loopback + RFC1918, **valable pour le modèle Docker+Apache mono-host de ce guide** (§1/§3). ⚠️ Ce défaut couvre aussi les plages d'adresses typiques des bridges Docker et des CIDR de pods Kubernetes — s'il faut un jour sortir de ce modèle mono-host (sidecar, cluster où d'autres charges de travail partagent ce même réseau privé), restreindre cette liste à l'IP réelle du reverse-proxy avant la migration, sous peine de reconstituer la confiance trop large que ce point corrige. |
| `OIDC_CLIENT_SECRET` | `dev-secret-change-me` | secret généré par le client Keycloak réel (§5) |
| `OAUTH2_ISSUER_URI` / `M2M_JWT_SECRET` | vides (M2M désactivé) ou HMAC de test | `OAUTH2_ISSUER_URI` pointant le realm Keycloak réel — jamais `M2M_JWT_SECRET` (§7) |
| `APP_TENANT_ID` (relaxed-binding Spring Boot vers `app.tenant.id`, défaut `tenant_dev` dans `application.yml`) | `tenant_dev` | un identifiant par instance, propre à chaque tenant (§4) |

------

## 3. Routage par chemin (décision n° 14)

`server.servlet.context-path: /${app.tenant.id}` est **actif par défaut** dans `application.yml`
(ex. `/tenant_a` pour l'instance `app.tenant.id=tenant_a`) — seul le profil `local`
(`application-local.yml`) le neutralise pour que le poste de dev continue de servir l'application à
la racine `/`. Aucune action nécessaire pour l'activer sur une instance déployée : ne pas passer
`SPRING_PROFILES_ACTIVE=local` suffit à l'exposer sous son préfixe de tenant.

Le SPA servi par l'instance reçoit `<base href="/{context-path}/">` (réécrit au service
d'`index.html`) : assets, routeur et appels API se résolvent contre ce préfixe, liens profonds et
rechargements compris. Le reverse-proxy doit donc publier l'instance **sous le même chemin** que son
context-path (`/tenant_a` → `…/tenant_a`, comme ci-dessous) — jamais en retirant ou réécrivant le
préfixe.

Router ensuite chaque instance depuis un reverse-proxy Apache à configuration **statique** (un bloc par
tenant, ajouté au provisioning — voir [docs/specs/02-multi-tenant.md](docs/specs/02-multi-tenant.md#routage-vers-linstance--chemin-par-tenant-reverse-proxy-statique)
pour le détail et la justification du choix) :

```apache
# httpd.conf — un bloc par tenant, ajouté au provisioning
# Écraser les X-Forwarded-* clients (mod_headers) puis poser proto=https.
# Sans ça, un client peut envoyer X-Forwarded-Proto: https sur HTTP clair.
RequestHeader unset X-Forwarded-Proto early
RequestHeader unset X-Forwarded-For early
RequestHeader unset X-Forwarded-Host early
RequestHeader unset X-Forwarded-Port early
# RFC 7239 : ForwardedHeaderFilter préfère « Forwarded » (for/proto/host) aux X-Forwarded-*.
RequestHeader unset Forwarded early
RequestHeader unset X-Forwarded-Prefix early
RequestHeader set X-Forwarded-Proto "https"
RequestHeader set X-Forwarded-For "%{REMOTE_ADDR}s"
ProxyPass        "/tenant_a" "http://127.0.0.1:8081/tenant_a"
ProxyPassReverse "/tenant_a" "http://127.0.0.1:8081/tenant_a"
```

Le préfixe n'est jamais réécrit par le proxy : la même URL (préfixe inclus) désigne la ressource
côté client et côté serveur. Ce mécanisme couvre uniformément le trafic humain (SPA, redirection
OAuth2), les webhooks entrants et l'API machine-to-machine (§7) — pas de routage distinct par flux.

> **Rapports CSP (TODO §64).** La CSP applicative pointe un endpoint anonyme et rate-limité
> (`POST .../api/security/csp-reports`, `APP_SECURITY_HEADERS_CSP_REPORT_RATE_LIMIT` par pair TCP,
> défaut 60/min) qui journalise les violations tronquées via `SECURITY_AUDIT`. Aucune action
> opérationnelle requise (même routage par chemin que le reste), mais à connaître pour le
> dimensionnement des logs si le shipping de `SECURITY_AUDIT` est agrégé/alerté.

------

## 4. Provisionner un nouveau tenant

1. **Choisir un identifiant de tenant** (ex. `tenant_b`), unique parmi les instances déployées —
   doit matcher `^[a-z][a-z0-9_]{2,52}$` (3 à 53 caractères — le rôle généré préfixe
   `flowforge_`, le nom de rôle résultant doit rester ≤ 63 octets).
2. **Créer le schéma et le rôle PostgreSQL dédiés** avec `scripts/provision-tenant.sh` — reproduit
   paramétré le gabarit `docker/postgres/init/01-init-tenant-schema.sql` (celui utilisé pour
   `tenant_dev` en dev local) :
   ```bash
   # Revue du SQL avant application (recommandé) :
   TENANT_DB_PASSWORD='<mot-de-passe-fort>' scripts/provision-tenant.sh tenant_b

   # Application directe contre PostgreSQL (superuser de provisioning) :
   TENANT_DB_PASSWORD='<mot-de-passe-fort>' \
     PGHOST=<host> PGPORT=5432 PGDATABASE=flowforge PGUSER=<superuser> PGPASSWORD=<mdp-superuser> \
     scripts/provision-tenant.sh tenant_b --apply
   ```
   Échoue explicitement si le schéma `tenant_b` existe déjà (pas d'écrasement). C'est l'option
   retenue par la décision D-41 (**un rôle PostgreSQL par tenant**, [TODO.md](TODO.md) point 41) —
   un seul rôle superutilisateur partagé entre tenants romprait l'isolation par schéma.
   Voir `scripts/provision-tenant.sh --help` pour le détail des options.
3. **Démarrer une nouvelle instance** (image buildée en §1) avec sa propre configuration :
   `APP_TENANT_ID=tenant_b`, `DB_USERNAME=flowforge_tenant_b` / `DB_PASSWORD` correspondant, un port
   dédié — `context-path` (`/tenant_b`) est déjà actif par défaut (§3), aucune configuration
   supplémentaire nécessaire pour ce point précis.
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
- **Deux protocol mappers sur le client BFF**, tous deux émis dans l'**ID token** (c'est lui que
  lit le BFF) : `realm_access.roles` (mapper *User Realm Role* — le mapper par défaut du scope
  `roles` ne l'émet que dans l'access token) et `tenant_id` (mapper *User Attribute* sur l'attribut
  utilisateur `tenant_id`). Sans `tenant_id` égal à l'`app.tenant.id` de l'instance,
  `TenantResolutionFilter` invalide la session et répond 403 ; sans les rôles, l'utilisateur est
  connecté sans aucun droit.
- **L'attribut utilisateur `tenant_id` déclaré dans le profil utilisateur** (Realm settings → User
  profile) avec vue/édition réservées à l'admin : sinon Keycloak 24+ ignore l'attribut (attributs
  non gérés désactivés), et s'il était éditable par l'utilisateur, celui-ci pourrait changer de
  tenant depuis la console de compte. Le realm dev
  ([`flowforge-realm.json`](docker/keycloak/import/flowforge-realm.json)) applique ces trois points.

------

## 6. Gérer les utilisateurs et leurs rôles

FlowForge ne fournit **aucun écran de gestion des comptes** — toute création d'utilisateur ou
attribution de rôle se fait côté Keycloak (console admin ou API Admin Keycloak), jamais dans l'UI
FlowForge elle-même. Mécanisme : l'ID token transmis au BFF porte les rôles dans la structure
**standard** Keycloak `realm_access.roles` ; `RealmAccessAuthoritiesMapper` les convertit en
autorités `ROLE_<nom>`, ce que l'API vérifie ensuite (`hasRole('TENANT_ADMIN')`, etc.). Keycloak
n'émet pas cette structure dans l'ID token par défaut : les mappers du client BFF (§5) sont requis.

Procédure pour un nouvel utilisateur :

1. Console admin Keycloak → realm FlowForge → **Users** → **Add user** (username, email).
2. Onglet **Credentials** → définir un mot de passe (décocher *Temporary* pour éviter un changement
   forcé, ou le laisser coché selon la politique du client).
3. Onglet **Role mapping** → **Assign role** → cocher un ou plusieurs des 4 rôles réalm (§5). Un
   utilisateur peut cumuler plusieurs rôles (ex. l'utilisateur `demo` du realm dev a
   `TENANT_ADMIN` + `WORKFLOW_ADMIN`).
4. Onglet **Details** → attribut **`tenant_id`** = l'`app.tenant.id` de l'instance qu'il utilise
   (ex. `tenant_a`) — sans lui, la connexion aboutit à un 403 (§5).
5. L'utilisateur peut se connecter dès son prochain login — pas de propagation différée côté
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

## 8. Scan antivirus des pièces jointes (ClamAV)

`AttachmentScanPort` (domaine, inchangé depuis `durcissement-donnees-sensibles`, PR #62) accepte
deux implémentations : `NoOpAttachmentScanAdapter` (défaut, toujours « clean ») ou
`ClamAvAttachmentScanAdapter`, qui parle au démon `clamd` en TCP natif (protocole `INSTREAM`).
Sélection par propriété, même idiome que `APP_STORAGE_TYPE` (§ci-dessus) :

```bash
APP_DOSSIER_ATTACHMENT_SCAN_TYPE=clamav
APP_DOSSIER_ATTACHMENT_SCAN_CLAMAV_HOST=clamav.internal
APP_DOSSIER_ATTACHMENT_SCAN_CLAMAV_PORT=3310
```

**`clamd` est un service externe déjà provisionné en production** — même modèle que
PostgreSQL (§4) ou Keycloak (§5) : l'application ne fait que s'y connecter, elle ne le
déploie pas. `docker-compose.yml` (profil `clamav`) ne couvre que le dev/démo local, pas la
prod.

Points à couvrir par l'opérateur qui provisionne `clamd` en prod (hors scope de ce dépôt, comme
le provisioning Keycloak §7 ci-dessous) :

- Dimensionnement mémoire : ~1,5-2 Go pour la base de signatures virales.
- Mise à jour des signatures (`freshclam`), en continu — pas un geste ponctuel au provisioning.
- **Alignement `clamd.conf` `StreamMaxLength` sur `spring.servlet.multipart.max-file-size`**
  (`APP_DOSSIER_ATTACHMENT_MAX_SIZE_MB`, défaut 25 Mo, `application.yml`) — si l'un est relevé
  sans l'autre, un fichier légitime dans la plage haute traverse `clamd` en fail-open
  (ci-dessous) sans être réellement scanné. Voir `docker/clamav/clamd.conf` pour l'équivalent
  dev/démo.
- Un seul `clamd` mutualisé entre tenants suffit (pas de sidecar par tenant) — `clamd` ne
  stocke aucune donnée tenant, le scan est un aller-retour bytes-in/verdict-out sans état.

**Résilience — fail-open assumé.** Si `clamd` est indisponible, en timeout, ou répond de façon
inattendue (y compris « taille de flux dépassée »), l'upload est **accepté** (pas de blocage sur
la panne d'un composant annexe) — la tentative est loggée et incrémente le compteur Micrometer
`flowforge.attachment.scan.fail_open` (`/actuator/prometheus`, même mécanisme que
`observabilite`, PR #59). **Une panne `clamd` prolongée désactive donc le scan sans bloquer
personne** — surveiller ce compteur en prod (alerte sur une valeur non nulle et croissante) est
la seule façon de le détecter autrement qu'en lisant les logs requête par requête.

## 9. Portail externe (tiers sans compte)

Opt-in (TODO §68) : tant que `APP_PORTAL_ENABLED=false` (défaut), aucune route publique n'existe
— `/portal/**` et `/api/portal/**` exigent la session Keycloak comme le reste de l'application,
et un BPMN contenant une tâche externe est refusé au déploiement.

```bash
APP_PORTAL_ENABLED=true
APP_PORTAL_PUBLIC_BASE_URL=https://flowforge.example.org/tenant-a
APP_PORTAL_DEFAULT_LINK_TTL=7d   # facultatif
APP_PORTAL_MAX_LINK_TTL=30d      # facultatif, plafond de ce que le BPMN peut demander
```

- **`APP_PORTAL_PUBLIC_BASE_URL` est obligatoire** (démarrage refusé sinon) : URL publique
  **absolue** de l'instance, **chemin du tenant compris** (§3), sans `?` ni `#`. Le serveur ne la
  reconstruit jamais à partir de l'en-tête `Host` (contrôlable par le client).
- **Reverse-proxy** : exposer `/<tenant>/portal/` et `/<tenant>/api/portal/` à internet comme le
  reste de l'instance. Le proxy **doit écraser** `X-Forwarded-For` (déjà requis §3) : la limite de
  30 requêtes par minute et par IP du portail s'appuie dessus. L'application ne lit
  `X-Forwarded-For` que si le pair TCP est dans `APP_SECURITY_HEADERS_TRUSTED_PROXY_CIDRS` (sinon
  le pair lui-même est le client) et n'en retient que l'entrée la plus à droite non fiable ;
  l'en-tête `Forwarded` n'est jamais utilisé pour cette limite (revue adversariale, C2). Quand un
  afflux de clients distincts remplit la table du limiteur (10 000 entrées), les nouveaux venus
  partagent un seau commun, les clients déjà suivis gardent leur quota. Ne pas journaliser l'en-tête
  `X-Portal-Token` si le proxy journalise des en-têtes (c'est le secret d'accès du tiers).
- **SMTP** : l'invitation part directement par le relais SMTP configuré (pas par l'outbox de
  notification, qui stockerait le lien en base). Le jeton n'existe en clair que dans l'e-mail ; la
  base ne conserve que son empreinte SHA-256.
- **Antivirus — à activer (§8)** : les pièces déposées par un tiers **anonyme** suivent la
  politique fail-open (risque accepté, D-PORTAL-SCAN). Sans `clamd`, ou pendant une panne, un
  fichier non scanné est accepté. Activer ClamAV sur tout tenant qui active le portail et
  surveiller `flowforge.attachment.scan.fail_open`.
- **Métriques** : `flowforge.portal.access.denied` (liens refusés ou limite atteinte — une hausse
  brutale signale une tentative d'énumération).
- **Données personnelles** : l'adresse du tiers et ses réponses sont supprimées avec le dossier
  par la purge (§ rétention / `purge_outbox`).
- Mono-réplique : la limite par IP est en mémoire de l'instance (pas de Redis, décision n° 13).

------

## 10. Signature électronique

Opt-in (TODO §72) : tant que `APP_SIGNATURE_PROVIDER=none` (défaut), aucune route de signature
n'existe et un BPMN contenant un bloc de signature est **refusé au déploiement**.

```bash
APP_SIGNATURE_PROVIDER=stub          # seul fournisseur livré : simulation
APP_SIGNATURE_DEFAULT_DEADLINE=14d   # facultatif
APP_SIGNATURE_MAX_DEADLINE=90d       # facultatif, plafond de ce que le BPMN peut demander
```

- **Le `stub` ne produit aucune signature valable.** Il n'existe que pour développer et démontrer le
  parcours. Il ne démarre sans réglage que sous les profils `local` ou `test`. **Hors de ces profils
  (production comprise), il exige en plus la propriété `app.signature.stub.allow-outside-local=true`**,
  volontairement absente de tous les gabarits (`.env.example`, `application*.yml`) : ne l'activer que
  pour une recette ou une démonstration, jamais pour un usage réel. Le démarrage journalise un
  avertissement et la jauge `flowforge.signature.stub.active` vaut 1 tant qu'il tourne — **alerter**
  si elle vaut 1 en production.
- **Signataires externes** : le portail doit être activé (§9), y compris `APP_PORTAL_PUBLIC_BASE_URL` ;
  le lien d'invitation est `<URL publique>/portal/#signature:<jeton>`. L'invitation part par le relais
  SMTP (cinq tentatives). Le jeton n'existe en clair que dans l'e-mail (empreinte SHA-256 en base, jamais
  journalisé) ; le portail répond toujours par le même 404 uniforme à un lien refusé.
- **Brancher un vrai fournisseur** : implémenter le port `SignaturePort` et créer une instance du
  connecteur `signature` (type `CALLBACK`, secret de webhook en champ mot de passe). Le fournisseur
  appelle `/api/callbacks/{id de l'instance}` (HMAC horodaté + clé d'idempotence, comme tout callback).
- **Garde du stub** : le profil `test` est réservé aux tests automatisés ; ne jamais nommer ainsi un environnement
  de recette (il démarrerait le stub sans le second drapeau). L'e-mail d'invitation et les PDF portent la mention
  « SIMULATION » ; les PDF ont en plus la métadonnée `Subject`/`Keywords` et sont protégés contre la modification.
- **Callback fournisseur** : atomique (une seule transaction) ; une référence encore inconnue répond **503** sans
  mémoriser la clé d'idempotence — le fournisseur rejoue avec la même clé. Bornes revérifiées à l'exécution :
  nombre de signataires (`max-signers`) et échéance (`APP_SIGNATURE_MAX_DEADLINE`) ; deux renvois d'invitation
  au même signataire sont espacés d'au moins `app.signature.send.resend-min-interval` (1 minute).
- **Reprise** : les issues sont reprises par un répartiteur à nouvelle tentative (20 essais) ; les
  demandes expirent à l'échéance (tâche planifiée toutes les 15 minutes).
- **Métriques** : `flowforge.signature.requests`, `.outcomes`, `.dispatch.retries` / `.dispatch.gave_up`
  (reprises abandonnées : à alerter), `.callbacks.rejected`, et la jauge `flowforge.signature.stub.active`.
- **Données personnelles** : demandes, signataires (adresses), documents signés et preuves sont
  supprimés avec le dossier par la purge.

------

## 11. Plusieurs répliques d'un même tenant et déploiement progressif (TODO §79)

Le modèle reste **1 instance = 1 tenant**, mais deux pods du même tenant coexistent dès qu'on fait un **déploiement progressif**
(rolling update) ou qu'on scale à plusieurs répliques. Les tâches planifiées (`@Scheduled`) tournent alors des deux côtés. Chacune
est sûre si elle **prend** son unité de travail avant d'agir (UPDATE conditionnel en base, sans Redis ni verrou externe) :

| Tâche | Garantie avec deux exécutants |
|---|---|
| Relais outbox des notifications (e-mail, webhook, navigateur) et relais outbox workflow | une ligne n'est traitée que par l'exécutant qui l'a prise ; bail de 5 min (exécutant mort ⇒ reprise) |
| Alertes SLA | la transition d'état est prise avant la mise en file de l'alerte : une seule alerte par transition |
| Invitations du portail | le lien est pris avant l'envoi (bail de 5 min) : le tiers ne reçoit pas deux e-mails |
| Déclencheurs cron (`scheduler`, `batch-import`) | le créneau est pris avant le déclenchement : un seul dossier / import par échéance |
| Export BI | l'horloge d'export avance par compare-and-set au commit : un seul jeu de fichiers catalogué |
| Réconciliation des liens `RESERVED` | prise du lien (bail de 2 min) ; un lien dont le process a démarré n'est jamais abandonné |
| Purge, rétention, signature (invitations, expiration, distribution), expiration des liens portail | déjà protégés par claim, version ou contrainte en base |

**Ce qui n'est pas distribué** (volontairement, pas de Redis, décision n° 13/14) : le flux **SSE** (un pod ne pousse qu'aux utilisateurs
connectés *à lui* ; l'historique de la boîte de réception reste exact), la **limite de débit par IP**, et la **session** de l'application.
Kafka garantit « au moins une fois » : un *rebalance* peut rejouer des messages non validés (pas d'idempotence par message à ce jour).
Les **webhooks sortants** portent l'`outboxId` dans l'enveloppe : le destinataire peut dédupliquer.

Réglages liés : les envois SMTP ont des **délais** (`MAIL_SMTP_CONNECTION_TIMEOUT_MS` 10 s, `MAIL_SMTP_READ_TIMEOUT_MS` 30 s,
`MAIL_SMTP_WRITE_TIMEOUT_MS` 30 s) — sans eux un serveur muet bloquerait un exécutant au-delà du bail de ses lignes. À l'arrêt, les tâches planifiées en cours se terminent
(`APP_SCHEDULING_SHUTDOWN_WAIT`, 30 s) : prévoyez un délai de grâce du pod au moins égal.

Conséquence en cas de crash d'un pod entre la prise et l'effet : l'unité est reprise à l'échéance du bail (notifications, outbox workflow,
portail, réconciliation) ; un créneau cron est en revanche **au plus une fois** (un déclenchement qui *échoue* est rendu et réessayé ;
un pod qui meurt entre la prise et le déclenchement perd ce créneau).

------

## 12. Recherche sur champs dynamiques — migration `jsonb` (TODO §81)

La migration `030-dossier-fields-jsonb` convertit `dossier.fields` de `json` en `jsonb` et crée l'index GIN `idx_dossier_fields_gin`
(`jsonb_path_ops`). Elle **réécrit la table `dossier`** (verrou exclusif le temps de la conversion) : à planifier hors pic sur un gros
volume (compter quelques secondes par centaine de milliers de dossiers). PostgreSQL seulement ; le retour arrière est fourni
(`rollback`). Pré-contrôle conseillé : `jsonb` refuse le caractère NUL (`\u0000`), que `json` acceptait — `select 1 from dossier where fields::text like '%\\u0000%'` doit ne rien renvoyer. Sur H2 (tests), la recherche sur champ dynamique est refusée (400). Pour rejouer les tests PostgreSQL sans Docker :
`FLOWFORGE_TEST_PG_URL=jdbc:postgresql://hôte:port/base FLOWFORGE_TEST_PG_USER=… ./mvnw verify`.

------

## 13. Écritures de configuration par MCP : brouillons, publication, environnement (TODO §82)

| Variable | Effet |
|---|---|
| `APP_ENVIRONMENT` | `local`, `staging` ou `production` (défaut **`production`**). Une valeur inconnue empêche le démarrage. |
| `M2M_TRUSTED_CLIENT_IDS` | **Obligatoire** hors `local` dès que la chaîne M2M est active (`OAUTH2_ISSUER_URI` ou `M2M_JWT_SECRET`) : sinon tout porteur d'un JWT M2M valide est administrateur de fait (A3-M2). |
| `APP_MCP_PUBLICATION_MODE` | `draft` (défaut) : un agent écrit un brouillon, un humain publie. `direct` : publication sans relecture — **refusé au démarrage en production**. |
| `APP_DRAFTS_RETENTION_DAYS` | Brouillons d'agent/assistant non modifiés depuis N jours : purgés (défaut 30 ; 0 = jamais). Les brouillons de l'éditeur ne sont jamais purgés. |

La migration 031 ajoute la provenance aux brouillons et crée le journal `config_publication` (additif, retour arrière fourni). **Mise à jour d'un
déploiement existant** : renseigner `APP_ENVIRONMENT` (sans lui, le défaut `production` impose l'allowlist M2M et interdit `direct`) et, si un
agent déployait directement, prévenir ses opérateurs : ses écrans de déploiement répondent désormais « brouillon en attente de publication ».

------

## 14. Promouvoir la configuration de la recette vers la production (TODO §83)

Procédure type (rôle TENANT_ADMIN des deux côtés) :

1. Recette : *Admin → Promotion → Télécharger le bundle* (ou `GET /api/admin/configuration/export?sourceLabel=recette`). Versionnez le ZIP (ou son contenu) dans Git.
2. Production : *Planifier* le bundle (`POST /api/admin/configuration/plan`, multipart `file` + `parameters` JSON optionnel) et relisez le plan.
3. *Appliquer* (`POST /api/admin/configuration/apply`, mêmes parties + `secrets` JSON `{nomConnecteur: {champ: valeur}}` pour les créations + `expectedBundleSha256` du plan relu).
   Les secrets ne sont jamais écrits dans le bundle ni dans le journal ; transmettez-les par TLS.
4. Publiez les brouillons BPMN/DMN déposés (*Admin → Brouillons*), relancez l'import tant que des artefacts restent *Bloqués* par une dépendance, activez les connecteurs après relecture de leurs adresses.

Intégrité : chaque fichier porte son SHA-256 dans `manifest.json` (altération détectée) ; le bundle **n'est pas signé**. Limites : 2 000 artefacts, 5 Mo par fichier, 50 Mo au total ;
la taille d'envoi reste plafonnée par `spring.servlet.multipart.max-file-size`. La migration 032 crée le journal `config_import` (additif, retour arrière fourni). Un import ne retire jamais
une clé de configuration d'un connecteur existant (fusion) et ne change pas son état activé.

------

## 18. Base de données — PostgreSQL géré uniquement

La production utilise un **PostgreSQL géré** (schéma et rôle par tenant, §4). Le mode « base embarquée
persistante » (`-Pembedded-db`, profil Spring `embedded-db`, voir GETTING-STARTED §18) est réservé aux
démos/QA : il n'est **pas** dans le jar construit sans ce profil (la CI le vérifie avec
`scripts/check-no-embedded-db-in-jar.sh`), et l'application refuse de démarrer si `embedded-db` est
combiné à un profil `prod`/`production` ou si `APP_ENVIRONMENT` est absent ou vaut `production`. Ne jamais publier l'image construite avec
`--build-arg MAVEN_PROFILES=embedded-db` comme image de production.

## Ce qui n'est pas (encore) couvert par ce guide

- **Démarrage de l'instance et bloc reverse-proxy** restent des étapes documentées (§1/§3/§4), pas
  automatisées de bout en bout — dépendent trop de l'environnement cible (systemd/Docker/bare metal,
  Apache/nginx) pour un script générique.
- **Provisioning Keycloak** (réalm, clients OIDC/M2M, §5/§7) reste une procédure manuelle — hors
  scope du lot `provisioning-tenant`.
- **Provisioning `clamd`** (dimensionnement, mise à jour des signatures, éventuelle HA, §8) reste
  une procédure manuelle — hors scope du lot `scan-antivirus-clamav` (D-AV-CLAMAV-PROD-TOPO).
- **Sauvegarde/restauration PostgreSQL par schéma et stratégie de montée de version** ne sont pas
  documentées ici — à traiter comme toute décision technique ouverte du projet plutôt qu'improvisées
  à la première mise en production.
- **Canal email (SMTP)** — voir §6 de [GETTING-STARTED.md](GETTING-STARTED.md).

------

## Pour aller plus loin

- Modèle multi-tenant détaillé : [docs/specs/02-multi-tenant.md](docs/specs/02-multi-tenant.md)
- Authentification, BFF, machine-to-machine : [docs/specs/03-authentification.md](docs/specs/03-authentification.md)
- Autorisation et conformité : [docs/specs/12-autorisation-conformite.md](docs/specs/12-autorisation-conformite.md)
- Décisions et suivi (provisioning tenant, points 31/41 : livré via le lot `provisioning-tenant`) : [TODO.md](TODO.md) ; prochaines évolutions : [ROADMAP.md](ROADMAP.md)
- Prise en main de l'UI : [GUIDE-UTILISATEUR.md](GUIDE-UTILISATEUR.md)

## 15. Assistant de conception IA (TODO §86)

L'écran *Admin → Assistant* est **désactivé par défaut** : sans configuration, aucune requête ne sort de l'instance.

| Variable | Rôle |
|---|---|
| `APP_ASSISTANT_ENABLED` | `true` pour l'activer (démarrage refusé sans clé) |
| `APP_ASSISTANT_API_KEY` | clé du fournisseur de modèle — **secret serveur**, jamais envoyée au navigateur ni journalisée |
| `APP_ASSISTANT_BASE_URL` | point d'accès (défaut API Anthropic) ; **https obligatoire** hors boucle locale ; permet une passerelle d'entreprise ou une région d'hébergement |
| `APP_ASSISTANT_MODEL` | modèle utilisé |
| `APP_ASSISTANT_RETENTION_DAYS` | durée de conservation des conversations (défaut 30 ; purge automatique) |
| `APP_ASSISTANT_MAX_*` | plafonds : appels d'outils par message (12), tours par conversation (60), jetons par conversation (400 000) et par jour et par tenant (3 000 000) |

**Données et sous-traitance (RGPD).** Le fournisseur de modèle est un sous-traitant : à déclarer. En mode *conception*, l'assistant n'a accès
à **aucune donnée de dossier** (le catalogue d'outils est celui de `McpToolModes` en conception) : partent chez le fournisseur le texte saisi par l'administrateur,
les descriptions d'outils et les résultats d'outils de configuration (types, formulaires, processus, tables de décision, noms de connecteurs — jamais un secret).
N'y saisissez pas de données personnelles. L'assistant **ne publie jamais** (les processus et tables DMN restent des brouillons à relire), n'applique pas de bundle de
configuration et n'écrit jamais de secret de connecteur. Les conversations sont propres à leur auteur ; un administrateur ne lit pas celles d'un autre.
Le pare-feu sortant doit autoriser le seul hôte de `APP_ASSISTANT_BASE_URL`.
Techniquement, l'assistant s'appuie sur le starter Spring AI Anthropic ; son auto-configuration est coupée (`spring.ai.model.chat=none`) et le modèle n'est construit que si `APP_ASSISTANT_ENABLED=true`. Les redirections HTTP ne sont jamais suivies et aucun appel n'est relancé automatiquement.

## 16. E-mail entrant et bus Azure (TODO §87)

Trois connecteurs interrogent un système externe à intervalle régulier (`imap-inbound`, `graph-mail-inbound`, `azure-service-bus`). Ils sont **inactifs tant qu'aucune instance n'est créée** et
activée dans *Admin → Connecteurs*.

| Variable | Rôle (défaut) |
|---|---|
| `APP_INBOUND_MAX_MESSAGES_PER_POLL` | messages traités par interrogation d'une instance (20) |
| `APP_INBOUND_MAX_MESSAGE_BYTES` | taille maximale d'un e-mail, au-delà : rangé sans analyse (26 214 400) |
| `APP_INBOUND_MAX_BODY_CHARS` | texte conservé du corps d'un e-mail (20 000) |
| `APP_INBOUND_MAX_ATTACHMENT_BYTES` | taille maximale d'une pièce jointe (10 485 760) |
| `APP_INBOUND_MIN_POLL_SECONDS` | intervalle minimal d'une instance (30) |
| `APP_INBOUND_MAX_BUS_MESSAGE_BYTES` | taille maximale d'un message de bus lu (1 048 576) |

**Réseau.** Le pare-feu sortant doit autoriser, pour chaque instance : l'hôte IMAP (993, ou 143 en STARTTLS), ou `*.servicebus.windows.net` (443), et — en OAuth2 —
le point d'accès des jetons (`login.microsoftonline.com`). Les hôtes doivent résoudre vers des adresses publiques.
**Microsoft Graph** (`graph-mail-inbound`) : sortie 443 vers `graph.microsoft.com` (ou `graph.microsoft.us`, `dod-graph.microsoft.us`, `microsoftgraph.chinacloudapi.cn`) et `login.microsoftonline.com`. Inscrire une application Entra ID avec la permission d'**application** `Mail.ReadWrite` (consentement administrateur), puis **la restreindre à la seule boîte voulue** par une stratégie d'accès d'application Exchange Online (`New-ApplicationAccessPolicy`) : sans elle, la permission donne accès à TOUTES les boîtes du tenant. La stratégie d'accès d'application est la voie historique ; Microsoft promeut désormais *RBAC for Applications* pour le même besoin — l'essentiel est que l'application ne voie qu'une boîte. Pas de mot de passe : seul `client_credentials` est accepté, avec `oauthScope = https://graph.microsoft.com/.default`.
**Données.** Un e-mail entrant peut contenir des données personnelles : il devient un dossier soumis aux règles de rétention habituelles ; les messages
traités restent dans la boîte (dossier `FlowForge-processed`) et sont à purger selon votre politique. **Plusieurs répliques** : le créneau d'interrogation est pris
par comparaison-échange (D5), une seule réplique lit une boîte ou une file à la fois.

## 17. Langue de l'interface (TODO §90)

L'interface existe en français et en anglais. Chaque utilisateur choisit sa langue (sélecteur en haut de page ; le choix reste dans son navigateur). Tant qu'il n'a rien choisi, la langue vient de
**`APP_I18N_DEFAULT_LOCALE`** (`fr` par défaut, ou `en`) puis du navigateur. Cette même valeur fixe la langue des **e-mails envoyés à des tiers sans profil** (invitation de tâche externe, invitation de signature) :
en anglais, le lien du portail porte `?lang=en`. Toute autre valeur arrête le démarrage. Les libellés saisis par le tenant (types, champs, statuts) restent dans la langue où ils ont été saisis.
