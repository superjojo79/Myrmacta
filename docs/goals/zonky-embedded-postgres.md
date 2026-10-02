# Goal — PostgreSQL embarqué (zonky) pour les tests et les démos/QA

> Usage : dans une session Claude Code ouverte à la racine du dépôt, lancer :
>
> ```
> /goal Exécute le goal décrit dans docs/goals/zonky-embedded-postgres.md (section « Prompt »).
> ```
>
> Le prompt ci-dessous est la source de vérité. Le modifier dans ce fichier plutôt que dans le chat.

## Prompt

Remplacer H2 (et Testcontainers) par un PostgreSQL embarqué zonky (io.zonky.test:embedded-postgres) pour tous les tests, et ajouter un mode "embedded" à données PERSISTANTES pour les démos/QA.

### Contexte (vérifié dans le dépôt)
- Aucun PostgreSQL de test n'est disponible ; le daemon Docker n'est pas utilisable dans ce bac à sable. Les 4 classes qui utilisent `PostgresTestContainerSupport` (Testcontainers, postgres:16-alpine) sont SKIPPED ici.
- ~97 classes @SpringBootTest tournent sur H2 en mémoire (`src/test/resources/application.properties`, `MODE=PostgreSQL`, `DEFAULT_NULL_ORDERING=HIGH`, `INIT=CREATE SCHEMA tenant_dev`).
- Les changelogs Liquibase portent des branches `dbms: h2` et des SQL dupliqués `*-h2.sql` (002, 003, 006, 025, 026, 009, 016…) : une émulation à supprimer.
- Des invariants de concurrence sont testés sur H2 (`ConcurrentWorkflowReservationTest`, `FlowableLockDimensioningTest`, purge) : ils doivent passer sur un vrai PostgreSQL.
- Prod/dev : PostgreSQL 17 (docker-compose), schéma + rôle par tenant (`docker/postgres/init/01-init-tenant-schema.sql`), `hikari.connection-init-sql: SET search_path TO ${app.tenant.id}, public`, Liquibase, Flowable 8, Spring Boot 4.1.1, Java 25.

### Contraintes non négociables
- Respecter le skill `flowforge-conventions` : hexagonal (le code embedded vit dans `infrastructure/config` ; ArchUnit doit rester vert), TDD, `verify` avant toute affirmation de succès, ne pas trancher silencieusement une décision ouverte.
- Ne JAMAIS modifier un changeSet Liquibase existant côté PostgreSQL (checksum). On ne supprime que les changeSets/branches `dbms: h2` ; vérifier avec un test de migration idempotente (rejeu sur une base déjà migrée) que les checksums PostgreSQL n'ont pas bougé.
- Le runtime embedded ne doit PAS être embarqué dans le jar de production : profil Maven dédié (ex. `-Pembedded-db`) + `@Profile("embedded-db")`/`@ConditionalOnClass`, avec fail-fast si le profil est actif en prod (ex. `app.env=prod`). La dépendance zonky est en scope `test` pour les tests, et en scope compile uniquement dans le profil Maven démo.
- Version : binaires PostgreSQL 17 via `embedded-postgres-binaries-bom`, alignés sur docker-compose.
- Ne pas créer de PR. Un commit par phase, sur la branche courante.

### Point à vérifier EN PREMIER
initdb/postgres refusent de tourner en root. Ce bac à sable tourne en root (uid=0) ; un utilisateur non-root `ubuntu` existe, et `runuser`/`sudo` sont disponibles. Contrôler `id`, puis faire fonctionner les tests sous un utilisateur non-root (ex. `runuser -u ubuntu -- ./mvnw verify`, avec ~/.m2 et le répertoire du projet accessibles à cet utilisateur) et documenter la procédure. Prévoir aussi un fallback `TEST_PG_URL` (PostgreSQL externe, schémas créés/nettoyés par le support). Vérifier la variante Alpine/arm64 des binaires si nécessaire, et que le téléchargement Maven passe par le proxy.

### Phases
P0 — Baseline : relever la durée de `./mvnw verify`, le nombre de tests passés/ignorés, et la liste des tests actuellement SKIPPED faute de Docker. Consigner dans `docs/ai/` (feature `zonky-embedded-postgres`, suivre dev-lifecycle : requirements, design, planning).
P1 — Support de test : remplacer `PostgresTestContainerSupport` par un support zonky : une instance par JVM (singleton, arrêt propre), un schéma unique par contexte Spring/classe de test, rôle applicatif non-superuser pour reproduire `TenantRoleIsolationIT`, `search_path` via `connection-init-sql` comme en prod, migration via le vrai changelog. Prévoir `TEST_PG_URL`. Migrer les 4 ITs existants et vérifier qu'ils s'exécutent (plus de SKIPPED).
P2 — Tests de concurrence : basculer d'abord `ConcurrentWorkflowReservationTest`, `FlowableLockDimensioningTest` et les tests de purge sur zonky. Si un test passait sur H2 mais échoue sur PostgreSQL, c'est une découverte : le documenter, ne pas l'ignorer ni le désactiver.
P3 — Migration en masse : basculer les @SpringBootTest vers zonky (supprimer le datasource H2 de `src/test/resources/application.properties`). Traiter les écarts (ordre des NULL, messages d'erreur, types). Mesurer la durée totale avant/après et la reporter.
P4 — Suppression de l'émulation : retirer H2 du pom, les branches `dbms: h2` et les fichiers `*-h2.sql` devenus morts ; retirer Testcontainers du pom s'il n'est plus utilisé (le justifier). Mettre à jour `ci.yml` (plus de dépendance à Docker pour les tests).
P5 — Mode démo/QA persistant : profil Spring `embedded-db` qui démarre un PostgreSQL zonky AVANT la création du DataSource (EnvironmentPostProcessor ou équivalent, dans le style de `SecretsEnvironmentPostProcessor`) et fixe `spring.datasource.*`. Exigences :
  - répertoire de données persistant et configurable (`APP_EMBEDDED_DB_DATA_DIR`, défaut hors du dépôt, ex. `~/.flowforge/pgdata`), `cleanDataDirectory=false`, port configurable (défaut fixe, 0 = aléatoire) ;
  - bootstrap idempotent du schéma tenant et du rôle applicatif, en réutilisant la même source SQL que `docker/postgres/init/01-init-tenant-schema.sql` (pas de copie divergente) ;
  - arrêt propre (shutdown hook, arrêt "fast") pour ne pas corrompre les données ; verrou/détection d'une seconde instance sur le même répertoire ;
  - marqueur de version majeure PostgreSQL dans le répertoire : refuser de démarrer avec un message clair si la version diffère ;
  - commande de reset documentée (script `scripts/embedded-db-reset.sh` ou équivalent) et procédure de sauvegarde (pg_dump ou copie à froid) ;
  - utilisable dans le Dockerfile existant pour une démo (volume pour le répertoire de données, utilisateur non-root).
  Tests : redémarrage de l'application → les données écrites avant l'arrêt sont toujours présentes ; refus du profil en prod ; refus si version PG différente.
P6 — Docs : GETTING-STARTED.md (lancer sans Docker ni PG local, mode démo, reset, limites), DEPLOIEMENT.md (le mode embedded est interdit en production : PostgreSQL géré), `.env.example` (toute nouvelle variable), CHANGELOG.md (`[Non publié]`, format du dépôt), TODO.md (point de la feature + section « Impacte »), et un ADR/décision dans `analysis doc/` remplaçant la décision test D-TESTCONTAINERS-EXEC.

### Critères d'acceptation (preuves attendues, sorties de commandes à l'appui)
1. `./mvnw verify` vert sans Docker et sans PostgreSQL préinstallé ; 0 test SKIPPED pour cause d'absence de Docker.
2. H2 et les branches `dbms: h2` absents du dépôt ; migration idempotente prouvée (rejeu, checksums PostgreSQL inchangés).
3. Les tests de concurrence et d'isolation tenant (`TenantSchemaIsolationIT`, `TenantRoleIsolationIT`) tournent sur PostgreSQL réel.
4. Durée avant/après de `./mvnw verify` mesurée et reportée ; si la durée augmente de plus de 50 %, proposer des optimisations (partage d'instance, schémas réutilisés) avant de conclure.
5. Profil `embedded-db` : données persistantes après redémarrage, démontré par un test automatisé ; absent du jar de production (vérifier le contenu du jar sans le profil Maven).
6. ArchUnit, ESLint/Vitest/Playwright inchangés et verts ; documentation et CHANGELOG à jour.

### Hors périmètre
Supabase, toute migration de la base de production, changement du modèle multi-tenant, Keycloak.
