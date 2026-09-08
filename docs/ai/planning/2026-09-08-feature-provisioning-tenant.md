---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown — `provisioning-tenant` (lot A1)

## Milestones
**What are the major checkpoints?**

- [x] M1 : `Dockerfile` fonctionnel — **validation partielle** : `docker build`/`docker run` réels
      impossibles dans ce bac à sable (Docker Hub bloqué par la politique réseau de la session, voir
      Risks) ; validé à la place par revue statique + `hadolint` (exit 0, propre) + cohérence
      `pom.xml` (`java.version=25`).
- [x] M2 : `scripts/provision-tenant.sh` fonctionnel, smoke-testé **réellement** contre PostgreSQL
      (service local du bac à sable, pas de conteneur — Docker Hub bloqué) + `shellcheck` propre.
- [x] M3 : `context-path` activé en configuration, `./mvnw verify` réellement exécuté et vert (1738
      tests, 0 échec, ArchUnit conforme, Jacoco respecté) — un bug réel trouvé et corrigé en route
      (clé YAML `server:` dupliquée), voir Risks/Task Breakdown.
- [x] M4 : Documentation (`DEPLOIEMENT.md`, `GETTING-STARTED.md`, `.env.example`) alignée avec
      l'outillage livré.
- [ ] M5 : Revue finale (Phase 9) + mise à jour `TODO.md`/`FILE-ATTENTE.md`.

M1 et M2 sont indépendants entre eux (aucun fichier partagé) — ordre arbitraire, M1 traité en
premier ici. M3 est isolé et rapide. M4 dépend de M1-M3 (documente ce qui a été livré). M5 clôt le
cycle.

## Task Breakdown
**What specific work needs to be done?**

### Milestone 1 — Dockerfile
- [x] T1.1 : `.dockerignore` (exclut `target/`, `frontend/node_modules`, `frontend/dist`, `.git`,
      `.worktrees`, `docs/`, `scripts/`, `docker/`, fichiers `.env*`).
- [x] T1.2 : `Dockerfile` multi-stage — stage `build` (`eclipse-temurin:25-jdk`, `./mvnw -B clean
      package -DskipTests`), stage `runtime` (`eclipse-temurin:25-jre`, utilisateur non-root UID/GID
      10001, `ENTRYPOINT` shell-form avec `-XX:MaxRAMPercentage=75.0` + `JAVA_OPTS` override,
      `EXPOSE 8080`).
- [x] T1.3 : Validation — **`docker build`/`docker run` réels impossibles** dans ce bac à sable
      (`production.cloudfront.docker.com`, CDN de blobs Docker Hub, rejeté par la politique réseau
      de la session — confirmé systémique, `eclipse-temurin:25-jdk` **et** `postgres:17-alpine`
      tous deux rejetés ; pas de contournement tenté, conformément à `/root/.ccr/README.md`).
      Repli réel obtenu : `hadolint` (binaire GitHub Releases, seul host testé hors Docker Hub) sur
      `Dockerfile` → exit 0, aucun avertissement ; `pom.xml` confirmé `java.version=25` (cohérent
      avec les tags `eclipse-temurin:25-*`) et `frontend-maven-plugin` autonome (télécharge son
      propre Node, comme en dev local) — le plan de stages est réalisable, non vérifié end-to-end.
      *Couvre partiellement les scénarios testing « build multi-stage réussit », « utilisateur
      non-root », « image runtime sans JDK » — ces 3 restent non exécutés faute d'accès registre.*

### Milestone 2 — Script de provisioning
- [x] T2.1 : `scripts/provision-tenant.sh` — validation de `<tenant_id>`
      (`^[a-z][a-z0-9_]{2,62}$`), génération SQL paramétrée à partir du gabarit
      `docker/postgres/init/01-init-tenant-schema.sql`, sortie stdout par défaut / `-o <fichier>`.
- [x] T2.2 : Mode `--apply` — connexion `psql` (variables standard `PGHOST`/`PGPORT`/`PGDATABASE`/
      `PGUSER`/`PGPASSWORD`), lecture `TENANT_DB_PASSWORD` (échec explicite si absente), garde
      anti-écrasement (vérifie `information_schema.schemata` avant d'appliquer, échoue si le schéma
      existe déjà).
- [x] T2.3 : Validation — **conteneur PostgreSQL jetable impossible** (Docker Hub bloqué, cf. M1),
      remplacé par le service `postgresql-16` déjà installé dans ce bac à sable (démarré via
      `sudo service postgresql start`) : tous les scénarios réellement exécutés avec de vraies
      requêtes SQL — génération + application nominale, rejeu sur tenant existant rejeté
      explicitement (pas d'écrasement), identifiant invalide rejeté avant connexion, mot de passe
      absent rejeté avant connexion, **et isolation croisée vérifiée** (un second tenant provisionné,
      le rôle du premier reçoit `permission denied` en `CREATE TABLE` dans le schéma du second).
      `shellcheck scripts/provision-tenant.sh` propre. Données de test nettoyées après coup.
      *Couvre tous les scénarios testing « scripts/provision-tenant.sh » avec de vraies preuves
      d'exécution, sur PostgreSQL natif plutôt que conteneurisé.*

### Milestone 3 — Activation `context-path`
- [x] T3.1 : `server.servlet.context-path: /${app.tenant.id}` **fusionné dans le bloc `server:`
      existant** d'`application.yml` (ligne 112, portait déjà `forward-headers-strategy` et
      `servlet.session.cookie.*`) — pas un nouveau bloc top-level (une première tentative en créant
      un second bloc `server:` cassait le YAML, SnakeYAML: « found duplicate key server »,
      détecté par `./mvnw verify` lui-même et corrigé).
- [x] T3.2 : Neutralisation `context-path: ""` ajoutée dans `application-local.yml` (pattern
      `app.security.cookie.secure`).
- [x] T3.3 : Validation — `./mvnw -B verify -Dmaven.compiler.release=21` **exécuté réellement** :
      `BUILD SUCCESS`, 1738 tests, 0 échec, 0 erreur, ArchUnit conforme, couverture Jacoco respectée.
      Confirme l'analyse préalable (seuls `ObservabilityPrometheusApiTest`/`ObservabilityActuatorIT`
      utilisent un vrai port HTTP, sur le port management séparé, non affecté par le context-path).
      *Couvre le scénario testing « `./mvnw verify` reste vert malgré context-path actif » — avec
      preuve fraîche.*

### Milestone 4 — Documentation
- [ ] T4.1 : `DEPLOIEMENT.md` — remplacer le runbook manuel §4 par les commandes réelles
      (`docker build`, `scripts/provision-tenant.sh ... --apply`, `docker run -e APP_TENANT_ID=...
      -e DB_USERNAME=... -e DB_PASSWORD=...`), documenter les 3 décisions Dockerfile actées (image,
      non-root, heap), retirer les mentions « aucun Dockerfile/CLI n'existe » devenues fausses,
      garder le rappel manuel Keycloak/reverse-proxy (hors scope de ce lot).
- [ ] T4.2 : `GETTING-STARTED.md` — note factuelle sur `context-path` actif hors profil `local`
      (aucun changement du parcours dev décrit).
- [ ] T4.3 : `.env.example` — ajouter `APP_TENANT_ID` (commenté/documenté, défaut hérité
      `tenant_dev`).
- [ ] T4.4 : `CHANGELOG.md` — entrée « Non publié » une fois une PR envisagée (convention projet,
      pas needed avant, cf. Phase 9 / si PR demandée explicitement).

### Milestone 5 — Clôture
- [ ] T5.1 : `TODO.md` §31 — passer « Partiel » à « clos » pour la part outillage provisioning
      (lot A1 livré), en gardant la trace du détail.
- [ ] T5.2 : `FILE-ATTENTE.md` — déplacer la ligne A1 vers *Récemment livré*, dater l'entrée avec le
      résumé (findings réels, résultat `./mvnw verify`, smoke tests Docker/PostgreSQL).
- [ ] T5.3 : Commit(s) + push sur `claude/cycle-sdd-lot-a1-mjxdai` (pas de PR sans demande explicite
      de l'utilisateur).

## Dependencies
**What needs to happen in what order?**

- T1.* et T2.* : aucune dépendance mutuelle, ordre arbitraire (T1 traité en premier).
- T3.* : indépendant de T1/T2, rapide — peut être traité à tout moment, placé en 3ᵉ ici par
  cohérence de narration (config avant doc).
- T4.* dépend de T1-T3 terminés (documente ce qui existe réellement, pas une anticipation).
- T5.* dépend de T1-T4 + Phase 9 (revue finale) terminés.
- Dépendance externe : démon Docker (déjà démarré manuellement dans ce bac à sable, `sudo dockerd`)
  — si indisponible au moment de l'implémentation, T1.3/T2.3 basculent en revue statique documentée
  (assumption déjà actée en requirements), sans bloquer le reste du cycle.

## Timeline & Estimates
**When will things be done?**

Lot ops de taille modeste (pas de nouveau code applicatif Java) — cycle attendu en une seule session
continue : M1-M3 (implémentation + validation) puis M4-M5 (doc + clôture) à la suite, sans découpage
en plusieurs jours.

## Risks & Mitigation
**What could go wrong?**

- **[Réalisé] Docker Hub inaccessible dans ce bac à sable.** Le démon Docker fonctionne (démarré
  manuellement, `sudo dockerd`), mais la politique réseau de la session rejette explicitement
  `production.cloudfront.docker.com` (CDN de blobs Docker Hub) — confirmé par
  `recentRelayFailures` de l'agent proxy (`connect_rejected`, « policy denial »), et systémique
  (`eclipse-temurin:25-jdk` et `postgres:17-alpine` tous deux rejetés, pas seulement une image
  précise). Conformément à `/root/.ccr/README.md`, aucun contournement n'a été tenté (pas de retry
  en boucle, pas de recherche de registre miroir alternatif). Mitigation appliquée : `hadolint`
  (récupéré une fois depuis GitHub Releases, host non bloqué) pour le `Dockerfile` ; `postgresql-16`
  déjà installé localement dans le bac à sable pour le smoke test du script de provisioning (au lieu
  d'un conteneur PostgreSQL jetable) ; `shellcheck` pour le script. **Conséquence assumée** : le
  `Dockerfile` n'a pas été buildé/exécuté réellement dans ce cycle — à faire à la première occasion
  dans un environnement avec accès registre (poste ops, CI), documenté comme tel dans le doc
  d'implémentation plutôt que de prétendre un smoke build réussi.
- **Démon Docker instable/indisponible en cours de route** (sandbox éphémère) → mitigation :
  documenter précisément la dernière validation réussie ; si le smoke test devient impossible, le
  signaler explicitement plutôt que de prétendre un résultat non observé (cf. `verify` skill).
- **Régression `context-path` non anticipée** (au-delà de l'analyse MockMvc/management-port déjà
  faite) → mitigation : `./mvnw verify` est la validation faisant foi ; en cas d'échec inattendu,
  revenir en Phase 3 (design) plutôt que de contourner par un correctif ad hoc non revu.
- **Script de provisioning divergent du gabarit SQL existant** (`01-init-tenant-schema.sql`) →
  mitigation : générer le SQL par paramétrage direct du même gabarit (pas de réécriture), comparer
  la sortie du script pour `tenant_dev` au fichier existant comme test de non-régression informel.
- **Débordement de surface** (toucher accidentellement un fichier hors liste exclusive du lot A1,
  ex. `application.yml` au-delà de `context-path`) → mitigation : relecture du diff complet avant
  chaque commit, comparé à la liste exclusive du design doc.

## Resources Needed
**What do we need to succeed?**

- Docker (déjà disponible, daemon démarré manuellement dans ce bac à sable — `sudo dockerd`).
- `./mvnw` (wrapper déjà présent, télécharge Maven/Node lui-même — accès réseau sortant nécessaire,
  déjà utilisé par le dev local).
- Aucune ressource externe supplémentaire (pas de nouvel outil, pas de nouvelle dépendance).
