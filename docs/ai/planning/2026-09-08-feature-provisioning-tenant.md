---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown — `provisioning-tenant` (lot A1)

## Milestones
**What are the major checkpoints?**

- [ ] M1 : `Dockerfile` fonctionnel, image buildée et smoke-testée réellement (`docker build` +
      `docker run`).
- [ ] M2 : `scripts/provision-tenant.sh` fonctionnel, smoke-testé contre un vrai PostgreSQL jetable.
- [ ] M3 : `context-path` activé en configuration sans régression (`./mvnw verify` vert).
- [ ] M4 : Documentation (`DEPLOIEMENT.md`, `GETTING-STARTED.md`, `.env.example`) alignée avec
      l'outillage livré.
- [ ] M5 : Revue finale (Phase 9) + mise à jour `TODO.md`/`FILE-ATTENTE.md`.

M1 et M2 sont indépendants entre eux (aucun fichier partagé) — ordre arbitraire, M1 traité en
premier ici. M3 est isolé et rapide. M4 dépend de M1-M3 (documente ce qui a été livré). M5 clôt le
cycle.

## Task Breakdown
**What specific work needs to be done?**

### Milestone 1 — Dockerfile
- [ ] T1.1 : `.dockerignore` (exclut `target/`, `frontend/node_modules`, `frontend/dist`, `.git`,
      `.worktrees`, `docker/postgres/data` éventuel, fichiers `.env*`).
- [ ] T1.2 : `Dockerfile` multi-stage — stage `build` (`eclipse-temurin:25-jdk`, `./mvnw -B clean
      package -DskipTests`), stage `runtime` (`eclipse-temurin:25-jre`, utilisateur non-root dédié,
      `ENTRYPOINT` shell-form avec `-XX:MaxRAMPercentage=75.0` + `JAVA_OPTS` override, `EXPOSE 8080`).
- [ ] T1.3 : Validation — `docker build -t flowforge:smoke .` réel ; `docker run --rm flowforge:smoke
      id` (UID non-root) ; vérifier taille/contenu image (pas de JDK dans le stage final).
      *Couvre les scénarios testing : « build multi-stage réussit », « utilisateur non-root »,
      « image runtime sans JDK ».*

### Milestone 2 — Script de provisioning
- [ ] T2.1 : `scripts/provision-tenant.sh` — validation de `<tenant_id>`
      (`^[a-z][a-z0-9_]{2,62}$`), génération SQL paramétrée à partir du gabarit
      `docker/postgres/init/01-init-tenant-schema.sql`, sortie stdout par défaut / `-o <fichier>`.
- [ ] T2.2 : Mode `--apply` — connexion `psql` (variables standard `PGHOST`/`PGPORT`/`PGDATABASE`/
      `PGUSER`/`PGPASSWORD`), lecture `TENANT_DB_PASSWORD` (échec explicite si absente), garde
      anti-écrasement (vérifie `information_schema.schemata` avant d'appliquer, échoue si le schéma
      existe déjà).
- [ ] T2.3 : Validation — conteneur PostgreSQL jetable (`postgres:17-alpine`, cohérent avec
      `docker-compose.yml`) ; scénario nominal (génération + application), scénario rejeu (échec
      attendu), scénario identifiant invalide (échec avant toute connexion), scénario mot de passe
      absent (échec avant toute connexion).
      *Couvre tous les scénarios testing « scripts/provision-tenant.sh » + l'intégration
      PostgreSQL jetable.*

### Milestone 3 — Activation `context-path`
- [ ] T3.1 : Décommenter `server.servlet.context-path: /${app.tenant.id}` dans `application.yml`.
- [ ] T3.2 : Ajouter la neutralisation `context-path: ""` dans `application-local.yml` (pattern
      `app.security.cookie.secure`).
- [ ] T3.3 : Validation — `./mvnw verify` (suite complète, profil `test`) ; relecture ciblée des 2
      tests `RANDOM_PORT`/`DEFINED_PORT` (`ObservabilityPrometheusApiTest`,
      `ObservabilityActuatorIT`) pour confirmer qu'ils continuent de cibler le port management,
      non affecté.
      *Couvre le scénario testing « `./mvnw verify` reste vert malgré context-path actif ».*

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
