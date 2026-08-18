---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: completed
created: 2026-08-18
updated: 2026-08-18
feature: edition-champs-dossier
phase7: 2026-08-18
phase9: 2026-08-18
---

# Implementation Guide — `edition-champs-dossier`

## Development Setup

Stack FE existante : Vue 3 `<script setup lang="ts">`, Pinia, Vitest,
Playwright. Worktree non utilisé (Cloud Agent, branche
`cursor/edition-champs-dossier-1489`).

## Code Structure

Fichiers livrés :

- `frontend/src/dossier/diffDossierFields.ts` (+ spec)
- `frontend/src/components/dossier/DossierFieldsEditor.vue` (+ spec)
- `frontend/src/components/dossier/DynamicFields.vue` — `applyDefaults`
- `frontend/src/api/dossiers.ts` / `dossiers.spec.ts`
- `frontend/src/stores/dossier.ts` / `dossier.spec.ts`
- `frontend/src/views/pilotage/DossierDetailView.vue` / spec
- `frontend/e2e/pilotage.spec.ts`

Aucun fichier Java.

## Implementation Notes

### Core Features

- **D-DIFF :** `diffDossierFields(snapshot, draft, definitions)`. Blank →
  `null` si la clé existait. Clé never-present + blank → omettre.
  `BOOLEAN` `false` n'est pas blank. NUMBER : string d'input → `Number`.
- **D-DEFAULTS :** `applyDefaults=false` sur l'éditeur. `withDefaults(...,
  { applyDefaults: true })` obligatoire : une prop booléenne Vue omise
  vaut `false`, ce qui cassait la création.
- **D-LOCK / D-AUTHZ :** `editable = canEdit && !locked`.
- **D-EMPTY-UI :** pas d'`emit('save')` si patch vide.

### Patterns

- Même flux d'erreur que `saveStatus` (`toProblemDetail`).
- Vue : Composition API, props down / emit up.
- Vue route : `DossierDetailView` reste une surface de composition.

## Integration Points

`PATCH /api/dossiers/{id}/fields` déjà en production. CSRF cookie Axios
global inchangé.

## Error Handling

400 / 403 / 409 → `actionError` de la vue (partagé avec le statut).

## Performance Considerations

Un PATCH, save explicite (pas de debounce).

## Security Notes

- Ne pas envoyer les clés redactées non touchées (D-DIFF).
- Le GET a déjà retiré les valeurs `sensitive` pour non-TA.
- Un manager **peut** poser une nouvelle valeur sensitive (autorisé par
  D-SENSITIVE-AUDIT backend).

## Phase 7 — Check Implementation

Lint feature : docs OK ; git branch `feature-edition-champs-dossier` MISS
attendu (Cloud Agent `cursor/edition-champs-dossier-1489`). Task tracing
indisponible.

| Fichier | Intention design | Statut |
|---|---|---|
| `diffDossierFields.ts` | Pure, D-DIFF / D-CLEAR-UI | Conforme |
| `dossiers.ts` `updateDossierFields` | PATCH fields + expectedVersion | Conforme |
| `dossier.ts` `patchFields` | Comme `patchStatus` | Conforme |
| `DynamicFields.vue` | `applyDefaults` défaut true | Conforme + piège Vue booléen corrigé |
| `DossierFieldsEditor.vue` | Section + emit patch non vide | Conforme |
| `DossierDetailView.vue` | Compose l'éditeur, plus de readonly inline | Conforme |
| Liquibase / Java | Absents | Conforme |

Écarts : aucun matériel.

## Phase 9 — Revue de code finale

**Alignement design :** D-SURFACE, D-AUTHZ, D-LOCK, D-DIFF, D-CLEAR-UI,
D-DEFAULTS, D-EMPTY-UI, D-VERSION. Backend **aucun diff**.

**Appelants :** `diffDossierFields` → éditeur + specs. `updateDossierFields`
→ store uniquement. `DossierFieldsEditor` → `DossierDetailView` uniquement.

### Findings

Aucun Critical / High / Medium.

| Sévérité | Fichier | Issue | Reco |
|---|---|---|---|
| Info | `DossierFieldsEditor` | Pas de disable du bouton quand diff vide | UX ; le click no-op évite déjà le 400 |
| Info | Édition `FILE` | Input texte (A2) | Aligné création |

**Sécurité :** CSRF inchangé ; pas de `v-html` ; pas de log des valeurs ;
redaction GET non contournée (clés absentes omises). Rollback = revert
artefact FE.

### Checklist finale

- [x] Correspondance design (D-*)
- [x] Aucun trou de logique bloquant
- [x] Sécurité (diff sensitive, ACL UI, lock statut)
- [x] Intégrations : API existante, création `applyDefaults` intacte
- [x] Tests : Vitest 111 + Playwright 24 + `./mvnw verify` 762
- [x] Docs + `DEFERRED-FEATURES.md`

**Preuves Phase 9 (2026-08-18, cette session) :**

- `cd frontend && npm run test` — **111** passed, exit 0
- `cd frontend && npm run test:e2e` — **24** passed (E2E-22 + E2E-23), exit 0
- `./mvnw verify -Dmaven.compiler.release=21` — **762** tests, Failures 0, exit 0

**Verdict :** READY. PR #53 à marquer prête pour revue humaine.
