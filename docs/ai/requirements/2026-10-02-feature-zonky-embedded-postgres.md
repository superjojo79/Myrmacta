---
phase: requirements
title: PostgreSQL embarqué (zonky) — tests et démos/QA
description: Remplacer H2 et Testcontainers par un PostgreSQL embarqué ; base persistante pour les démos/QA
status: implemented
created: 2026-10-02
updated: 2026-10-02
feature: zonky-embedded-postgres
todo_ref: TODO.md §91 · docs/goals/zonky-embedded-postgres.md
---

# Requirements — `zonky-embedded-postgres`

## Problème

- ~97 classes `@SpringBootTest` tournaient sur **H2 en mode PostgreSQL** (émulation) : branches `dbms: h2`
  et SQL dupliqués `*-h2.sql` dans les changelogs, index partiels « émulés », invariants de
  concurrence prouvés sur un moteur qui ne se comporte pas comme PostgreSQL.
- Les tests qui exigent un vrai PostgreSQL (`TenantSchemaIsolationIT`, `TenantRoleIsolationIT`,
  `SequentialReferenceMultiPodIT`) passaient par Testcontainers : **ignorés (SKIPPED) sans Docker**,
  donc jamais exécutés dans ce bac à sable.
- Aucune base persistante n'existe pour une démo/QA sans Docker Compose.

## Constats vérifiés (baseline, 2026-10-02)

| Mesure | Avant (H2) |
|---|---|
| `./mvnw verify -Dskip.frontend=true` | 2653 tests, 3 ignorés (`SignatureBrowserE2eTest`, `-Dsignature.e2e`), 1 erreur d'environnement (`OpenApiDisabledApiTest` : pas de `static/index.html` sans build frontend), 4 min 07 s (téléchargement des dépendances inclus) |
| `*IT` (`-Dtest='*IT'`) | 87 tests, 5 ignorés (3 PostgreSQL faute de Docker, 2 ClamAV), 62 s — **jamais exécutés par `verify`** (pas de Failsafe, surefire n'inclut pas `*IT`) |

## Décisions

| Id | Décision |
|---|---|
| D-ZONKY | PostgreSQL embarqué `io.zonky.test:embedded-postgres` 2.2.2, binaires **PostgreSQL 17** (BOM `embedded-postgres-binaries-bom` 17.11.0) — aligné sur `postgres:17-alpine` de docker-compose (les tests Testcontainers utilisaient la 16). |
| D-ZONKY-REPLACES-H2 | H2 est retiré (dépendance, datasource de test, branches `dbms: h2`, `sql/*-h2.sql`). **Remplace** D-TESTCONTAINERS-EXEC et la partie PostgreSQL de D-TESTCONTAINERS (feature `fiabilisation-tests-plateforme`). Testcontainers n'est conservé que pour `ClamAvAttachmentScanIT` (conteneur ClamAV). |
| D-ZONKY-ISOLATION | Une instance zonky par JVM de test ; **une base fraîche par contexte Spring** (`EmbeddedPostgresEnvironmentPostProcessor`), initialisée par le script réel `docker/postgres/init/01-init-tenant-schema.sql` (schéma `tenant_dev` + rôle applicatif non-superuser, comme en production). |
| D-ZONKY-NONROOT | `initdb` refuse de tourner en root : les tests se lancent sous un utilisateur non-root, ou avec `FLOWFORGE_TEST_PG_URL` / `_USER` / `_PASSWORD` (PostgreSQL externe). |
| D-IT-IN-VERIFY | Les `*IT` sont ajoutés aux inclusions surefire : ils tournent dans `verify` (plus de dépendance à Docker). |
| D-CHECKSUM-GUARD | `ChangelogChecksumStabilityIT` + `checksums-baseline.csv` : aucun changeSet déjà appliqué ne peut être modifié sans faire échouer le build. |
| D-EMBEDDED-DEMO | Profil Spring `embedded-db` (démos/QA) : PostgreSQL embarqué **persistant**, jamais dans le jar de production (profil Maven `embedded-db`), refusé avec un profil `prod`/`production`. |

## Hors périmètre

Supabase, migration de la base de production, modèle multi-tenant, Keycloak.
