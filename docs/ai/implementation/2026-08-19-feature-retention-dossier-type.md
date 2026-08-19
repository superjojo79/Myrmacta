---
phase: implementation
title: Implementation Plan
description: Track implementation progress for retention-dossier-type
status: done
created: 2026-08-19
updated: 2026-08-19
feature: retention-dossier-type
phase5: 2026-08-19
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

## Design deviations
- Job `@Transactional` sur le batch entier (domaine sans Spring) — documenté sur le job.

## Evidence
- `./mvnw verify -Dmaven.compiler.release=21` → BUILD SUCCESS (Jacoco OK)
- `cd frontend && npm run test` → 114 passed
- `cd frontend && CI=1 npm run test:e2e` → 27 passed
