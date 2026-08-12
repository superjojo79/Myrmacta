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
sequencing: two-waves
---

# Project Planning & Task Breakdown

Plan **phase 1** après décisions stakeholder 2026-08-12.
**Aucune tâche d’implémentation démarrée.**

Traçage `task` CLI : **indisponible**.

Branche : `cursor/feature-frontend-21f2` · lifecycle `feature-frontend`.

## Milestones

### Vague 1 — Instructeur (**D-SEQ**)
- [ ] **M1** Scaffold & qualité
- [ ] **M2** Auth BFF (`id`=sub) & shell (sans Rapports)
- [ ] **M3** Création dossier + détail + viz + start/complete/reassign
- [ ] **M4** Éléments, SSE, Supervision MVP, Mes tâches
- [ ] **M5** Packaging Maven → `static/` + preuves vague 1

### Vague 2 — Admin
- [ ] **M6** Admin types + ACL
- [ ] **M7** Admin templates + connectors + workflow-type-configs
- [ ] **M8** Preuves admin + sync docs

## Task Breakdown

Statuts : `[ ]` · `[~]` · `[x]` · `[!]`

### M1 — Scaffold
- [ ] **T1** Init Vite `frontend/` vue-ts strict + structure dossiers
- [ ] **T2** Deps PrimeVue/Pinia/Axios/Router/bpmn-js + Vitest/Playwright/ESLint/Prettier/vue-tsc
- [ ] **T3** Scripts `typecheck|lint|test|test:e2e|build` + README FE

### M2 — Auth & shell
- [ ] **T4** Axios CSRF + ProblemDetail helper
- [ ] **T5** Backend : `AuthUserResponse.id` + test MockMvc (**D-ME**)
- [ ] **T6** Auth store boot/logout + wiring
- [ ] **T7** Shell/nav tokens maquette ; **pas** Rapports (**D-MOCKGAP**)

### M3 — Dossier cœur
- [ ] **T8** API clients dossiers/types/workflows/viz
- [ ] **T9** CreateDossier + DynamicFields + SensitiveValue (**D-SENSITIVE**)
- [ ] **T10** DossierDetailView + store (load id/ref)
- [ ] **T11** BpmnViewer + history/tasks (409 sans PI)
- [ ] **T12** Start workflow + complete + reassign UI (**D-ACTIONS**)

### M4 — Compléments vague 1
- [ ] **T13** ElementsList + download ; Ajouter disabled (**D-ELEMENTS**)
- [ ] **T14** Notifications SSE + mark-read local
- [ ] **T15** Supervision MVP (ref/id + lien créer ; sans faux KPI)
- [ ] **T16** MobileTasks filtrées par `auth.id` (**A-MOBILE**)

### M5 — Packaging vague 1
- [ ] **T17** `frontend-maven-plugin` + gitignore bundles + fallback `index.html` (**D-PACK**, **A-HISTORY**)
- [ ] **T18** Playwright E2E-1..7 (session mock **D-E2E**) + coverage Vitest
- [ ] **T19** `./mvnw verify` evidence + docs sync vague 1

### M6 — Admin types
- [ ] **T20** UI CRUD dossier-types
- [ ] **T21** UI ACL type

### M7 — Admin reste (**D-ADMIN-RES**)
- [ ] **T22** UI document-templates (activate/archive/new-version)
- [ ] **T23** UI connectors + definitions (secrets redact)
- [ ] **T24** UI workflow-type-configs + observers

### M8 — Clôture
- [ ] **T25** E2E-8 + tests admin Vitest
- [ ] **T26** Evidence finale + DEFERRED/lint + Phase 7/8/9 docs

## Dependencies

```text
Vague 1: T1→T2→T3 ; T2→T4→T6→T7 ; T5∥T4 ; T4→T8→T9→T10→T11→T12
         T10→T13 ; T7→T14 ; T10→T15 ; T12→T16 ; T1…T16→T17→T18→T19
Vague 2: T19→T20→T21 ; T19→T22 ; T19→T23 ; T19→T24 ; T20…T24→T25→T26
```

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Scope admin | Ne pas démarrer M6 avant T19 vert |
| Node/Maven | Version Node figée dans plugin |
| TODO §24 liste | Supervision MVP uniquement |
| Flaky OAuth E2E | Mock session only |

## Resources

Skills vue / pinia / vite / vitest / playwright / flowforge-conventions /
tdd / verify. Maquette HTML + analysis 11.

## Next

Phase 2 — Review Requirements (`dev-requirements`).
