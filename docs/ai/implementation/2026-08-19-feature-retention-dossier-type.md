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

## Phase 7 remediations (2026-08-19)

| Gap | Fix |
|---|---|
| D-BOOTSTRAP warn | `EvaluationResult.settingsAbsent` + job `log.warn` when settings missing |
| D-OUTBOX-UNIQ + PG TX | Removed catch of `DataIntegrityViolationException` in joining TX; exists-check then insert; rare race rolls back batch and retries next run |
| MockMvc overclaim | Added `RetentionApiTest` (settings 404/403/400/round-trip, type NEVER + inherit patch, `purgeEligibleAt` null) |

## Design deviations (residual)

| Id | Severity | Note |
|---|---|---|
| D-ARCHIVE-PATH / TX | low | Job wraps **entire batch** in one `@Transactional` (documented on job) vs design wording of one TX per dossier+outbox pair. Safer rollback; may hold locks longer. |
| Job IT / dedicated Liquibase IT | low | Covered indirectly by domain unit + MockMvc + global Liquibase on SpringBootTest; no dedicated scheduler IT. |

## Phase 7 alignment (2026-08-19)

Verdict: **implementation covers all D-\* / A-\* decisions**; remediations close bootstrap warn + outbox TX poison; MockMvc coverage added.

### Coverage (summary)
- ALIGNED: D-SCAN, D-ARCHIVE-PATH (UC path + batch TX), D-TYPE-VIS, D-SCHED, D-PURGE-FIELD, D-FORCE, D-JOB-ACTOR, D-POLICY-CHANGE, D-UI-LOC, A-CLOCK, A-NULL-CLOSEDAT, A-PATCH-INHERIT, A-BATCH-COUNT, D-OUTBOX-UNIQ (schema + exists + fail-closed race), D-BOOTSTRAP (GET 404 + job no-op **with warn**).
- Security: `@PreAuthorize TENANT_ADMIN` + `TenantAdminGuard` on settings/mutations.
- `UpdateDossierStatusUseCase` bypassed for retention archive (`withRetentionArchive`).
- Physical purge / S3 delete: **absent** (correct non-goal).

## Evidence
- Phase 5: `./mvnw verify -Dmaven.compiler.release=21` SUCCESS; Vitest 114; Playwright 27 (E2E-RET-1/2/3)
- Phase 7 remediations: `./mvnw -Dmaven.compiler.release=21 -Dtest=RetentionApiTest,EvaluateDossierRetentionUseCaseImplTest test` → 16 tests, 0 failures
