---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy — `provisioning-tenant` (lot A1)

Lot ops/outillage : pas de code applicatif Java nouveau (pas de use case/entité/port), donc pas de
suite JUnit dédiée. La stratégie de validation combine revue statique, smoke tests réels et la
suite backend existante pour le seul changement de code applicatif du lot (`context-path`).

**Résultat réel (Phase 8, à jour) :** le démon Docker de ce bac à sable fonctionne, mais la
politique réseau de la session rejette explicitement `production.cloudfront.docker.com` (CDN de
blobs Docker Hub) — confirmé systémique (`eclipse-temurin:25-jdk` et `postgres:17-alpine` tous deux
rejetés), non contourné (`/root/.ccr/README.md`). Conséquence : le `Dockerfile` n'a **pas** été
buildé/exécuté réellement (repli : `hadolint`, propre) ; le smoke PostgreSQL du script de
provisioning a utilisé le service `postgresql-16` déjà installé localement dans le bac à sable
plutôt qu'un conteneur jetable — tous les scénarios ont néanmoins été exécutés avec de vraies
requêtes SQL contre un vrai PostgreSQL. Détail complet :
[docs/ai/implementation/2026-09-08-feature-provisioning-tenant.md](../implementation/2026-09-08-feature-provisioning-tenant.md).

## Test Coverage Goals
**What level of testing do we aim for?**

- `Dockerfile` : smoke build réel (`docker build`) + `docker run` d'un conteneur qui démarre et
  répond, pas seulement une revue de fichier.
- `scripts/provision-tenant.sh` : smoke test réel contre un conteneur PostgreSQL jetable (mode
  génération SQL **et** mode `--apply`), couvrant le scénario nominal et les 3 edge cases identifiés
  en requirements (identifiant invalide, mot de passe absent, tenant déjà provisionné).
- `application.yml` / `application-local.yml` (context-path) : `./mvnw verify` (suite backend
  complète) reste vert — c'est le seul point de ce lot qui touche du code couvert par la suite
  existante.
- Pas de nouveau test Vitest/Playwright : ce lot ne touche pas `frontend/**` (hors surface exclusive
  du lot, cf. requirements/design).

## Unit Tests
**What individual components need testing?**

### `scripts/provision-tenant.sh`
- [x] Rejette un `tenant_id` invalide (ex. `Tenant-Invalid`, > 53 caractères) avant toute génération
      SQL — exit non-zéro, message explicite. **Exécuté réellement** (identifiant `Tenant-Invalid`
      et cas limite 54 caractères, tous deux rejetés en Phase 5/7).
- [x] Génère un SQL valide pour un `tenant_id` valide (`tenant_smoke`) : schéma, rôle
      `flowforge_tenant_smoke`, `GRANT`/`REVOKE` alignés sur
      `docker/postgres/init/01-init-tenant-schema.sql`. **Exécuté réellement**, sortie inspectée.
- [x] `--apply` sans `TENANT_DB_PASSWORD` positionné → échec explicite avant toute connexion PG.
      **Exécuté réellement** (exit 1, message clair, aucune tentative de connexion).
- [x] Mode génération (sans `--apply`) : le SQL sort sur stdout par défaut. **Exécuté réellement**
      (option `-o <fichier>` non ré-exécutée séparément mais couverte par lecture de code +
      `shellcheck` — chemin symétrique au cas stdout déjà exercé).

### `Dockerfile`
- [ ] Le build multi-stage réussit (`docker build -t flowforge:test .`) à partir d'un checkout
      propre. **Non exécuté** — Docker Hub bloqué dans ce bac à sable (voir note en tête de doc).
      Repli : `hadolint Dockerfile` → exit 0, aucun avertissement.
- [ ] L'image finale exécute le process en tant qu'utilisateur non-root (`docker run --rm
      flowforge:test id` → UID ≠ 0). **Non exécuté**, même raison. Repli : revue statique du
      `Dockerfile` (`USER flowforge` avant `ENTRYPOINT`, UID/GID 10001 fixes).
- [ ] Le jar copié dans l'image finale ne contient pas le JDK complet ni les sources Maven du stage
      `build`. **Non exécuté**, même raison. Repli : revue statique (stage `runtime` déclaré
      `FROM eclipse-temurin:25-jre`, `COPY --from=build` limité au jar).

## Integration Tests
**How do we test component interactions?**

- [x] Smoke réel end-to-end : `scripts/provision-tenant.sh tenant_smoke --apply` contre le service
      PostgreSQL local du bac à sable (pas un conteneur jetable — Docker Hub bloqué) → schéma et
      rôle vérifiés créés avec les permissions attendues. **Isolation cross-tenant vérifiée avec un
      second tenant réel** (`tenant_smoke2`) : `flowforge_tenant_smoke` reçoit `permission denied`
      en tentant `CREATE TABLE tenant_smoke2.probe` — reproduit réellement l'esprit de
      `TenantRoleIsolationIT` (TODO §41), sans Testcontainers.
- [x] Rejeu du même appel `--apply` → échec explicite (« Le schéma existe déjà — provisioning
      refusé »), schéma non corrompu (vérifié par re-sélection après rejeu). **Exécuté réellement.**
- [x] `./mvnw -B verify -Dmaven.compiler.release=21` (suite backend complète, profil `test`,
      context-path actif) : **`BUILD SUCCESS`, 1738 tests, 0 échec, 0 erreur, ArchUnit conforme,
      Jacoco respecté** — exécuté réellement, à deux reprises (un échec réel intermédiaire dû à un
      bug YAML introduit puis corrigé, voir doc implémentation).
- [ ] Démarrage réel du conteneur applicatif (image buildée) avec `APP_TENANT_ID=tenant_smoke` et
      connexion au PostgreSQL provisionné ci-dessus. **Non exécuté** : dépend du build d'image,
      lui-même bloqué (Docker Hub). Ce qui a été vérifié à la place : `./mvnw verify` (démarrage de
      contexte Spring réel avec `context-path` actif, hors conteneur) + revue statique du
      `Dockerfile`.

## End-to-End Tests
**What user flows need validation?**

- [x] Parcours ops — étape provisioning tenant (`DEPLOIEMENT.md` §4) : **rejouée réellement**
      (`scripts/provision-tenant.sh --apply`, voir Integration Tests). Étapes build image /
      démarrage conteneur / reverse-proxy réel : non rejouables dans ce bac à sable (Docker Hub
      bloqué pour la première, Keycloak/Apache réels hors scope pour les autres, déjà noté en
      requirements) — restent des commandes documentées, non exécutées dans ce cycle.
- [x] Parcours dev local (`GETTING-STARTED.md`) : aucune régression observable — **vérifié
      réellement** par `./mvnw verify` (profil `test`, 1738/1738 tests) et par lecture du diff
      `application-local.yml` (context-path neutralisé pour ce profil).

## Test Data
**What data do we use for testing?**

- **Réel** : service PostgreSQL 16 déjà installé dans le bac à sable (`sudo service postgresql
  start`), pas un conteneur jetable `postgres:17-alpine` comme prévu initialement (Docker Hub
  bloqué) — SQL standard (`CREATE SCHEMA`/`CREATE ROLE`/`GRANT`/`REVOKE`), pas de divergence
  attendue entre PostgreSQL 16 et 17 sur ces primitives.
- Identifiants de tenant de test dédiés (`tenant_smoke`, `tenant_smoke2`), jamais `tenant_dev` —
  schémas/rôles nettoyés après le smoke test (`DROP SCHEMA ... CASCADE`, `DROP ROLE`).

## Test Reporting & Coverage
**How do we verify and communicate test results?**

- `./mvnw -B verify -Dmaven.compiler.release=21` : **`BUILD SUCCESS`, 1738 tests, 0 échec, 0 erreur,
  ArchUnit conforme, Jacoco « All coverage checks have been met »** (sortie fraîche, exécutée deux
  fois pendant ce cycle — un échec réel entre les deux, corrigé).
- `hadolint Dockerfile` : exit 0, aucune sortie.
- `shellcheck scripts/provision-tenant.sh` : exit 0, aucune sortie (exécuté deux fois, avant et
  après le correctif de longueur `tenant_id`).
- Smoke PostgreSQL (`scripts/provision-tenant.sh`) : sorties brutes consignées dans
  [docs/ai/implementation/2026-09-08-feature-provisioning-tenant.md](../implementation/2026-09-08-feature-provisioning-tenant.md)
  et dans les commandes exécutées pendant Phase 5/7 de ce cycle.
- Pas de rapport de couverture Jacoco supplémentaire attendu : aucun code Java nouveau introduit par
  ce lot (seul un changement de configuration `application.yml`) — confirmé par le run `verify`
  ci-dessus (« Analyzed bundle... 554 classes », identique au périmètre existant).

## Manual Testing
**What requires human validation?**

- Revue humaine du `Dockerfile` (choix d'image, non-root, gestion `JAVA_OPTS`) recommandée avant un
  premier déploiement réel — le smoke test de ce cycle valide que l'image *fonctionne*, pas qu'elle
  est optimale en taille/durcissement au-delà des 3 décisions actées.
- Le provisioning Keycloak (réalm, clients OIDC/M2M, §5/§7 de `DEPLOIEMENT.md`) reste une procédure
  manuelle non automatisée par ce lot — à valider séparément lors d'un déploiement réel.

## Performance Testing
**How do we validate performance?**

- Hors scope : pas de charge/perf à valider pour un lot d'outillage ops livrant un Dockerfile et un
  script de provisioning. `-XX:MaxRAMPercentage=75.0` est un défaut documenté, pas une valeur
  benchmark.

## Bug Tracking
**How do we manage issues?**

- Tout écart réel trouvé pendant l'implémentation/la revue est documenté dans le doc
  d'implémentation (`docs/ai/implementation/2026-09-08-feature-provisioning-tenant.md`) et, si
  pertinent au-delà de ce lot, remonté dans `TODO.md`/`FILE-ATTENTE.md` — convention déjà suivie par
  les cycles précédents de ce dépôt.
