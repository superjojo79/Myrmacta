---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: ready
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
sequencing: layered
phase1: 2026-08-19
phase4: 2026-08-19
design_aligned: 2026-08-19
requirements_ref: docs/ai/requirements/2026-08-19-feature-purge-stockage.md
design_ref: docs/ai/design/2026-08-19-feature-purge-stockage.md
testing_ref: docs/ai/testing/2026-08-19-feature-purge-stockage.md
---

# Project Planning & Task Breakdown — `purge-stockage`

Plan **Phase 4** figé après requirements `reviewed` et design `reviewed`.

Task tracing : indisponible (`npx ai-devkit@latest task` → unknown command).

TDD : test rouge → code → vert. Claim final : `./mvnw verify` +
`cd frontend && npm run test` + `npm run test:e2e`.

## Milestones

- [ ] **M1 — `DocumentStoragePort.delete` (FS + S3)**
- [ ] **M2 — Outbox lifecycle (status, origin, claim, Liquibase 011)**
- [ ] **M3 — Deletes graphe PG**
- [ ] **M4 — Use cases purge + enqueue + Flowable**
- [ ] **M5 — HTTP + job**
- [ ] **M6 — SPA `/admin/purge-outbox` + E2E**
- [ ] **M7 — Docs produit + verify**

## Task Breakdown

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Port stockage

- [ ] **T1. Contrat `DocumentStoragePort.delete` (TDD)**
  - **Outcome :** méthode port ; missing = succès ; ref invalide → exception.
  - **Depends on :** —
  - **Validation :** `DocumentGenerationPortsContractTest`.
  - **Tests liés :** testing § Port stockage.

- [ ] **T2. `FilesystemStorageAdapter.delete` (TDD)**
  - **Depends on :** T1
  - **Validation :** unit temp dir.

- [ ] **T3. `S3StorageAdapter.delete` (TDD)**
  - **Depends on :** T1
  - **Validation :** mock `S3Client` ; `NoSuchKey` = succès.

### M2 — Outbox

- [ ] **T4. Domaine status + origin + record (TDD)**
  - **Outcome :** `PROCESSING`/`COMPLETED`/`FAILED` ; `PurgeOutboxOrigin`
    `RETENTION`|`ADMIN` ; `lastError` optionnel.
  - **Depends on :** —
  - **Validation :** constructeur / égalité.

- [ ] **T5. Liquibase 011 + JPA + repo find/claim/mark (TDD/IT)**
  - **Outcome :** colonnes `origin`, `last_error` ; index `(status, created_at)` ;
    `findRetryable` ; `claim` atomique ; `markCompleted`/`markFailed`.
  - **Depends on :** T4
  - **Validation :** `RetentionSchemaIT` étendu / `PurgeSchemaIT` ; IT claim race.
  - **Tests liés :** testing § Liquibase + claim.

### M3 — Graphe PG

- [ ] **T6. Deletes éléments / relations / links / dossier (TDD/IT)**
  - **Outcome :** méthodes repos ; ordre respecté par le use case.
  - **Depends on :** —
  - **Validation :** IT graphe puis empty.

### M4 — Use cases + Flowable

- [ ] **T7. `WorkflowEnginePort.deleteProcessInstanceBestEffort` (TDD)**
  - **Depends on :** —
  - **Validation :** mock RuntimeService ; PI absente = no-op.

- [ ] **T8. `ProcessPurgeOutboxUseCase` (TDD)**
  - **Outcome :** D-ELIGIBLE-GUARD ; D-ORDER ; D-WF ; COMPLETED/FAILED ; batch.
  - **Depends on :** T1–T7
  - **Validation :** tous cas unitaires testing § ProcessPurgeOutbox.

- [ ] **T9. `EnqueueDossierPurgeUseCase` (TDD)**
  - **Outcome :** Q11 `CLOTURE`/`ARCHIVE` ; 4xx ouvert ; idempotent UNIQUE ;
    origin ADMIN.
  - **Depends on :** T4, T5
  - **Validation :** unit + 409 ouvert.

### M5 — HTTP + job

- [ ] **T10. API GET `/api/purge-outbox` + POST enqueue (TDD MockMvc)**
  - **Outcome :** `TENANT_ADMIN` ; CSRF ; counts + items ; 403 autres.
  - **Depends on :** T8, T9
  - **Validation :** MockMvc 200/403/409/404.

- [ ] **T11. `PurgeOutboxJob` + `app.purge.*` (TDD)**
  - **Depends on :** T8
  - **Validation :** job slice + yaml.

### M6 — Frontend

- [ ] **T12. Client API TS (TDD Vitest)**
  - **Depends on :** T10 (contrat)
  - **Validation :** `npm run test`.

- [ ] **T13. Vue `/admin/purge-outbox` lecture (TDD Vitest)**
  - **Outcome :** compteurs + liste ; pas de bouton force ; nav admin.
  - **Depends on :** T12
  - **Validation :** specs vue.

- [ ] **T14. Playwright E2E-PURGE-1..5**
  - **Depends on :** T13
  - **Validation :** `npm run test:e2e`.

### M7 — Clôture

- [ ] **T15. Docs FILE-ATTENTE / TODO §22 / DEFERRED + verify**
  - **Depends on :** T11, T14
  - **Validation :** `./mvnw verify` + Vitest + Playwright (skill `verify`).

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

## Sequencing

T1∥T4∥T6∥T7 → T5 → T8/T9 → T10/T11 → T12–T14 → T15.

## Next (Phase 5)

Démarrer **T1**.
