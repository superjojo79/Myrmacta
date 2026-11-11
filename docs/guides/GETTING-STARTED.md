# Getting started — environnement de développement local

Ce guide construit un environnement Myrmacta minimal en local : backend Spring Boot lancé sur
l'hôte (`./mvnw spring-boot:run`) connecté à une infrastructure de support (PostgreSQL, Keycloak,
et en option un stockage objet compatible S3) fournie par des conteneurs. Il ne couvre pas un
déploiement de production — voir [docs/guides/DEPLOIEMENT.md](DEPLOIEMENT.md) pour la suite (artefact,
reverse-proxy, provisioning tenant, comptes Keycloak), [README.md](../../README.md#modèle-de-déploiement)
et [docs/specs/](../specs/00-index.md) pour l'architecture cible.

------

## 1. Prérequis

| Outil | Version | Pourquoi |
|---|---|---|
| JDK | **25** | `pom.xml` (`java.version`) — le wrapper Maven (`./mvnw`) télécharge Maven lui-même, pas besoin de l'installer |
| Node.js | **22+** | Build du frontend Vue (voir [frontend/README.md](../../frontend/README.md)) |
| Runtime conteneur | Docker **ou** Podman | Pour PostgreSQL, Keycloak et (en option) le stockage objet — voir §2 |

Aucun autre service n'est requis pour un premier démarrage : le stockage de documents utilise le
filesystem local par défaut (§5), pas besoin de MinIO/S3 pour commencer.

------

## 2. Runtime conteneur — Docker, Podman, ou Hyper-V sous Windows

Tous les exemples de ce guide utilisent `docker compose <commande>`. Le fichier
[`docker-compose.yml`](../../docker-compose.yml) fourni est un compose file standard (pas
d'extension propriétaire) : il fonctionne à l'identique avec `podman compose` ou
`podman-compose`. Choisir l'option adaptée à l'OS :

| OS | Option | Commande équivalente |
|---|---|---|
| Linux | Docker Engine (natif) | `docker compose ...` |
| Linux | Podman (natif, rootless) | `podman compose ...` (Podman 4+, plugin `compose` requis) ou `podman-compose ...` |
| macOS | Docker Desktop | `docker compose ...` |
| macOS | Podman Desktop / `podman machine init && podman machine start` | `podman compose ...` |
| Windows | Docker Desktop — backend **WSL2** (recommandé) ou **Hyper-V** au choix dans Settings → General | `docker compose ...` (dans un terminal WSL2 ou PowerShell selon le backend) |
| Windows | Podman — `podman machine init` provisionne une VM légère via **Hyper-V** (ou WSL2 selon la version) | `podman compose ...` depuis PowerShell |

Notes :

- Sous Windows, Docker Desktop et Podman ont tous les deux besoin d'une petite VM Linux pour
  exécuter les conteneurs — WSL2 et Hyper-V sont deux hyperviseurs possibles pour cette VM, pas
  deux façons différentes d'utiliser ce guide. WSL2 est plus léger et généralement recommandé ;
  Hyper-V reste nécessaire si WSL2 n'est pas disponible (Windows Server, certaines politiques
  d'entreprise) — les commandes `docker compose` / `podman compose` ne changent pas selon le
  backend choisi.
- Remplacer une Hyper-V manuelle (VM Linux provisionnée à la main, sans Docker/Podman dedans) est
  possible mais hors du périmètre de ce guide : installer Docker ou Podman **à l'intérieur** de
  cette VM, puis suivre le reste du guide normalement depuis là.
- Vérifier l'installation : `docker compose version` (ou `podman compose version`).

### Windows + Podman (sans Docker Desktop) — parcours vérifié

Parcours validé de bout en bout (Windows 11, Podman 5.8, machine WSL) :

```powershell
winget install RedHat.Podman
podman machine init
podman machine start
winget install --id Docker.DockerCompose --scope user   # fournisseur compose, sans droits admin
podman compose version                                  # doit afficher « Docker Compose version … »
```

- **`podman compose` n'embarque pas de moteur compose** : il délègue à `docker-compose` ou
  `podman-compose`, sinon `Error: looking up compose provider failed`. Le binaire autonome
  `docker-compose` (sans Docker Desktop) est recommandé : il gère les valeurs par défaut imbriquées
  de `docker-compose.yml` (`${POSTGRES_PASSWORD:-${DB_PASSWORD:-myrmacta}}`).
- **PATH non rafraîchi** : un terminal (ou un IDE / agent) ouvert avant l'installation ne voit pas
  `podman` / `docker-compose`. Rouvrir le terminal, ou recharger le PATH dans la session :
  `$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')`.
- **Testcontainers** (`ClamAvAttachmentScanIT`) fonctionne sans configuration : la machine Podman
  publie le pipe nommé compatible Docker `\\.\pipe\docker_engine` (tant que Docker Desktop n'est pas
  installé), que Testcontainers détecte seul.
- Les ports publiés par les conteneurs (`5432`, `8081`) sont joignables sur `localhost` depuis
  Windows (redirection `gvproxy` de la machine Podman).

------

## 3. Config minimale

```bash
cp .env.local.example .env
```

[`.env.local.example`](../../.env.local.example) documente chaque variable ([`.env.example`](../../.env.example)
est le gabarit d'un environnement réel, voir `docs/guides/DEPLOIEMENT.md` §2). Les valeurs par défaut suffisent pour un
premier démarrage — rien à modifier pour suivre ce guide tel quel.
`APP_SECURITY_HEADERS_ENABLED` (défaut `true`) coupe le paquet CSP / HSTS / `X-Frame-Options` —
uniquement utilisable en profil `local`/`test` (seul HSTS y est absent par ailleurs) : hors de ces
profils, le boot refuse de démarrer si cette variable vaut `false` (TODO §64).
`APP_SECURITY_HEADERS_TRUSTED_PROXY_CIDRS` et `APP_SECURITY_HEADERS_CSP_REPORT_RATE_LIMIT` ont des
défauts adaptés au dev local — voir `.env.local.example`.
`APP_SECURITY_OIDC_GROUPS_CLAIM` (défaut `groups`) nomme la claim du jeton qui porte les groupes de l'utilisateur,
repris par les groupes de **Admin → Groupes** (TODO §114). Rien à configurer pour suivre ce guide : sans cette claim, les
groupes n'ont que leurs membres listés.

Le profil Spring **`local`** (`SPRING_PROFILES_ACTIVE=local` dans `.env.local.example`) autorise les
secrets de démo et le HMAC M2M. Hors `local`/`test`, le boot refuse `DB_PASSWORD` dans la denylist
(`myrmacta`, `postgres`, `admin`, `password`, vide) et tout `M2M_JWT_SECRET` non vide.
`APP_ENVIRONMENT` est le seul interrupteur « mode dev » (TODO §95) : le profil `local` exige
`APP_ENVIRONMENT=local` et le profil `test` est refusé quand `APP_ENVIRONMENT=production` (valeur par
défaut) — un `.env` de dev recopié tel quel sur un serveur ne démarre donc pas.

`docker compose` lit `.env` automatiquement. Le backend Spring Boot, lui, tourne sur l'hôte (pas
en conteneur) et ne lit **pas** `.env` nativement : avant `./mvnw spring-boot:run`, charger les
variables dans le shell courant :

```bash
set -a; source .env; set +a
```

(PowerShell : `Get-Content .env | ForEach-Object { if ($_ -match '^\s*([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }`)

------

## 4. Démarrer l'infrastructure (PostgreSQL + Keycloak)

```bash
docker compose up -d
```

Démarre uniquement `postgres` et `keycloak` (les services de stockage objet sont derrière des
profils, voir §5 — inactifs par défaut). Au premier démarrage :

- PostgreSQL crée la base `myrmacta` **et** le schéma `tenant_dev` (script
  [`docker/postgres/init/01-init-tenant-schema.sql`](../../docker/postgres/init/01-init-tenant-schema.sql))
  — nécessaire car l'application ne fait que `SET search_path` à la connexion, elle ne crée jamais
  le schéma elle-même (décision n° 13, [docs/specs/02-multi-tenant.md](../specs/02-multi-tenant.md)).
  Le superuser du conteneur (`POSTGRES_USER`, défaut `myrmacta`) n'est **pas** le user JDBC de
  l'app (`DB_USERNAME=myrmacta_tenant_dev`) : ce rôle LOGIN n'a de droits que sur `tenant_dev`.
- Keycloak importe un realm `myrmacta` préconfiguré
  ([`docker/keycloak/import/myrmacta-realm.json`](../../docker/keycloak/import/myrmacta-realm.json)) :
  client `mon-app` (celui attendu par `application.yml`) et trois utilisateurs de test, tous avec
  l'attribut `tenant_id=tenant_dev` (mot de passe = identifiant) :

  | Identifiant | Rôles | Voit |
  |---|---|---|
  | `demo` / `demo` | `TENANT_ADMIN` + `WORKFLOW_ADMIN` | tout, menus Admin compris |
  | `manager` / `manager` | `DOSSIER_MANAGER` | pilotage + Rapports & SLA |
  | `viewer` / `viewer` | `DOSSIER_VIEWER` | pilotage en lecture |

  Les
  mappers du client émettent `realm_access.roles` et `tenant_id` dans l'ID token, ce qu'exige le
  BFF — voir [docs/guides/DEPLOIEMENT.md §5](DEPLOIEMENT.md#5-keycloak-en-production). Après une modification
  du fichier de realm : `docker compose rm -sf keycloak`, `docker volume rm <projet>_keycloak_data`
  puis `docker compose up -d keycloak` (un realm déjà présent n'est pas réimporté). Un volume
  PostgreSQL ou Keycloak créé avant le renommage des identifiants garde les anciens noms :
  les variables à figer sont dans [docs/RENAMING.md](../RENAMING.md).

Vérifier que tout est prêt :

```bash
docker compose logs -f keycloak   # attendre "Keycloak <version> started" (~15-30s)
```

Console Keycloak : http://localhost:8081 (admin / admin, cf. `.env`).

------

## 5. Stockage des documents

### Par défaut : filesystem (aucun conteneur requis)

`.env.local.example` fixe `APP_STORAGE_TYPE=filesystem` — les documents générés sont écrits dans
`./data/documents` (créé automatiquement). Rien d'autre à faire : passer directement à l'étape 6.

### En option : stockage compatible S3 (MinIO, RustFS, SeaweedFS, AWS S3…)

L'application ne dépend d'aucun produit de stockage particulier — `DocumentStoragePort` est un
port hexagonal, son implémentation S3 (`S3StorageAdapter`) parle le protocole S3 standard via
`APP_STORAGE_S3_ENDPOINT`, quel que soit le serveur en face. Choisir **un seul** backend et
démarrer son profil Compose :

| Backend | Démarrer | `APP_STORAGE_S3_ENDPOINT` |
|---|---|---|
| **MinIO** | `docker compose --profile minio up -d` | `http://localhost:9000` |
| **RustFS** | `docker compose --profile rustfs up -d` | `http://localhost:9000` |
| **SeaweedFS** | `docker compose --profile seaweedfs up -d` | `http://localhost:8333` |
| **AWS S3 réel** (ou tout autre object store compatible S3) | rien à lancer localement | laisser vide, renseigner la vraie région + les clés IAM |

Chaque profil lance aussi un service `*-init` (basé sur `minio/mc`, un client S3 générique — pas
spécifique à MinIO) qui crée automatiquement le bucket `myrmacta-documents`. Ensuite, dans `.env` :

```bash
APP_STORAGE_TYPE=s3
APP_STORAGE_S3_ENDPOINT=http://localhost:9000   # adapter selon le backend choisi, cf. tableau
```

puis recharger les variables dans le shell (§3) avant de (re)lancer le backend. Consoles web :
MinIO → http://localhost:9001, RustFS → http://localhost:9001 (ports distincts si les deux
profils tournent en même temps — sinon changer `9000`/`9001` dans `docker-compose.yml` ou `.env`).

Ne jamais coder un client spécifique à MinIO/RustFS/SeaweedFS dans l'application : seul
`APP_STORAGE_S3_*` doit changer d'un backend à l'autre (voir [`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md), §0 —
« pas de MinIO en dur »).

------

## 6. Configurer le canal email (optionnel)

Un des trois canaux de notification ([docs/specs/09-notifications.md](../specs/09-notifications.md))
est l'email, envoyé via SMTP (`spring-boot-starter-mail`). **Aucun serveur SMTP n'est démarré par
défaut par `docker-compose.yml`** (profil optionnel `mailpit` ci-dessous) — contrairement à PostgreSQL/Keycloak/stockage, ce n'est pas un prérequis
pour démarrer : sans configuration, `MAIL_HOST` vaut `localhost:587`, les envois échoueront
silencieusement (pas de service à l'écoute) et resteront en re-tentative dans
`notification_outbox` jusqu'à passer en `DEAD` (voir l'écran **Observabilité**, §9 de
[docs/guides/GUIDE-UTILISATEUR.md](GUIDE-UTILISATEUR.md)) — sans bloquer le workflow déclencheur ni les autres
canaux (webhook, navigateur).

**En local, le plus simple : Mailpit** (profil Compose `mailpit`), qui capture tous les e-mails
sans les délivrer — UI sur http://localhost:8025 — et fait passer l'indicateur de santé mail du
backend à `UP` (sans serveur SMTP, `/actuator/health` reste `DOWN`, liveness/readiness restant
`UP`) :

```bash
docker compose --profile mailpit up -d
# puis, dans .env : MAIL_PORT=1025 (Docker/Podman rootless ne publient pas le port 587)
```

Pour un envoi réel, renseigner dans `.env` un relais SMTP existant (fournisseur cloud, relais
interne) :

```bash
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=un-compte-smtp
MAIL_PASSWORD=un-mot-de-passe-smtp
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
```

puis recharger les variables dans le shell (§3) avant de (re)lancer le backend.

> **Limite actuelle :** l'adaptateur d'envoi (`SmtpEmailAdapter`) ne positionne pas d'en-tête
> `From` explicite — le comportement dépend alors entièrement des règles du relais SMTP configuré
> (certains l'acceptent avec une valeur par défaut, d'autres rejettent l'envoi). Il n'y a pas non
> plus, à ce jour, de compteur dédié aux échecs email dans **Observabilité** (`/admin/observabilite`)
> comme il en existe un pour les webhooks (« Webhooks DEAD ») — un échec email n'est visible
> qu'indirectement, via l'âge de l'outbox de notification.

------

## 7. Démarrer le backend

```bash
./mvnw spring-boot:run
```

Sert l'API sur `http://localhost:8080`. Si `src/main/resources/static/` est vide (frontend pas
encore buildé), les routes `/api/**` répondent mais l'UI ne s'affiche pas — voir §8.

> `server.servlet.context-path` (routage multi-tenant par chemin, décision n°14) est actif par
> défaut hors profil `local` — `application-local.yml` le neutralise pour ce parcours, l'application
> reste donc servie à la racine `/` comme ci-dessus. Voir [docs/guides/DEPLOIEMENT.md](DEPLOIEMENT.md#3-routage-par-chemin-décision-n-14)
> pour une instance déployée sous un préfixe de tenant.

Actuator (health / Prometheus) : **`http://127.0.0.1:8090`** (`MANAGEMENT_ADDRESS` /
`MANAGEMENT_PORT`, D-42). Keycloak reste sur **`:8081`**. `permitAll` sur le port management
est conservé parce que le bind est loopback.

**Scraper avec Prometheus (optionnel).** `docker compose --profile prometheus up -d` démarre un
Prometheus préconfiguré ([`docker/prometheus/prometheus.yml`](../../docker/prometheus/prometheus.yml))
qui scrape `127.0.0.1:8090/actuator/prometheus` — UI sur http://localhost:9090. Le conteneur
tourne en `network_mode: host` pour atteindre ce port loopback sans l'exposer plus largement
(voir le commentaire du service `prometheus` dans `docker-compose.yml`) : fonctionne tel quel sous
Linux (Docker Engine ou Podman rootless) ; sous macOS/Windows Docker Desktop, `network_mode: host`
exige d'activer *Host networking* (Docker Desktop ≥ 4.34) — à défaut, se contenter de
`curl http://127.0.0.1:8090/actuator/prometheus` sans ce conteneur.

**Connecteur `kafka` (optionnel, connecteurs-cible lot B1).**
`docker compose --profile kafka up -d` démarre un broker Kafka mono-nœud en mode KRaft (image
officielle `apache/kafka`, pas de Zookeeper), joignable depuis l'application sur
`localhost:${KAFKA_PORT:-9094}`. Sert uniquement à tester/démontrer le connecteur `kafka` en
local — aucune dépendance du reste de l'application sur ce service.

Test rapide sans frontend :

```bash
curl -i http://localhost:8080/api/auth/me   # 401 sans session (l'API ne redirige jamais)
curl -i http://localhost:8080/              # 302 vers /oauth2/authorization/oidc (login Keycloak)
curl -s http://127.0.0.1:8090/actuator/health
```

------

## 8. Démarrer le frontend

Deux modes, voir aussi [frontend/README.md](../../frontend/README.md) :

- **Build + backend seul (recommandé pour un parcours complet)** :

  ```bash
  cd frontend && npm install && npm run build && cd ..
  ./mvnw spring-boot:run
  ```

  Le backend sert alors la SPA buildée depuis `static/`, même origine que l'API — aucune config
  proxy nécessaire.

- **HMR (développement frontend actif)** : `cd frontend && npm install && npm run dev` sert la
  SPA sur son propre port Vite (ex. `5173`), sans proxy vers l'API par défaut → les appels
  `/api/...` échouent en 404 tant que le backend n'est pas atteint. Voir la section dédiée de
  [frontend/README.md](../../frontend/README.md#tester-contre-un-vrai-backend-en-hmr) pour brancher un
  `server.proxy` temporaire.

------

## 9. Vérifier que tout fonctionne

1. Ouvrir http://localhost:8080 — redirection automatique vers Keycloak.
2. Se connecter avec `demo` / `demo` (ou `manager` / `viewer` pour un rôle restreint, §4).
3. Retour sur l'app, authentifié — `GET /api/auth/me` doit renvoyer l'identité et les rôles.

Si l'authentification boucle sans jamais aboutir (retour immédiat vers Keycloak), voir le
dépannage ci-dessous (cookie `Secure`).

------

## 10. Arrêter / nettoyer

```bash
docker compose down            # arrête postgres + keycloak (+ profil actif si précisé)
docker compose down -v         # + supprime les volumes (base, realm Keycloak, bucket) — repart de zéro
```

------

## 11. Dépannage

| Symptôme | Cause probable | Fix |
|---|---|---|
| Boucle de redirection vers Keycloak, jamais authentifié, sur `http://localhost` | `app.security.cookie.secure=true` (défaut) exige HTTPS ; le navigateur rejette le cookie de session sur HTTP | Vérifier `APP_SECURITY_COOKIE_SECURE=false` dans `.env`, variables bien rechargées dans le shell avant `spring-boot:run` |
| Erreur `app.storage.s3.bucket is required` au démarrage | `APP_STORAGE_TYPE=s3` sans bucket configuré | Renseigner `APP_STORAGE_S3_BUCKET` (déjà positionné dans `.env.local.example`) |
| `NoSuchBucket` / erreurs S3 à l'upload | Le profil Compose du backend de stockage n'est pas démarré, ou le service `*-init` n'a pas encore créé le bucket | `docker compose --profile <minio\|rustfs\|seaweedfs> up -d`, puis `docker compose logs <backend>-init` |
| Tables créées dans `public` au lieu du schéma tenant | Le schéma `tenant_dev` n'existait pas avant le premier démarrage de PostgreSQL (le script d'init ne s'exécute qu'une fois, sur un volume vide) | `docker compose down -v` puis `docker compose up -d` pour repartir d'un volume vierge — **aussi** après un changement du rôle LOGIN / GRANT (D-41) |
| Port déjà utilisé (`8080`, `5432`, `8081`, `8090`, `9000`…) | Conflit avec un service local existant | Adapter les ports dans `.env` (`DB_PORT`, `KEYCLOAK_PORT`, `MANAGEMENT_PORT`) ou dans `docker-compose.yml` pour le stockage objet |
| `OIDC_CLIENT_SECRET` vide au démarrage backend | `.env` non chargé dans le shell avant `spring-boot:run` | Refaire `set -a; source .env; set +a` dans le même terminal que la commande Maven |
| Connexion Keycloak réussie puis 403 vide sur l'app | L'ID token ne porte pas `tenant_id` égal à `app.tenant.id` (`TenantResolutionFilter`) | Attribut utilisateur `tenant_id` + mapper du client (docs/guides/DEPLOIEMENT.md §5) ; realm dev : réimporter (§4) |
| Connecté mais aucun menu Admin, `/api/auth/me` → `"roles": []` | `realm_access.roles` absent de l'ID token (le mapper par défaut de Keycloak ne l'y met pas) | Mapper *User Realm Role* « Add to ID token » sur le client (docs/guides/DEPLOIEMENT.md §5) |
| `/actuator/health` → `DOWN`, liveness/readiness `UP` | Indicateur mail : aucun serveur SMTP sur `MAIL_HOST:MAIL_PORT` | Profil `mailpit` + `MAIL_PORT=1025` (§6), ou relais SMTP réel |
| Connexion qui aboutit sur `/login?error` | Deux onglets ouverts sur l'app lancent chacun un login OIDC dans la même session : le second écrase la requête d'autorisation du premier | Un seul onglet pendant la connexion, puis recharger |
| `podman compose` → `looking up compose provider failed` | Aucun fournisseur compose installé | `winget install --id Docker.DockerCompose --scope user` (§2) |
| `rootlessport cannot expose privileged port` | Port hôte < 1024 refusé par Docker/Podman rootless | Publier un port ≥ 1024 (ex. Mailpit sur 1025) |

------

## 12. Serveur MCP (outillage agent IA)

Myrmacta expose un serveur [MCP](https://modelcontextprotocol.io) pour un agent IA interne
(Claude Code ou tout autre client MCP compatible Streamable HTTP) — une quarantaine de tools
couvrant :

- **la conception** : types de dossier et schémas de questions de tâche, templates de documents
  (avec aperçu), workflows BPMN (et leurs briques de palette), tables de décision DMN
  (déploiement, versions, évaluation, test), parcours, expressions et règles métier,
  connecteurs ;
- **la gouvernance** : brouillons en attente et publication, export/import de configuration ;
- **les écritures métier** (`create_dossier`, `add_dossier_element`, `start_workflow`,
  `generate_document`) ;
- **la lecture** : dossier, recherche, statistiques SLA —

de quoi définir **et opérer** un flux métier sans sortir du canal MCP.

Endpoint : `POST http://localhost:8080/api/m2m/mcp` — même chaîne de sécurité machine-à-machine
que `/api/m2m/connectors/**` (aucune session navigateur, aucun cookie) : un JWT `Authorization:
Bearer <token>` avec le rôle `API_SERVICE` et un claim `tenant_id` correspondant à
`app.tenant.id` (`tenant_dev` par défaut en local, §3).

En local (profil `local`), le canal M2M est **désactivé tant que `M2M_JWT_SECRET` est vide**
(défaut de `.env.local.example`) : y renseigner un secret d'au moins 32 caractères (HS256), recharger
`.env` (§3) et relancer le backend. Générer ensuite un JWT de test signé HS256 avec ce secret —
claims `roles` (`["API_SERVICE"]`), `tenant_id` (`tenant_dev`), `sub` (ou `client_id`), `exp` —
puis pointer un client MCP dessus :

```bash
curl -i http://localhost:8080/api/m2m/mcp \
  -H "Authorization: Bearer $M2M_JWT" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"local-test","version":"0.1.0"}}}'
```

Une réponse `200` avec `"serverInfo":{"name":"myrmacta",...}` confirme l'endpoint actif. La
liste des tools (`tools/list`, qui fait foi pour leur nombre exact) et leurs schémas d'entrée
sont générés automatiquement depuis le code (`infrastructure.mcp.tool`) — pas de documentation à
maintenir manuellement en plus de celle-ci. Aucune UI dédiée côté SPA : ce canal est réservé à
l'outillage interne, pas aux utilisateurs finaux du tenant.

Par défaut, tout JWT M2M authentifié reçoit `API_SERVICE` (un seul client M2M de confiance
assumé, TODO §56). `M2M_TRUSTED_CLIENT_IDS` (voir `.env.local.example`) restreint ce rôle à une
allowlist de `client_id`/`sub` — à renseigner avant d'ouvrir `/api/m2m/mcp` à un second client M2M
à portée réduite. **Depuis TODO §82 (D6)** : hors `APP_ENVIRONMENT=local`, l'allowlist est **obligatoire** dès que le
canal M2M est actif (l'application ne démarre pas sinon) ; `deploy_workflow`, `deploy_decision_table` et
`redeploy_decision_table` écrivent un **brouillon** qu'un humain publie depuis *Admin → Brouillons* (rupture pour les
agents qui supposaient un déploiement immédiat — `APP_MCP_PUBLICATION_MODE=direct`, hors production, le rétablit).
`generate_document` partage par ailleurs le rate limiter (30/min par défaut,
`app.document.generate.rate-limit-per-minute`) de l'équivalent REST `/api/documents/generate`.

------

## 13. Scan antivirus des pièces jointes (optionnel)

Par défaut (`APP_DOSSIER_ATTACHMENT_SCAN_TYPE=noop`, `.env.local.example`), les pièces jointes
(`POST /api/dossiers/{id}/elements/attachment`) ne sont pas scannées. Ce défaut n'est accepté qu'en
dev : avec `APP_ENVIRONMENT=staging` le démarrage l'avertit, avec `APP_ENVIRONMENT=production` il
est refusé sauf `APP_DOSSIER_ATTACHMENT_SCAN_NOOP_ACKNOWLEDGED=true` (TODO §95). Pour activer un scan réel via [ClamAV](https://www.clamav.net/) :

```bash
docker compose --profile clamav up -d
```

Le démarrage est **lent** (plusieurs minutes) : `clamd` télécharge sa base de signatures
virales avant d'accepter des connexions — le `healthcheck` du service attend ce chargement.
Puis, dans `.env` :

```bash
APP_DOSSIER_ATTACHMENT_SCAN_TYPE=clamav
APP_DOSSIER_ATTACHMENT_SCAN_CLAMAV_HOST=localhost
APP_DOSSIER_ATTACHMENT_SCAN_CLAMAV_PORT=3310
```

puis recharger les variables dans le shell (§3) avant de (re)lancer le backend. Pour vérifier
que le scan fonctionne, téléverser le
[fichier de test EICAR](https://www.eicar.org/download-anti-malware-testfile/) (une chaîne de
test standard, pas un vrai virus) comme pièce jointe : il doit être rejeté.

Si `clamd` n'est pas joignable (profil non démarré, arrêté, etc.) alors que le scan est activé,
l'upload reste **accepté** (fail-open assumé — voir [docs/guides/DEPLOIEMENT.md § ClamAV](DEPLOIEMENT.md#8-scan-antivirus-des-pièces-jointes-clamav)
pour le détail de ce compromis) — ce n'est pas une panne bloquante en local.

## 14. Portail externe — tiers sans compte (optionnel, TODO §68)

Désactivé par défaut. Pour l'essayer en local, dans `.env` :

```bash
APP_PORTAL_ENABLED=true
APP_PORTAL_PUBLIC_BASE_URL=http://localhost:8080
```

`APP_PORTAL_PUBLIC_BASE_URL` est **obligatoire** dès que le portail est activé (le backend refuse
de démarrer sinon) : c'est l'URL publique écrite dans l'e-mail d'invitation, suivie de
`/portal/#<jeton>`. L'invitation part par le canal SMTP (§6) — sans relais SMTP, le lien reste
*Non envoyé* après 5 tentatives ; un outil de capture type Mailpit suffit pour voir l'e-mail en
local. Déployer ensuite un BPMN contenant une tâche externe (GUIDE-CONCEPTION-BPMN §3ter).

Variables facultatives : `APP_PORTAL_DEFAULT_LINK_TTL` (défaut `7d`), `APP_PORTAL_MAX_LINK_TTL`
(plafond, défaut `30d`).

------

## 15. Signature électronique — simulation locale (optionnel, TODO §72)

Désactivée par défaut. Pour l'essayer en local (profil `local`), dans `.env` :

```bash
APP_SIGNATURE_PROVIDER=stub
# signataires externes : portail activé (§14)
APP_PORTAL_ENABLED=true
APP_PORTAL_PUBLIC_BASE_URL=http://localhost:8080
```

Le fournisseur `stub` **simule** la signature (PDF marqué « SIMULATION — sans valeur juridique »).
Il démarre sans autre réglage sous le profil `local` ou `test` ; hors de ces profils, il refuse de
démarrer (voir docs/guides/DEPLOIEMENT.md § Signature électronique). Les fournisseurs `yousign` et
`docusign` se règlent dans cette même section, avec les secrets sur le connecteur `signature`.
Variables facultatives :
`APP_SIGNATURE_DEFAULT_DEADLINE` (défaut `14d`), `APP_SIGNATURE_MAX_DEADLINE` (défaut `90d`).
Déployer ensuite un BPMN contenant un bloc de signature (GUIDE-CONCEPTION-BPMN §3quater).

## 16. Assistant de conception IA (optionnel, TODO §86)

Désactivé par défaut. Pour l'essayer en local : dans `.env`, `APP_ASSISTANT_ENABLED=true` et `APP_ASSISTANT_API_KEY=<votre clé>` (jamais commitée),
redémarrez le backend, puis ouvrez *Admin → Assistant*. Sans clé, l'écran affiche « non activé » et rien ne sort de votre poste.
Détails (variables, plafonds, RGPD) : `docs/guides/DEPLOIEMENT.md` §15.

## 17. Lancer les tests — sans Docker ni PostgreSQL préinstallé

Les tests (`./mvnw verify`) utilisent un **PostgreSQL 17 embarqué** (zonky, binaires téléchargés par
Maven) : une instance par JVM de test, une base fraîche par contexte Spring. Aucun conteneur,
aucune base à démarrer. Seul `ClamAvAttachmentScanIT` utilise encore Docker ou Podman (conteneur
ClamAV) ; il ne tourne qu'avec `-Dclamav.it=true` (`docs/guides/DEPLOIEMENT.md` §21).

- **Ne pas lancer en root** : `initdb` refuse de tourner sous l'utilisateur root (cas fréquent des
  conteneurs de CI et des bacs à sable). Lancer sous un utilisateur normal, par exemple
  `runuser -u <utilisateur> -- ./mvnw verify` (le dépôt et `~/.m2` doivent lui être accessibles).
- **PostgreSQL externe** (poste où les binaires embarqués ne conviennent pas, ou root imposé) :

  ```bash
  export MYRMACTA_TEST_PG_URL=jdbc:postgresql://localhost:5432/postgres
  export MYRMACTA_TEST_PG_USER=postgres          # doit pouvoir CREATE DATABASE et CREATE ROLE
  export MYRMACTA_TEST_PG_PASSWORD=...
  ./mvnw verify
  # Les anciens noms de ces trois variables sont encore lus (docs/RENAMING.md).
  ```

- Les classes `*IT` s'exécutent désormais dans `./mvnw verify` (plus besoin de `-Dtest='*IT'`).
- **Windows** : `.\mvnw.cmd verify` passe tel quel (zonky fournit des binaires Windows). Les tests ne
  doivent dépendre ni du DNS du poste (résolveur figé : `StubPublicDnsTestConfiguration`, ou
  `OutboundUrlGuard.HostResolver` en test unitaire — certains résolveurs répondent NXDOMAIN pour
  `example.com`), ni de la langue des messages PostgreSQL (tester le SQLState, pas le texte : le
  serveur embarqué hérite de la locale de l'OS), ni de la résolution de `System.nanoTime()` (pas de
  100 ns sous Windows — utiliser un compteur pour générer des valeurs distinctes).
- **Suites opt-in de la CI** (job backend de `.github/workflows/ci.yml`) :
  `./mvnw test -Pembedded-db -Dskip.frontend=true -Djacoco.skip=true -Dtest='ZonkyEmbeddedDbLauncherTest,EmbeddedDbProfileRestartIT' -Dsurefire.failIfNoSpecifiedTests=false`
  et `./mvnw test -Dtest=SignatureBrowserE2eTest -Dsignature.e2e=true -Dskip.frontend=true -Djacoco.skip=true -Dsurefire.failIfNoSpecifiedTests=false`
  (après `npx playwright install chromium` dans `frontend/`). Sous PowerShell, mettre chaque
  `-D…` entre guillemets.

## 18. Base embarquée persistante — démos et QA (optionnel)

Pour une démo ou une machine de QA **sans Docker Compose ni PostgreSQL**, le profil Maven
`embedded-db` produit un jar qui embarque PostgreSQL ; les données sont **conservées** entre deux
démarrages.

```bash
./mvnw -Pembedded-db package
APP_ENVIRONMENT=local SPRING_PROFILES_ACTIVE=local,embedded-db java -jar target/myrmacta-*.jar   # + variables du §3 (OIDC…)
```

| Variable | Défaut | Rôle |
|---|---|---|
| `APP_EMBEDDED_DB_DATA_DIR` | `~/.myrmacta/embedded-db` | répertoire persistant (cluster `pgdata/`, secret du rôle applicatif, verrou). Un répertoire déjà présent sous l'ancien chemin est encore utilisé ([docs/RENAMING.md](../RENAMING.md)). |
| `APP_EMBEDDED_DB_PORT` | `15432` | port TCP (127.0.0.1) ; `0` = port libre aléatoire |

- Le profil `embedded-db` remplace `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` : le rôle applicatif
  (`myrmacta_tenant_dev`) reçoit un **mot de passe généré** (`app-role-password`, droits 0600).
  Hors profil `local`/`test`, les autres secrets (clé connecteurs, client OIDC) restent exigés.
- Un seul tenant (`tenant_dev`) ; une seule instance par répertoire (verrou) ; un cluster initialisé
  par une autre version majeure de PostgreSQL est refusé avec un message explicite.
- **Exploitation** : `scripts/embedded-db.sh status | backup <archive.tar.gz> | restore <archive.tar.gz> |
  dump <fichier.sql> | reset`. `backup`/`restore`/`reset` exigent l'application arrêtée ; `dump`
  (pg_dump du schéma tenant) se fait à chaud.
- **Image Docker de démo** : `docker build --build-arg MAVEN_PROFILES=embedded-db -t myrmacta:demo .`
  puis `docker run -v myrmacta-data:/data -e APP_ENVIRONMENT=staging -e SPRING_PROFILES_ACTIVE=local,embedded-db …` (le volume
  `/data` conserve les données ; l'application tourne en utilisateur non-root).
- **Interdit en production** : le jar standard ne contient pas ce mode, et le profil est refusé si un
  profil `prod`/`production` est actif ou si `APP_ENVIRONMENT` est absent ou vaut `production` (à
  définir à `local` ou `staging`). Le serveur écoute 127.0.0.1 mais en authentification `trust` :
  réserver à une machine ou un conteneur non partagé.

### Démo complète (profil `demo`, TODO §127)

La démo de Myrmacta pour les présentations et les captures du site. Elle se compose de **paquets**, installés au premier
démarrage sur une base embarquée vide, par les vrais cas d'usage :

```bash
scripts/demo.sh build      # jar embedded-db (une fois)
scripts/demo.sh start      # depuis la racine du dépôt, avec les variables OIDC du §3
```

- **Paquets livrés :** la mail room (voir ci-dessous). Les paquets onboarding collaborateur, autorisation riverain et
  « états » (dossiers à différents stades, SLA dépassés, incident, brouillon MCP) suivent (issue #55).
- **Démarrages suivants :** un paquet déjà installé n'est pas touché ; un paquet ajouté depuis s'installe.
- **Rejouer la démo :** arrêter l'application, puis lancer `scripts/demo.sh reset` et `start`.
- Comme `demo-mailroom`, le profil n'existe que dans le build `embedded-db`.

### Démo de la Digital Mail Room (profil `demo-mailroom`, TODO §113)

Une démo reproductible du courrier entrant, sur la base embarquée, sans serveur de messagerie ni SFTP :

```bash
scripts/demo-mailroom.sh build      # jar embedded-db (une fois)
scripts/demo-mailroom.sh start      # depuis la racine du dépôt, avec les variables OIDC du §3
```

- **Premier démarrage, base vide :** l'exemple [`exemples/digital-mail-room`](../../exemples/digital-mail-room/README.md)
  est installé. Il comprend les groupes, les types, les configurations, la table de routage, les process, le workflow
  réponse, l'échéance de la facture, un point de dépôt et le gabarit (activé) de l'accusé de réception. La facture reçue par
  e-mail déclenche cet accusé : sans serveur d'envoi configuré, la ligne d'outbox échoue après ses tentatives, sans effet
  sur le tri.
- **Trois courriers arrivent par le vrai pipeline des connecteurs :**
  - une facture par e-mail, routée vers la COMPTABILITE dans un dossier « facture » lié ;
  - une amende déposée en PDF, qui va à la file de tri ;
  - un e-mail non reconnu, qui va aussi à la file de tri.
- **Démarrages suivants :** rien n'est réinstallé.
- **Rejouer la démo :** arrêter l'application, puis lancer `scripts/demo-mailroom.sh reset` et `start`.
- **Où :** `APP_DEMO_MAILROOM_DIR` désigne un autre répertoire d'exemple. Le profil n'existe que dans le build
  `embedded-db` ; `scripts/check-no-embedded-db-in-jar.sh` vérifie qu'aucun code de démo n'est dans le jar de production.
- C'est le paquet « mail room » de la démo complète, installé seul.

## 19. CI sur un runner auto-hébergé (optionnel)

Quand le compte n'a plus de minutes GitHub Actions, les workflows de `.github/workflows/` tournent tels quels sur une
machine à vous (TODO §110) : Docker ou Podman, Linux x64 ou arm64 (Docker Desktop convient), 4 cœurs, 8 Go de
mémoire et 20 Go de disque au moins.

> **Dépôt public : ne pas utiliser.** Depuis la publication du dépôt (registre §118), une pull request ouverte depuis
> un fork exécuterait son code sur votre machine. Ne définissez pas la variable `CI_RUNNER` sur le dépôt public :
> les runners hébergés par GitHub y sont gratuits. Cette section ne vaut que pour un dépôt privé (fork privé,
> archive).

1. **Jeton.** Sur GitHub : dépôt → *Settings* → *Actions* → *Runners* → *New self-hosted runner*, copier la valeur
   qui suit `--token` (valable une heure, pour un seul enregistrement).
2. **Démarrer.**

   ```bash
   RUNNER_TOKEN=<jeton> docker compose -f docker/ci-runner/compose.yml up -d --build
   docker compose -f docker/ci-runner/compose.yml logs -f      # attendre « Listening for Jobs »
   ```

   Le runner apparaît *Idle* dans *Settings* → *Actions* → *Runners*, avec le libellé `myrmacta-ci`. Il redémarre
   avec la machine (`restart: unless-stopped`) et n'a plus besoin de jeton : son enregistrement vit dans le volume.
3. **Basculer.** *Settings* → *Secrets and variables* → *Actions* → onglet *Variables* → variable de dépôt
   `CI_RUNNER` = `myrmacta-ci`. Les push et PR suivants passent sur le runner ; une PR déjà ouverte se rejoue par
   *Re-run all jobs* dans l'onglet *Actions*.
4. **Revenir aux runners GitHub** : supprimer la variable `CI_RUNNER`.

| Variable | Défaut | Rôle |
|---|---|---|
| `RUNNER_TOKEN` | — | jeton de création (premier démarrage) ou de suppression (`remove`) |
| `RUNNER_NAME` | `myrmacta-ci` | nom du runner dans GitHub |
| `RUNNER_LABELS` | `myrmacta-ci` | libellés, séparés par des virgules ; doivent contenir la valeur de `CI_RUNNER` |
| `RUNNER_VERSION` | dernière publiée | version téléchargée au premier démarrage ; le runner se met ensuite à jour seul |
| `REPO_URL` | `https://github.com/superjojo79/Myrmacta` | dépôt servi |

- **Désenregistrer.** *Runners* → le runner → *Remove*, copier le jeton affiché, puis :

  ```bash
  docker compose -f docker/ci-runner/compose.yml stop
  RUNNER_TOKEN=<jeton> docker compose -f docker/ci-runner/compose.yml run --rm ci-runner remove
  docker compose -f docker/ci-runner/compose.yml down -v      # volume compris : JDK, ~/.m2, navigateurs
  ```

- **Un job à la fois.** Les trois jobs de `ci.yml` passent l'un après l'autre (une vingtaine de minutes en tout).
  Runner éteint : les jobs attendent en file, GitHub les annule au bout de 24 heures.
- **Sécurité.** Tout code poussé sur le dépôt, branches des agents comprises, s'exécute sur la machine, dans le
  conteneur. Ne pas y monter le socket Docker ni un répertoire de l'hôte ; préférer une machine sans accès à des
  services sensibles du réseau local.
- `ClamAvAttachmentScanIT` ne tourne qu'avec `-Dclamav.it=true` et un démon Docker, absent de ce runner : le job
  ClamAV du workflow « Recette sur environnement réel » reste sur `ubuntu-latest` (`docs/guides/DEPLOIEMENT.md` §21).

## Pour aller plus loin

- Déploiement au-delà de ce guide (reverse-proxy, provisioning tenant, comptes Keycloak) : [docs/guides/DEPLOIEMENT.md](DEPLOIEMENT.md)
- Architecture détaillée : [docs/specs/](../specs/00-index.md)
- Décisions actées et points ouverts : [registre des décisions](../decisions/README.md)
- Prochaine feature / backlog : [FILE-ATTENTE.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/FILE-ATTENTE.md), [DEFERRED-FEATURES.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md)
