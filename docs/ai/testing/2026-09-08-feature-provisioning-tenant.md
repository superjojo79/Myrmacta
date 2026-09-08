---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy — `provisioning-tenant` (lot A1)

Lot ops/outillage : pas de code applicatif Java nouveau (pas de use case/entité/port), donc pas de
suite JUnit dédiée. La stratégie de validation combine revue statique, smoke tests réels (Docker est
disponible dans cet environnement d'exécution) et la suite backend existante pour le seul changement
de code applicatif du lot (`context-path`).

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
- [ ] Rejette un `tenant_id` invalide (ex. `Tenant-B`, `1abc`, chaîne vide, > 53 caractères) avant
      toute génération SQL — exit non-zéro, message explicite, aucune sortie SQL produite.
- [ ] Génère un SQL valide pour un `tenant_id` valide (ex. `tenant_b`) : schéma `tenant_b`, rôle
      `flowforge_tenant_b`, `GRANT`/`REVOKE` alignés sur `docker/postgres/init/01-init-tenant-schema.sql`.
- [ ] `--apply` sans `TENANT_DB_PASSWORD` positionné → échec explicite avant toute connexion PG.
- [ ] Mode génération (sans `--apply`) : le SQL sort sur stdout par défaut ; `-o <fichier>` écrit
      dans le fichier indiqué.

### `Dockerfile`
- [ ] Le build multi-stage réussit (`docker build -t flowforge:test .`) à partir d'un checkout
      propre.
- [ ] L'image finale exécute le process en tant qu'utilisateur non-root (`docker run --rm
      flowforge:test id` → UID ≠ 0).
- [ ] Le jar copié dans l'image finale ne contient pas le JDK complet ni les sources Maven du stage
      `build` (image runtime basée sur `-jre`, pas `-jdk`).

## Integration Tests
**How do we test component interactions?**

- [ ] Smoke réel end-to-end : `docker run` d'un conteneur PostgreSQL jetable → exécuter
      `scripts/provision-tenant.sh tenant_smoke --apply` contre ce conteneur → vérifier que le
      schéma `tenant_smoke` et le rôle `flowforge_tenant_smoke` existent, avec les permissions
      attendues (le rôle ne voit que son propre schéma — reproduit l'esprit de
      `TenantRoleIsolationIT`, TODO §41, sans dépendre de Testcontainers/Java).
- [ ] Rejeu du même appel `--apply` sur le même conteneur → échec explicite (pas d'écrasement), sans
      corrompre le schéma déjà créé.
- [ ] `./mvnw verify` (suite backend complète, profil `test`, context-path actif par défaut dans ce
      profil) : zéro régression sur la suite existante malgré l'activation de
      `server.servlet.context-path`.
- [ ] Démarrage réel du conteneur applicatif (image buildée) avec `APP_TENANT_ID=tenant_smoke` et
      connexion au PostgreSQL provisionné ci-dessus si le temps du cycle le permet ; sinon,
      documenter précisément ce qui a été vérifié à la place (ex. `./mvnw verify` seul + revue
      statique du `Dockerfile`) plutôt que de prétendre un smoke complet non exécuté.

## End-to-End Tests
**What user flows need validation?**

- [ ] Parcours ops complet documenté dans `DEPLOIEMENT.md` : build image → provisionner un tenant →
      démarrer l'instance avec `context-path` actif → ajouter le bloc reverse-proxy — rejoué au
      moins partiellement (les étapes automatisables) pendant ce cycle, le reste (Keycloak, reverse
      proxy réel) restant hors de portée d'un smoke test en bac à sable (déjà noté hors scope en
      requirements).
- [ ] Parcours dev local (`GETTING-STARTED.md`) : aucune régression observable — vérifié par la
      suite `./mvnw verify` (profil `test`) et par lecture du diff `application-local.yml` (context
      path neutralisé pour ce profil).

## Test Data
**What data do we use for testing?**

- Conteneur PostgreSQL jetable (`postgres:17-alpine`, même image que `docker-compose.yml`), lancé et
  détruit pour le seul besoin du smoke test — pas de volume persistant, pas de dépendance à
  l'environnement de dev habituel.
- Identifiants de tenant de test dédiés (`tenant_smoke`, jamais `tenant_dev`) pour ne pas interférer
  avec un éventuel schéma de dev existant.

## Test Reporting & Coverage
**How do we verify and communicate test results?**

- `./mvnw verify` : sortie fraîche à citer dans le doc d'implémentation (nombre de tests, 0 échec),
  cf. `verify` skill — pas de claim sans sortie de commande récente.
- Sortie brute des smoke tests Docker/PostgreSQL consignée dans le doc d'implémentation (commandes +
  résultat), pas seulement « ça marche ».
- Pas de rapport de couverture Jacoco supplémentaire attendu : aucun code Java nouveau introduit par
  ce lot (seul un changement de configuration `application.yml`).

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
