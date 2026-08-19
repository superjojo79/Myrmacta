---
phase: implementation
title: Implementation Plan
description: Track implementation progress for retention-dossier-type
status: done
created: 2026-08-19
updated: 2026-08-19
feature: retention-dossier-type
phase5: 2026-08-19
phase7: 2026-08-19
---

# Implementation — `retention-dossier-type`

## Shipped (Phase 5)

### Backend
- Settings tenant : domaine + use cases + JPA + `GET/PUT /api/tenant/retention-settings`
- `DossierType` : `retentionDays` / `retentionNever` + API create/patch
- `Dossier.purgeEligibleAt` + `withRetentionArchive`
- `purge_outbox` UNIQUE(`dossier_id`) + `EvaluateDossierRetentionUseCase` + job `fixedDelay` 1h / batch 100
- Liquibase `010-retention-tables.yaml`

### Frontend
- `retentionSettings` client + bloc admin sur `DossierTypesAdminView`
- Champs rétention create/edit type
- Vitest + Playwright E2E-RET-1/2/3

## Design deviations (Phase 7)

| Id | Severity | Note |
|---|---|---|
| D-ARCHIVE-PATH / TX | low | Job wraps **entire batch** in one `@Transactional` (documented on job) vs design wording of one TX per dossier+outbox pair. Safer rollback semantics; may hold locks longer. |
| D-BOOTSTRAP warn | low | Settings-absent path is no-op but **no warn log** (requirements edge case said warn). |
| D-OUTBOX-UNIQ + PG | med | `insert` catches `DataIntegrityViolationException` inside a joining `@Transactional`; on PostgreSQL a unique violation can abort the **outer** batch TX. Prefer `REQUIRES_NEW` or native upsert for true concurrent idempotence. |

## Phase 7 alignment (2026-08-19)

Verdict: **implementation covers all D-\* / A-\* decisions in code**; residual gaps are mostly **tests vs testing-doc claims** and the TX/logging notes above.

### Coverage (summary)
- ALIGNED: D-SCAN, D-ARCHIVE-PATH (UC path), D-TYPE-VIS, D-SCHED, D-PURGE-FIELD, D-FORCE, D-JOB-ACTOR, D-POLICY-CHANGE, D-UI-LOC, A-CLOCK, A-NULL-CLOSEDAT, A-PATCH-INHERIT, A-BATCH-COUNT, D-OUTBOX-UNIQ (schema + attempt), D-BOOTSTRAP (GET 404 + job no-op, minus warn).
- Security: `@PreAuthorize TENANT_ADMIN` + `TenantAdminGuard` on settings/mutations.
- `UpdateDossierStatusUseCase` bypassed for retention archive (`withRetentionArchive`).
- Physical purge / S3 delete: **absent** (correct non-goal).

### Test gaps vs testing doc
Testing doc marks MockMvc / Liquibase IT / Job IT as `[x]`, but repo has **domain unit + FE only** for retention — no `*Retention*ApiTest`, no purge_outbox persistence IT, no job IT. Correct testing doc checkboxes or add those tests before claiming Phase 8 complete.

## Evidence
- `./mvnw verify -Dmaven.compiler.release=21` → BUILD SUCCESS (Jacoco OK) — Phase 5
- `cd frontend && npm run test` → 114 passed — Phase 5
- `cd frontend && CI=1 npm run test:e2e` → 27 passed — Phase 5
