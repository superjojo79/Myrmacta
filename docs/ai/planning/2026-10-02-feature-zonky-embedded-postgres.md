---
phase: planning
title: PostgreSQL embarqué (zonky) — plan
description: Phases P0 à P6
status: done
created: 2026-10-02
updated: 2026-10-02
feature: zonky-embedded-postgres
---

# Planning — `zonky-embedded-postgres`

- [x] P0 — Baseline (voir requirements) ; JDK 25 installé dans le bac à sable, exécution sous `ubuntu`.
- [x] P1 — `EmbeddedPostgresSupport`, `EmbeddedPostgresEnvironmentPostProcessor`, ITs PostgreSQL migrés (plus de SKIPPED).
- [x] P2 — Tests de concurrence sur PostgreSQL réel : `SequentialReferenceMultiPodIT` a **révélé un bug** (voir implementation).
- [x] P3 — Bascule des `@SpringBootTest` (datasource H2 supprimé) ; écarts H2/PostgreSQL corrigés côté tests.
- [x] P4 — H2 et `testcontainers-postgresql` retirés ; branches `dbms: h2` et `*-h2.sql` supprimées ; `ChangelogChecksumStabilityIT` ; `*IT` dans `verify` ; CI mise à jour.
- [x] P5 — Profil `embedded-db` persistant, `scripts/embedded-db.sh`, Dockerfile (`--build-arg MAVEN_PROFILES=embedded-db`), garde-fou jar en CI.
- [x] P6 — Documentation (GETTING-STARTED, DEPLOIEMENT, `.env.example`, CHANGELOG, TODO §91, analysis doc 47).
