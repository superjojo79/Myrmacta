# Déploiement — au-delà du poste de développement

Ce guide prend le relais de [GETTING-STARTED.md](GETTING-STARTED.md) (environnement de dev local,
infrastructure jetable, un seul tenant `tenant_dev`) pour couvrir ce qui change quand on sort de ce
cadre : construire un artefact déployable, exposer une instance derrière un reverse-proxy, faire
tourner plusieurs tenants, et gérer les comptes réels côté Keycloak.

> **Portée.** Le modèle cible (une instance Spring Boot dédiée par tenant, routage par chemin) est
> **décidé** (décisions n° 13–14, [analysis doc/02-multi-tenant.md](analysis%20doc/02-multi-tenant.md))
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

Router ensuite chaque instance depuis un reverse-proxy Apache à configuration **statique** (un bloc par
tenant, ajouté au provisioning — voir [analysis doc/02-multi-tenant.md](analysis%20doc/02-multi-tenant.md#routage-vers-linstance--chemin-par-tenant-reverse-proxy-statique)
pour le détail et la justification du choix) :

```apache
# httpd.conf — un bloc par tenant, ajouté au provisioning
# Écraser les X-Forwarded-* clients (mod_headers) puis poser proto=https.
# Sans ça, un client peut envoyer X-Forwarded-Proto: https sur HTTP clair.
RequestHeader unset X-Forwarded-Proto early
RequestHeader unset X-Forwarded-For early
RequestHeader unset X-Forwarded-Host early
RequestHeader unset X-Forwarded-Port early
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

------

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

- Modèle multi-tenant détaillé : [analysis doc/02-multi-tenant.md](analysis%20doc/02-multi-tenant.md)
- Authentification, BFF, machine-to-machine : [analysis doc/03-authentification.md](analysis%20doc/03-authentification.md)
- Autorisation et conformité : [analysis doc/12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md)
- Décisions ouvertes (dont le provisioning tenant, points 31/41) : [TODO.md](TODO.md)
- Prise en main de l'UI : [GUIDE-UTILISATEUR.md](GUIDE-UTILISATEUR.md)
