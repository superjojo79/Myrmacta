---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: done
created: 2026-08-18
updated: 2026-08-18
feature: edition-champs-dossier
sequencing: layered
phase4: 2026-08-18
phase5: 2026-08-18
phase6: 2026-08-18
phase7: 2026-08-18
phase8: 2026-08-18
phase9: 2026-08-18
design_aligned: 2026-08-18
---

# Project Planning & Task Breakdown — `edition-champs-dossier`

Plan initial Phase 4, calé sur requirements `reviewed` et design
`reviewed`. Aucune migration Liquibase. Aucun Java.

Task tracing : indisponible (`npx ai-devkit@latest task list --json` →
`unknown command 'task'`).

## Milestones

- [x] **M1 — Diff + API + store**
- [x] **M2 — UI éditeur + vue**
- [x] **M3 — Playwright + docs DEFERRED**

## Task Breakdown

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Diff + API + store

- [x] **T1. `diffDossierFields` (TDD)**
  - **Outcome :** `frontend/src/dossier/diffDossierFields.ts`.
  - **Depends on :** —
  - **Validation :** `diffDossierFields.spec.ts` (8 cas, exit 0).
  - **Tests liés :** testing § `diffDossierFields`.

- [x] **T2. Client `updateDossierFields` (TDD)**
  - **Outcome :** `dossiers.ts` + spec axios.patch.
  - **Depends on :** —
  - **Validation :** `dossiers.spec.ts`.

- [x] **T3. `patchFields` store (TDD)**
  - **Outcome :** action Pinia, `expectedVersion` = `dossier.version`.
  - **Depends on :** T2
  - **Validation :** `dossier.spec.ts`.

### M2 — UI

- [x] **T4. `DynamicFields.applyDefaults` (TDD)**
  - **Outcome :** prop optionnelle, défaut `true` via `withDefaults`
    (prop booléenne Vue = `false` si omise sans défaut).
  - **Depends on :** —
  - **Validation :** `DynamicFields.spec.ts`.

- [x] **T5. `DossierFieldsEditor` (TDD)**
  - **Outcome :** SFC + spec (viewer, lock, emit diff).
  - **Depends on :** T1, T4
  - **Validation :** `DossierFieldsEditor.spec.ts`.

- [x] **T6. Brancher `DossierDetailView`**
  - **Outcome :** compose l'éditeur ; `patchFields` ; erreur ProblemDetail.
  - **Depends on :** T3, T5
  - **Validation :** `DossierDetailView.spec.ts` étendu.

### M3 — E2E & docs

- [x] **T7. Playwright E2E-22 / E2E-23**
  - **Outcome :** mock `/fields` + `fieldDefinitions` CLAIM.
  - **Depends on :** T6
  - **Validation :** `npm run test:e2e` — 24 passed.

- [x] **T8. Docs + DEFERRED-FEATURES**
  - **Outcome :** success criteria cochés ; §1.4 UI PATCH livrée.
  - **Depends on :** T7
  - **Validation :** lint feature (worktree MISS Cloud Agent attendu).

## Dependencies

T1 ∥ T2 ∥ T4 → T3 → T5 → T6 → T7 → T8. Tous clos.

## Timeline & Estimates

Effort technique : un écran, un utilitaire, un client HTTP.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| `applyDefaults` écrase un sensitive redacté | T4 + T1 clé absente — livré |
| Bouton confondu avec « Enregistrer statut » | Libellé distinct ; E2E-3 vert |
| PATCH `{}` → 400 | D-EMPTY-UI : pas d'emit |

## Resources Needed

- Patterns `patchStatus` / `DossierDetailView.spec.ts` / `pilotage.spec.ts`.
- Contrat `patch-dossier-fields` (D-MERGE, D-CLEAR, D-STATUS-GATE).

## Summary (Phase 6)

M1–M3 livrés. Preuves Vitest 111 + Playwright 24. Prochaine étape :
Phase 9 revue, PR #53.
