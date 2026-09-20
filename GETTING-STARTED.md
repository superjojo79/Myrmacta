# Getting started — environnement de développement local

Ce guide construit un environnement FlowForge minimal en local : backend Spring Boot lancé sur
l'hôte (`./mvnw spring-boot:run`) connecté à une infrastructure de support (PostgreSQL, Keycloak,
et en option un stockage objet compatible S3) fournie par des conteneurs. Il ne couvre pas un
déploiement de production — voir [DEPLOIEMENT.md](DEPLOIEMENT.md) pour la suite (artefact,
reverse-proxy, provisioning tenant, comptes Keycloak), [README.md](README.md#modèle-de-déploiement)
et [analysis doc/](analysis%20doc/00-index.md) pour l'architecture cible.

------

## 1. Prérequis

| Outil | Version | Pourquoi |
|---|---|---|
| JDK | **25** | `pom.xml` (`java.version`) — le wrapper Maven (`./mvnw`) télécharge Maven lui-même, pas besoin de l'installer |
| Node.js | **22+** | Build du frontend Vue (voir [frontend/README.md](frontend/README.md)) |
| Runtime conteneur | Docker **ou** Podman | Pour PostgreSQL, Keycloak et (en option) le stockage objet — voir §2 |

Aucun autre service n'est requis pour un premier démarrage : le stockage de documents utilise le
filesystem local par défaut (§5), pas besoin de MinIO/S3 pour commencer.

------

## 2. Runtime conteneur — Docker, Podman, ou Hyper-V sous Windows

Tous les exemples de ce guide utilisent `docker compose <commande>`. Le fichier
[`docker-compose.yml`](docker-compose.yml) fourni est un compose file standard (pas
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

------

## 3. Config minimale

```bash
cp .env.example .env
```

[`.env.example`](.env.example) documente chaque variable. Les valeurs par défaut suffisent pour un
premier démarrage — rien à modifier pour suivre ce guide tel quel.

Le profil Spring **`local`** (`SPRING_PROFILES_ACTIVE=local` dans `.env.example`) autorise les
secrets de démo et le HMAC M2M. Hors `local`/`test`, le boot refuse `DB_PASSWORD` dans la denylist
(`flowforge`, `postgres`, `admin`, `password`, vide) et tout `M2M_JWT_SECRET` non vide.

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

- PostgreSQL crée la base `flowforge` **et** le schéma `tenant_dev` (script
  [`docker/postgres/init/01-init-tenant-schema.sql`](docker/postgres/init/01-init-tenant-schema.sql))
  — nécessaire car l'application ne fait que `SET search_path` à la connexion, elle ne crée jamais
  le schéma elle-même (décision n° 13, [analysis doc/02-multi-tenant.md](analysis%20doc/02-multi-tenant.md)).
  Le superuser du conteneur (`POSTGRES_USER`, défaut `flowforge`) n'est **pas** le user JDBC de
  l'app (`DB_USERNAME=flowforge_tenant_dev`) : ce rôle LOGIN n'a de droits que sur `tenant_dev`.
- Keycloak importe un realm `flowforge` préconfiguré
  ([`docker/keycloak/import/flowforge-realm.json`](docker/keycloak/import/flowforge-realm.json)) :
  client `mon-app` (celui attendu par `application.yml`) et un utilisateur de test
  `demo` / `demo`.

Vérifier que tout est prêt :

```bash
docker compose logs -f keycloak   # attendre "Keycloak <version> started" (~15-30s)
```

Console Keycloak : http://localhost:8081 (admin / admin, cf. `.env`).

------

## 5. Stockage des documents

### Par défaut : filesystem (aucun conteneur requis)

`.env.example` fixe `APP_STORAGE_TYPE=filesystem` — les documents générés sont écrits dans
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
spécifique à MinIO) qui crée automatiquement le bucket `flowforge-documents`. Ensuite, dans `.env` :

```bash
APP_STORAGE_TYPE=s3
APP_STORAGE_S3_ENDPOINT=http://localhost:9000   # adapter selon le backend choisi, cf. tableau
```

puis recharger les variables dans le shell (§3) avant de (re)lancer le backend. Consoles web :
MinIO → http://localhost:9001, RustFS → http://localhost:9001 (ports distincts si les deux
profils tournent en même temps — sinon changer `9000`/`9001` dans `docker-compose.yml` ou `.env`).

Ne jamais coder un client spécifique à MinIO/RustFS/SeaweedFS dans l'application : seul
`APP_STORAGE_S3_*` doit changer d'un backend à l'autre (voir `DEFERRED-FEATURES.md`, §0 —
« pas de MinIO en dur »).

------

## 6. Configurer le canal email (optionnel)

Un des trois canaux de notification ([analysis doc/09-notifications.md](analysis%20doc/09-notifications.md))
est l'email, envoyé via SMTP (`spring-boot-starter-mail`). **Aucun serveur SMTP n'est fourni par
`docker-compose.yml`** — contrairement à PostgreSQL/Keycloak/stockage, ce n'est pas un prérequis
pour démarrer : sans configuration, `MAIL_HOST` vaut `localhost:587`, les envois échoueront
silencieusement (pas de service à l'écoute) et resteront en re-tentative dans
`notification_outbox` jusqu'à passer en `DEAD` (voir l'écran **Observabilité**, §9 de
[GUIDE-UTILISATEUR.md](GUIDE-UTILISATEUR.md)) — sans bloquer le workflow déclencheur ni les autres
canaux (webhook, navigateur).

Pour activer réellement l'envoi, renseigner dans `.env` un relais SMTP existant (fournisseur cloud,
relais interne, ou un outil de capture d'email pour du test local type Mailpit/Mailhog — non
fourni par ce dépôt) :

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
> reste donc servie à la racine `/` comme ci-dessus. Voir [DEPLOIEMENT.md](DEPLOIEMENT.md#3-routage-par-chemin-décision-n-14)
> pour une instance déployée sous un préfixe de tenant.

Actuator (health / Prometheus) : **`http://127.0.0.1:8090`** (`MANAGEMENT_ADDRESS` /
`MANAGEMENT_PORT`, D-42). Keycloak reste sur **`:8081`**. `permitAll` sur le port management
est conservé parce que le bind est loopback.

**Scraper avec Prometheus (optionnel).** `docker compose --profile prometheus up -d` démarre un
Prometheus préconfiguré ([`docker/prometheus/prometheus.yml`](docker/prometheus/prometheus.yml))
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
curl -i http://localhost:8080/api/auth/me   # doit rediriger vers Keycloak (302) si pas de session
curl -s http://127.0.0.1:8090/actuator/health
```

------

## 8. Démarrer le frontend

Deux modes, voir aussi [frontend/README.md](frontend/README.md) :

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
  [frontend/README.md](frontend/README.md#tester-contre-un-vrai-backend-en-hmr) pour brancher un
  `server.proxy` temporaire.

------

## 9. Vérifier que tout fonctionne

1. Ouvrir http://localhost:8080 — redirection automatique vers Keycloak.
2. Se connecter avec `demo` / `demo` (utilisateur importé au §4).
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
| Erreur `app.storage.s3.bucket is required` au démarrage | `APP_STORAGE_TYPE=s3` sans bucket configuré | Renseigner `APP_STORAGE_S3_BUCKET` (déjà positionné dans `.env.example`) |
| `NoSuchBucket` / erreurs S3 à l'upload | Le profil Compose du backend de stockage n'est pas démarré, ou le service `*-init` n'a pas encore créé le bucket | `docker compose --profile <minio\|rustfs\|seaweedfs> up -d`, puis `docker compose logs <backend>-init` |
| Tables créées dans `public` au lieu du schéma tenant | Le schéma `tenant_dev` n'existait pas avant le premier démarrage de PostgreSQL (le script d'init ne s'exécute qu'une fois, sur un volume vide) | `docker compose down -v` puis `docker compose up -d` pour repartir d'un volume vierge — **aussi** après un changement du rôle LOGIN / GRANT (D-41) |
| Port déjà utilisé (`8080`, `5432`, `8081`, `8090`, `9000`…) | Conflit avec un service local existant | Adapter les ports dans `.env` (`DB_PORT`, `KEYCLOAK_PORT`, `MANAGEMENT_PORT`) ou dans `docker-compose.yml` pour le stockage objet |
| `OIDC_CLIENT_SECRET` vide au démarrage backend | `.env` non chargé dans le shell avant `spring-boot:run` | Refaire `set -a; source .env; set +a` dans le même terminal que la commande Maven |

------

## 12. Serveur MCP (outillage agent IA)

FlowForge expose un serveur [MCP](https://modelcontextprotocol.io) pour un agent IA interne
(Claude Code ou tout autre client MCP compatible Streamable HTTP) — 20 tools couvrant la
définition de type de dossier (schéma + templates de documents associés), le déploiement de
workflow BPMN, la définition de connecteurs et de schémas de questions de tâche, **et les
écritures métier** (`create_dossier`, `add_dossier_element`, `start_workflow`,
`generate_document`), plus la lecture d'un dossier, la recherche, les statistiques SLA, et
la lecture des types/templates/workflows/connecteurs — de quoi définir **et opérer** un flux
métier sans sortir du canal MCP.

Endpoint : `POST http://localhost:8080/api/m2m/mcp` — même chaîne de sécurité machine-à-machine
que `/api/m2m/connectors/**` (aucune session navigateur, aucun cookie) : un JWT `Authorization:
Bearer <token>` avec le rôle `API_SERVICE` et un claim `tenant_id` correspondant à
`app.tenant.id` (`tenant_dev` par défaut en local, §3).

En local (profil `local`, HMAC M2M déjà activé au §3), générer un JWT de test signé avec
`M2M_JWT_SECRET` (voir `.env`) puis pointer un client MCP dessus :

```bash
curl -i http://localhost:8080/api/m2m/mcp \
  -H "Authorization: Bearer $M2M_JWT" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"local-test","version":"0.1.0"}}}'
```

Une réponse `200` avec `"serverInfo":{"name":"flowforge",...}` confirme l'endpoint actif. La
liste des 20 tools (`tools/list`) et leurs schémas d'entrée sont générés automatiquement depuis le
code (`infrastructure.mcp.tool`) — pas de documentation à maintenir manuellement en plus de
celle-ci. Aucune UI dédiée côté SPA : ce canal est réservé à l'outillage interne, pas aux
utilisateurs finaux du tenant.

Par défaut, tout JWT M2M authentifié reçoit `API_SERVICE` (un seul client M2M de confiance
assumé, TODO §56). `M2M_TRUSTED_CLIENT_IDS` (voir `.env.example`) restreint ce rôle à une
allowlist de `client_id`/`sub` — à renseigner avant d'ouvrir `/api/m2m/mcp` à un second client M2M
à portée réduite. `generate_document` partage par ailleurs le rate limiter (30/min par défaut,
`app.document.generate.rate-limit-per-minute`) de l'équivalent REST `/api/documents/generate`.

------

## 13. Scan antivirus des pièces jointes (optionnel)

Par défaut (`APP_DOSSIER_ATTACHMENT_SCAN_TYPE=noop`, `.env.example`), les pièces jointes
(`POST /api/dossiers/{id}/elements/attachment`) ne sont pas scannées — comportement inchangé
depuis toujours. Pour activer un scan réel via [ClamAV](https://www.clamav.net/) :

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
l'upload reste **accepté** (fail-open assumé — voir [DEPLOIEMENT.md § ClamAV](DEPLOIEMENT.md#8-scan-antivirus-des-pièces-jointes-clamav)
pour le détail de ce compromis) — ce n'est pas une panne bloquante en local.

------

## Pour aller plus loin

- Déploiement au-delà de ce guide (reverse-proxy, provisioning tenant, comptes Keycloak) : [DEPLOIEMENT.md](DEPLOIEMENT.md)
- Architecture détaillée : [analysis doc/](analysis%20doc/00-index.md)
- Décisions actées et points ouverts : [TODO.md](TODO.md)
- Prochaine feature / backlog : [FILE-ATTENTE.md](FILE-ATTENTE.md), [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md)
