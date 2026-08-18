---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: in-progress
created: 2026-08-18
updated: 2026-08-18
feature: edition-champs-dossier
sequencing: layered
phase4: 2026-08-18
design_aligned: 2026-08-18
---

# Project Planning & Task Breakdown — `edition-champs-dossier`

Plan initial Phase 4, calé sur requirements `reviewed` et design
`reviewed`. Aucune migration Liquibase. Aucun Java.

Task tracing : indisponible (`npx ai-devkit@latest task list --json` →
`unknown command 'task'`).

## Milestones

- [ ] **M1 — Diff + API + store**
- [ ] **M2 — UI éditeur + vue**
- [ ] **M3 — Playwright + docs DEFERRED**

## Task Breakdown

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Diff + API + store

- [ ] **T1. `diffDossierFields` (TDD)**
  - **Outcome :** `frontend/src/dossier/diffDossierFields.ts`.
  - **Depends on :** —
  - **Validation :** `diffDossierFields.spec.ts` (scénarios testing).
  - **Tests liés :** testing § `diffDossierFields`.

- [ ] **T2. Client `updateDossierFields` (TDD)**
  - **Outcome :** `dossiers.ts` + spec axios.patch.
  - **Depends on :** —
  - **Validation :** `dossiers.spec.ts`.

- [ ] **T3. `patchFields` store (TDD)**
  - **Outcome :** action Pinia, `expectedVersion` = `dossier.version`.
  - **Depends on :** T2
  - **Validation :** `dossier.spec.ts`.

### M2 — UI

- [ ] **T4. `DynamicFields.applyDefaults` (TDD)**
  - **Outcome :** prop optionnelle, défaut `true`.
  - **Depends on :** —
  - **Validation :** `DynamicFields.spec.ts`.

- [ ] **T5. `DossierFieldsEditor` (TDD)**
  - **Outcome :** SFC + spec (viewer, lock, emit diff).
  - **Depends on :** T1, T4
  - **Validation :** `DossierFieldsEditor.spec.ts`.

- [ ] **T6. Brancher `DossierDetailView`**
  - **Outcome :** compose l'éditeur ; `patchFields` ; erreur ProblemDetail.
  - **Depends on :** T3, T5
  - **Validation :** `DossierDetailView.spec.ts` étendu.

### M3 — E2E & docs

- [ ] **T7. Playwright E2E-22 / E2E-23**
  - **Outcome :** mock `/fields` + `fieldDefinitions` CLAIM.
  - **Depends on :** T6
  - **Validation :** `npm run test:e2e`.

- [ ] **T8. Docs + DEFERRED-FEATURES**
  - **Outcome :** success criteria cochés ; §1.4 UI PATCH livrée.
  - **Depends on :** T7
  - **Validation :** `npx ai-devkit@latest lint --feature edition-champs-dossier`.

## Dependencies

T1 ∥ T2 ∥ T4 → T3 → T5 → T6 → T7 → T8.

## Timeline & Estimates

Effort technique : un écran, un utilitaire, un client HTTP. Risque
principal = écrasement des champs redactés (couvert T1 + T4).

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| `applyDefaults` écrase un sensitive redacté | T4 + T1 clé absente |
| Bouton confondu avec « Enregistrer statut » | Libellé distinct ; E2E-3 non régressé |
| PATCH `{}` → 400 | D-EMPTY-UI : pas d'appel / pas d'emit |

## Resources Needed

- Patterns `patchStatus` / `DossierDetailView.spec.ts` / `pilotage.spec.ts`.
- Contrat `patch-dossier-fields` (D-MERGE, D-CLEAR, D-STATUS-GATE).
