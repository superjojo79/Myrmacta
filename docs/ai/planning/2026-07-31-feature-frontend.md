---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-12
updated: 2026-08-12
feature: frontend
design_aligned: pending-review
sequencing: layered
---

# Project Planning & Task Breakdown

Plan **phase 1 draft** — dérivé des docs `requirements` / `design` /
`testing` (draft). **Aucune tâche d’implémentation démarrée.**

Traçage `task` CLI : **indisponible** (`npx ai-devkit@latest task list
--json` → `unknown command 'task'`).

Branche agent : `cursor/feature-frontend-21f2` (feature lifecycle
`frontend`).

## Milestones
**What are the major checkpoints?**

- [ ] **M1 — Scaffold & qualité** : Vite vue-ts, deps, ESLint/Prettier,
      Vitest, Playwright, scripts CI
- [ ] **M2 — Auth BFF & shell** : Axios CSRF, auth store, layout maquette,
      router
- [ ] **M3 — Dossier & visualisation** : API clients, détail, bpmn-js,
      tasks complete/reassign
- [ ] **M4 — Notifications & Supervision MVP** : SSE drawer ; ouverture
      ref/id
- [ ] **M5 — Packaging & preuves** : copie `static/`, verify Boot,
      coverage, sync docs

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Scaffold & qualité

- [ ] **T1. Init Vite `frontend/`**
  - **Outcome :** `npm create vite` vue-ts ; `strict` ; structure
    `src/{app,api,stores,views,components,styles}` ; `base` relative.
  - **Deps :** aucune.
  - **Validation :** `npm run build` produit `dist/`.
  - **Tests :** smoke build.

- [ ] **T2. Dépendances UI & outils**
  - **Outcome :** primevue + themes + primeicons ; pinia ; axios ;
    vue-router ; bpmn-js ; vitest + VTU + jsdom ; playwright ; eslint
    vue-ts ; prettier ; vue-tsc.
  - **Deps :** T1.
  - **Validation :** `npm ls` sans peer errors bloquants.
  - **Tests :** config vitest/playwright présentes.

- [ ] **T3. Gates scripts**
  - **Outcome :** `typecheck`, `lint`, `test`, `test:e2e`, `build` ;
    doc README frontend courte.
  - **Deps :** T2.
  - **Validation :** commandes dry-run OK sur skeleton.
  - **Tests :** placeholder spec vert.

### M2 — Auth BFF & shell

- [ ] **T4. Axios + CSRF + ProblemDetail**
  - **Outcome :** instance Axios defaults ; helper erreur typée.
  - **Deps :** T2.
  - **Validation :** unit defaults.
  - **Tests :** Axios / CSRF helpers.

- [ ] **T5. Auth store + boot**
  - **Outcome :** `GET /api/auth/me` ; redirect OAuth ; logout ;
    wiring `main.ts`.
  - **Deps :** T4.
  - **Validation :** Auth store Vitest.
  - **Tests :** Auth store.

- [ ] **T6. Shell layout + tokens**
  - **Outcome :** nav Pilotage (Supervision, Dossier via route,
    Tâches) ; CSS variables maquette ; thème PrimeVue ; header user.
  - **Deps :** T5.
  - **Validation :** snapshot/composant shell.
  - **Tests :** shell rendu basique.

### M3 — Dossier & visualisation

- [ ] **T7. API clients dossiers / workflow**
  - **Outcome :** modules TS typés pour endpoints V1.
  - **Deps :** T4.
  - **Validation :** unit mapping.
  - **Tests :** client + fixtures.

- [ ] **T8. DossierDetailView + store**
  - **Outcome :** charge id/ref ; champs ; éléments ; liste links ;
    navigation.
  - **Deps :** T6, T7.
  - **Validation :** Vitest store + vue ; Playwright E2E-2.
  - **Tests :** Dossier store ; E2E-2.

- [ ] **T9. BpmnViewer + history/tasks panels**
  - **Outcome :** importXML + overlays ; timeline history ; task list ;
    gestion 409.
  - **Deps :** T8.
  - **Validation :** Vitest BpmnViewer ; E2E-3.
  - **Tests :** BpmnViewer ; E2E-3.

- [ ] **T10. Complete + reassign actions**
  - **Outcome :** POST complete ; reassign admin-only UI ; refresh.
  - **Deps :** T9.
  - **Validation :** Vitest actions ; E2E-4/5.
  - **Tests :** complete/reassign ; E2E-4/5.

### M4 — Notifications & Supervision MVP

- [ ] **T11. Notifications SSE**
  - **Outcome :** EventSource store + drawer ; fail-soft.
  - **Deps :** T6.
  - **Validation :** Vitest ; E2E-6.
  - **Tests :** notification store ; E2E-6.

- [ ] **T12. SupervisionView MVP**
  - **Outcome :** formulaire ref/id → resolve → route dossier ;
    placeholders stats non branchés (libellé « bientôt »).
  - **Deps :** T8.
  - **Validation :** Vitest Supervision ; E2E navigation.
  - **Tests :** SupervisionView.

- [ ] **T13. MobileTasksView**
  - **Outcome :** layout responsive ; actions complete ancrées ;
    données via store (A-TASKS).
  - **Deps :** T10.
  - **Validation :** composant + viewport test manuel checklist.
  - **Tests :** tasks view unitaire.

### M5 — Packaging & preuves

- [ ] **T14. Packaging Maven/CI → `static/`**
  - **Outcome :** build FE intégré verify ou étape documentée ;
    `index.html` servi ; fallback history si requis (A-HISTORY).
  - **Deps :** T1–T13.
  - **Validation :** `./mvnw verify` + présence artefacts.
  - **Tests :** smoke resource static (si IT ajoutée).

- [ ] **T15. Evidence & docs sync**
  - **Outcome :** cocher scénarios testing ; implementation notes ;
    DEFERRED à jour ; lint feature.
  - **Deps :** T14.
  - **Validation :** `npx ai-devkit@latest lint --feature frontend`.
  - **Tests :** N/A docs.

## Dependencies
**What needs to happen in what order?**

```text
T1 → T2 → T3
T2 → T4 → T5 → T6
T4 → T7 → T8 → T9 → T10
T6 → T11
T8 → T12
T10 → T13
T1…T13 → T14 → T15
```

Bloquants externes : TODO §24 (liste dossiers) — **non bloquant** V1
grâce à Supervision MVP. Q6 (`sub` dans `/me`) — mitigation A-TASKS.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Context-path casse assets | `base` Vite + chemins relatifs |
| bpmn-js lourd | lazy import sur route dossier |
| Flakiness Playwright OAuth | stubs session |
| Divergence maquette / PrimeVue | tokens + overrides ; revue visuelle Phase 8/9 |

## Resources Needed

- Node LTS compatible Vite ; Java/Maven existants.
- Skills : `vue`, `vue-best-practices`, `pinia`, `vite`, `vitest`,
  `vue-testing-best-practices`, `playwright-best-practices`,
  `flowforge-conventions`, `tdd`, `verify`.
- Maquette HTML + analysis 11.

## Next phase after planning ready

Après Phase 2 (review requirements) + Phase 3 (design review) +
Phase 4 (planning `ready`) → Phase 5 Execute Plan (`dev-implementation`)
en TDD.
