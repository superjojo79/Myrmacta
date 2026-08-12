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
sequencing: two-waves
---

# Project Planning & Task Breakdown

Plan aligné requirements **`reviewed`** (Phase 2) + design draft.
**Aucune tâche d’implémentation démarrée.**

Traçage `task` CLI : **indisponible**.

Branche : `cursor/feature-frontend-21f2` · lifecycle `feature-frontend`.

## Milestones

### Vague 1 — Instructeur (**D-SEQ**)
- [ ] **M1** Scaffold & qualité
- [ ] **M2** Auth BFF (`id`=sub, champ `name`) & shell (sans Rapports)
- [ ] **M3** Backend deltas inbox + elements list + auth `id`
- [ ] **M4** Création dossier + détail + status + viz + start/complete/reassign
- [ ] **M5** Éléments, SSE, Supervision MVP, Mes tâches (`/mine`)
- [ ] **M6** Packaging Maven → `static/` + preuves vague 1

### Vague 2 — Admin
- [ ] **M7** Admin types + ACL
- [ ] **M8** Admin templates + connectors + workflow-type-configs
- [ ] **M9** Preuves admin + sync docs

## Task Breakdown

Statuts : `[ ]` · `[~]` · `[x]` · `[!]`

### M1 — Scaffold
- [ ] **T1** Init Vite `frontend/` vue-ts strict + structure dossiers
- [ ] **T2** Deps PrimeVue/Pinia/Axios/Router/bpmn-js + Vitest/Playwright/ESLint/Prettier/vue-tsc
- [ ] **T3** Scripts `typecheck|lint|test|test:e2e|build` + README FE

### M2 — Auth & shell
- [ ] **T4** Axios CSRF + ProblemDetail helper
- [ ] **T5** Backend : `AuthUserResponse(id, name, email, roles)` + MockMvc (**D-ME**)
- [ ] **T6** Auth store boot/logout + wiring
- [ ] **T7** Shell/nav tokens maquette ; **pas** Rapports (**D-MOCKGAP**)

### M3 — Backend deltas vague 1
- [ ] **T8** `GET /api/workflow-tasks/mine` + use case/port/tests (**D-INBOX**)
- [ ] **T9** `GET /api/dossiers/{id}/elements` + tests (**D-ELEMENTS-API**)

### M4 — Dossier cœur FE
- [ ] **T10** API clients dossiers/types/workflows/viz/mine/elements
- [ ] **T11** CreateDossier (`dossierTypeCode`, `label`, fields) + SensitiveValue
- [ ] **T12** DossierDetailView + store (load id/ref) + **PATCH status** (**D-STATUS**)
- [ ] **T13** BpmnViewer + history/tasks (409 sans PI)
- [ ] **T14** Start workflow + complete `{}` + reassign UI (**D-ACTIONS**, **D-COMPLETE**)

### M5 — Compléments vague 1
- [ ] **T15** ElementsList via `/dossiers/{id}/elements` + download ; Ajouter disabled
- [ ] **T16** Notifications SSE + mark-read local
- [ ] **T17** Supervision MVP (ref/id + lien créer ; sans faux KPI)
- [ ] **T18** MobileTasks via `/mine` (**D-INBOX**, **A-MOBILE**)

### M6 — Packaging vague 1
- [ ] **T19** `frontend-maven-plugin` + gitignore + fallback `index.html`
- [ ] **T20** Playwright E2E (session mock) + coverage Vitest
- [ ] **T21** `./mvnw verify` evidence + docs sync vague 1

### M7 — Admin types
- [ ] **T22** UI CRUD dossier-types
- [ ] **T23** UI ACL type

### M8 — Admin reste (**D-ADMIN-RES**)
- [ ] **T24** UI document-templates (activate/archive/new-version)
- [ ] **T25** UI connectors + definitions (secrets redact)
- [ ] **T26** UI workflow-type-configs + observers

### M9 — Clôture
- [ ] **T27** E2E admin smoke + Vitest admin
- [ ] **T28** Evidence finale + DEFERRED/lint + Phase 7/8/9 docs

## Dependencies

```text
Vague 1: T1→T2→T3 ; T2→T4→T6→T7 ; T5∥T4 ; T5→T8 ; T5→T9
         T4→T10→T11→T12→T13→T14 ; T9→T15 ; T7→T16 ; T12→T17 ; T8→T18
         T1…T18→T19→T20→T21
Vague 2: T21→T22→T23 ; T21→T24||T25||T26 ; →T27→T28
```

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Scope admin | Ne pas démarrer M7 avant T21 vert |
| Inbox Flowable query | Tests unit + MockMvc ; tenant-safe |
| Node/Maven | Version Node figée dans plugin |
| TODO §24 liste | Supervision MVP uniquement |

## Resources

Skills vue / pinia / vite / vitest / playwright / flowforge-conventions /
tdd / verify. Maquette HTML + analysis 11.

## Next

Phase 3 — Review Design (`dev-design`).
