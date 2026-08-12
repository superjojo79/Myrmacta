---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-12
updated: 2026-08-12
feature: frontend
design_aligned: 2026-08-12
stakeholder_decisions: 2026-08-12
phase2_sync: 2026-08-12
phase3_sync: 2026-08-12
sequencing: two-waves
---

# Project Planning & Task Breakdown

Plan aligné requirements **`reviewed`** + design **`reviewed`** (Phase 3).
**Aucune tâche d’implémentation démarrée.** (Phase 4 formalisera
`status: ready`.)

Traçage `task` CLI : **indisponible**.

Branche : `cursor/feature-frontend-21f2` · lifecycle `feature-frontend`.

## Milestones

### Vague 1 — Instructeur (**D-SEQ**)
- [ ] **M1** Scaffold & qualité (Node 22)
- [ ] **M2** Auth BFF (`id`+`name`) & shell ; Vite `base: './'`
- [ ] **M3** Backend deltas : `/me` id, `/mine` enrichi, elements list
- [ ] **M4** Création + détail + status + viz + start (DD-START) + complete/reassign
- [ ] **M5** Éléments DTO, SSE, Supervision MVP, Mes tâches `/mine`
- [ ] **M6** Packaging Maven Node 22 → `static/` + SPA fallback + preuves

### Vague 2 — Admin
- [ ] **M7** Admin types + ACL
- [ ] **M8** Admin templates + connectors (mask) + workflow-type-configs
- [ ] **M9** Preuves admin + sync docs

## Task Breakdown

### M1 — Scaffold
- [ ] **T1** Init Vite `frontend/` vue-ts strict ; `base: './'` (**DD-BASE**)
- [ ] **T2** Deps PrimeVue/Pinia/Axios/Router/bpmn-js + Vitest/Playwright/ESLint/Prettier/vue-tsc
- [ ] **T3** Scripts gates + README FE ; engines Node 22

### M2 — Auth & shell
- [ ] **T4** Axios CSRF + ProblemDetail + résolution préfixe API (**DD-BASE**)
- [ ] **T5** Backend `AuthUserResponse(id, name, email, roles)` + MockMvc
- [ ] **T6** Auth store boot/logout
- [ ] **T7** Shell/nav tokens ; pas Rapports

### M3 — Backend deltas
- [ ] **T8** `ListMyTasksUseCase` + `GET /mine` enrichi (plafond 100) + tests (**DD-INBOX-***)
- [ ] **T9** `ListDossierElementsUseCase` + elements DTO sans storageRef + tests

### M4 — Dossier cœur FE
- [ ] **T10** API clients (mine, elements, status, start+Idempotency-Key)
- [ ] **T11** CreateDossier + DynamicFields + SensitiveValue
- [ ] **T12** DossierDetail + PATCH status
- [ ] **T13** BpmnViewer + history/tasks
- [ ] **T14** StartWorkflowDialog (**DD-START**) + complete `{}` + reassign

### M5 — Compléments vague 1
- [ ] **T15** ElementsList + download ; Ajouter disabled
- [ ] **T16** Notifications SSE + mark-read local
- [ ] **T17** Supervision MVP
- [ ] **T18** MobileTasks via `/mine`

### M6 — Packaging
- [ ] **T19** frontend-maven-plugin **Node 22** + gitignore + SPA fallback
- [ ] **T20** Playwright E2E mock + Vitest coverage
- [ ] **T21** `./mvnw verify` evidence + docs sync vague 1

### M7–M9 — Admin + clôture
- [ ] **T22** UI CRUD dossier-types
- [ ] **T23** UI ACL
- [ ] **T24** UI templates
- [ ] **T25** UI connectors (+ secretConfigured)
- [ ] **T26** UI workflow-type-configs + observers
- [ ] **T27** E2E/Vitest admin smoke
- [ ] **T28** Evidence finale + lint + Phase 7/8/9 docs

## Dependencies

```text
Vague 1: T1→T2→T3 ; T2→T4→T6→T7 ; T5∥T4 ; T5→T8 ; T5→T9
         T4→T10→T11→T12→T13→T14 ; T9→T15 ; T7→T16 ; T12→T17 ; T8→T18
         →T19→T20→T21
Vague 2: T21→T22→T23 ; T21→T24||T25||T26 ; →T27→T28
```

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Context-path | DD-BASE checklist |
| Inbox join cost | Cap 100 |
| Admin scope | Gate T21 avant M7 |

## Next

Phase 4 — Create Initial Plan (`ready`).
