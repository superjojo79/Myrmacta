---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: done
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
sequencing: layered
phase1: 2026-08-19
phase4: 2026-08-19
phase5: 2026-08-19
phase6: 2026-08-19
phase7: 2026-08-19
phase8: 2026-08-19
design_aligned: 2026-08-19
requirements_ref: docs/ai/requirements/2026-08-19-feature-purge-stockage.md
design_ref: docs/ai/design/2026-08-19-feature-purge-stockage.md
testing_ref: docs/ai/testing/2026-08-19-feature-purge-stockage.md
---

# Project Planning & Task Breakdown — `purge-stockage`

Plan **Phase 4** figé après requirements `reviewed` et design `reviewed`.
**Phase 5** livrée (T1–T15). **Phase 6** : toutes les tâches `[x]`.

Task tracing : indisponible (`npx ai-devkit@latest task` → unknown command).

TDD : test rouge → code → vert. Claim final : `./mvnw verify` +
`cd frontend && npm run test` + `npm run test:e2e`.

## Milestones

- [x] **M1 — `DocumentStoragePort.delete` (FS + S3)**
- [x] **M2 — Outbox lifecycle (status, origin, claim, Liquibase 011)**
- [x] **M3 — Deletes graphe PG**
- [x] **M4 — Use cases purge + enqueue + Flowable**
- [x] **M5 — HTTP + job**
- [x] **M6 — SPA `/admin/purge-outbox` + E2E**
- [x] **M7 — Docs produit + verify**

## Task Breakdown

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Port stockage

- [x] **T1. Contrat `DocumentStoragePort.delete` (TDD)**
  - **Outcome :** méthode port ; missing = succès ; ref invalide → exception.
  - **Depends on :** —
  - **Validation :** `DocumentGenerationPortsContractTest`.
  - **Tests liés :** testing § Port stockage.

- [x] **T2. `FilesystemStorageAdapter.delete` (TDD)**
  - **Depends on :** T1
  - **Validation :** unit temp dir.

- [x] **T3. `S3StorageAdapter.delete` (TDD)**
  - **Depends on :** T1
  - **Validation :** mock `S3Client` ; `NoSuchKey` = succès.

### M2 — Outbox

- [x] **T4. Domaine status + origin + record (TDD)**
  - **Outcome :** `PROCESSING`/`COMPLETED`/`FAILED` ; `PurgeOutboxOrigin`
    `RETENTION`|`ADMIN` ; `lastError` optionnel.
  - **Depends on :** —
  - **Validation :** constructeur / égalité.

- [x] **T5. Liquibase 011 + JPA + repo find/claim/mark (TDD/IT)**
  - **Outcome :** colonnes `origin`, `last_error` ; index `(status, created_at)` ;
    `findRetryable` ; `claim` atomique ; `markCompleted`/`markFailed`.
  - **Depends on :** T4
  - **Validation :** `RetentionSchemaIT` étendu ; IT claim (`PurgeGraphPersistenceIT`).
  - **Tests liés :** testing § Liquibase + claim.

### M3 — Graphe PG

- [x] **T6. Deletes éléments / relations / links / dossier (TDD/IT)**
  - **Outcome :** méthodes repos ; ordre respecté par le use case.
  - **Depends on :** —
  - **Validation :** IT graphe puis empty.

### M4 — Use cases + Flowable

- [x] **T7. `WorkflowEnginePort.deleteProcessInstanceBestEffort` (TDD)**
  - **Depends on :** —
  - **Validation :** mock RuntimeService ; PI absente = no-op.

- [x] **T8. `ProcessPurgeOutboxUseCase` (TDD)**
  - **Outcome :** D-ELIGIBLE-GUARD ; D-ORDER ; D-WF ; COMPLETED/FAILED ; batch.
  - **Depends on :** T1–T7
  - **Validation :** tous cas unitaires testing § ProcessPurgeOutbox.

- [x] **T9. `EnqueueDossierPurgeUseCase` (TDD)**
  - **Outcome :** Q11 `CLOTURE`/`ARCHIVE` ; 4xx ouvert ; idempotent UNIQUE ;
    origin ADMIN.
  - **Depends on :** T4, T5
  - **Validation :** unit + 409 ouvert.

### M5 — HTTP + job

- [x] **T10. API GET `/api/purge-outbox` + POST enqueue (TDD MockMvc)**
  - **Outcome :** `TENANT_ADMIN` ; CSRF ; counts + items ; 403 autres.
  - **Depends on :** T8, T9
  - **Validation :** MockMvc 200/403/409/404.

- [x] **T11. `PurgeOutboxJob` + `app.purge.*` (TDD)**
  - **Depends on :** T8
  - **Validation :** job slice + yaml.

### M6 — Frontend

- [x] **T12. Client API TS (TDD Vitest)**
  - **Depends on :** T10 (contrat)
  - **Validation :** `npm run test`.

- [x] **T13. Vue `/admin/purge-outbox` lecture (TDD Vitest)**
  - **Outcome :** compteurs + liste ; pas de bouton force ; nav admin.
  - **Depends on :** T12
  - **Validation :** specs vue.

- [x] **T14. Playwright E2E-PURGE-1..5**
  - **Depends on :** T13
  - **Validation :** `npm run test:e2e`.

### M7 — Clôture

- [x] **T15. Docs FILE-ATTENTE / TODO §22 / DEFERRED + verify**
  - **Depends on :** T11, T14
  - **Validation :** `./mvnw verify` + Vitest + Playwright (skill `verify`).
  - **Evidence :** `./mvnw verify -Dmaven.compiler.release=21` → **832** tests,
    0 failures ; Vitest **120** ; Playwright **32** (E2E-PURGE-1..5).

## Traceability — testing → tasks

| Scenario | Task(s) |
|---|---|
| Port delete FS/S3 | T1–T3 |
| Liquibase 011 / claim | T5 |
| Graphe PG | T6 |
| Flowable best-effort | T7 |
| ProcessPurgeOutbox (tous cas) | T8 |
| Enqueue Q11 | T9, T10 |
| GET file 403/200 | T10 |
| Job | T11 |
| E2E-PURGE-1..5 | T14 |
| Rétention non-régression | T5, T15 |

## Risks

| Risque | Mitigation |
|---|---|
| Orphelins store | D-ORDER + test mid-flight |
| UNIQUE vs enqueue | Idempotent no-op |
| Q10 manquante Clos | restaurée Phase 3 |
| H2 vs `pg_indexes` | `information_schema.indexes` (comme NotificationSchemaIT) |
| Liste MockMvc `items[0]` | `hasItem` (autres PENDING possibles) |

## Sequencing

T1∥T4∥T6∥T7 → T5 → T8/T9 → T10/T11 → T12–T14 → T15.

## Progress (Phase 6)

Toutes les tâches T1–T15 **done**. Phase 7 alignée design ; Phase 8 tests
de couverture ajoutés. Suite cycle : Phase 9 (revue).
