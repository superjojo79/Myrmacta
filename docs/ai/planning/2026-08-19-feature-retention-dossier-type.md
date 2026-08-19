---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: retention-dossier-type
sequencing: layered
phase1: 2026-08-19
---

# Project Planning & Task Breakdown — `retention-dossier-type`

Plan **initial Phase 1** (brouillon). À valider / figer en Phase 4 après
review requirements (P2) et design (P3).

Task tracing : indisponible (`npx ai-devkit@latest task` → unknown command).

## Milestones

- [ ] **M1 — Modèle + Liquibase + settings**
- [ ] **M2 — Override type + résolution politique**
- [ ] **M3 — Job évaluation + outbox**
- [ ] **M4 — API REST**
- [ ] **M5 — Admin SPA + Playwright**
- [ ] **M6 — Docs TODO / FILE-ATTENTE / DEFERRED**

## Task Breakdown

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Settings

- [ ] **T1. Domaine `TenantRetentionSettings` + ports + use cases (TDD)**
  - **Outcome :** modèle, guard admin, upsert/get.
  - **Depends on :** —
  - **Validation :** tests unitaires use case.
  - **Tests liés :** testing § settings.

- [ ] **T2. JPA + Liquibase settings**
  - **Outcome :** table / ligne settings ; adapter.
  - **Depends on :** T1
  - **Validation :** IT persistence.

### M2 — Type override

- [ ] **T3. Étendre `DossierType` + validation rétention (TDD)**
  - **Outcome :** champs + `withUpdate` ; create/update use cases.
  - **Depends on :** —
  - **Validation :** tests create/update type.

- [ ] **T4. `RetentionPolicyResolver` (TDD)**
  - **Outcome :** héritage / jours / NEVER.
  - **Depends on :** T3
  - **Validation :** tests resolver.

- [ ] **T5. Liquibase + JPA colonnes type**
  - **Depends on :** T3
  - **Validation :** IT round-trip.

### M3 — Job + outbox

- [ ] **T6. Étendre `Dossier` (`purgeEligibleAt`) + factory archivage rétention**
  - **Depends on :** —
  - **Validation :** tests record.

- [ ] **T7. `PurgeOutboxRecord` + port + Liquibase `purge_outbox`**
  - **Depends on :** —
  - **Validation :** IT insert PENDING.

- [ ] **T8. `EvaluateDossierRetentionUseCase` (TDD)**
  - **Outcome :** batch idempotent ; D-FORCE respecté.
  - **Depends on :** T4, T6, T7, T1
  - **Validation :** tests use case (cas testing §).

- [ ] **T9. Job scheduler + propriétés yaml + index scan**
  - **Depends on :** T8
  - **Validation :** IT job / slice.

### M4 — API

- [ ] **T10. Controller settings + DTOs**
  - **Depends on :** T2
  - **Validation :** MockMvc.

- [ ] **T11. Étendre DTOs / controller `DossierType` + `DossierResponse`**
  - **Depends on :** T5, T6
  - **Validation :** MockMvc.

### M5 — Frontend

- [ ] **T12. Clients API + types TS (TDD Vitest)**
  - **Depends on :** T10, T11 (contrats)
  - **Validation :** `npm run test`.

- [ ] **T13. UI admin rétention + types**
  - **Depends on :** T12
  - **Validation :** specs composants.

- [ ] **T14. Playwright E2E-RET-***
  - **Depends on :** T13
  - **Validation :** `npm run test:e2e`.

### M6 — Docs produit

- [ ] **T15. Clôturer TODO §21, FILE-ATTENTE, DEFERRED ; success criteria**
  - **Depends on :** T14 + verify
  - **Validation :** lint feature + verify evidence.

## Dependencies

T1→T2→T10 ; T3→T4→T8 ; T3→T5→T11 ; T6∥T7→T8→T9 ; T10∥T11→T12→T13→T14→T15.

## Timeline & Estimates

Effort technique : modèle + job + admin (pas de purge I/O). Pas d’estimation
calendaire (agents autonomes).

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Confusion ARCHIVE métier vs purge | D-FORCE + tests « ARCHIVE sans outbox » |
| Settings absents → job silencieux | Warn + doc ; UI force config |
| Scope creep §22 | Outbox PENDING seulement ; pas d’adaptateur S3 |
| A-REOPEN mal implémenté | Ne pas ajouter de transitions ; doc seulement |
| Scan full table | Index `(status, closed_at)` + batch-size |

## Resources Needed

- Skills : flowforge-conventions, tdd, verify, vue*, vitest, playwright.
- CI : `./mvnw verify`, job `frontend`.
