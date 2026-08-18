---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: in-progress
created: 2026-08-18
updated: 2026-08-18
feature: edition-champs-dossier
---

# Implementation Guide — `edition-champs-dossier`

## Development Setup

Stack FE existante : Vue 3 `<script setup lang="ts">`, Pinia, Vitest,
Playwright. Worktree non utilisé (Cloud Agent, branche
`cursor/edition-champs-dossier-1489`).

## Code Structure

Fichiers prévus :

- `frontend/src/dossier/diffDossierFields.ts` (+ spec)
- `frontend/src/components/dossier/DossierFieldsEditor.vue` (+ spec)
- `frontend/src/components/dossier/DynamicFields.vue` — `applyDefaults`
- `frontend/src/api/dossiers.ts` / `dossiers.spec.ts`
- `frontend/src/stores/dossier.ts` / `dossier.spec.ts`
- `frontend/src/views/pilotage/DossierDetailView.vue` / spec
- `frontend/e2e/pilotage.spec.ts`

## Implementation Notes

### Core Features

- **D-DIFF :** comparer snapshot GET et draft local. Blank → `null` si la
  clé existait. Clé never-present + blank → omettre.
- **D-DEFAULTS :** `applyDefaults=false` sur l'éditeur (pas sur la
  création).
- **D-LOCK / D-AUTHZ :** `readonly = !canEdit || locked` ;
  bouton seulement si `canEdit && !locked`.
- **D-EMPTY-UI :** `DossierFieldsEditor` n'émet `save` que si le patch
  n'est pas vide.

### Patterns

- Même flux d'erreur que `saveStatus` (`toProblemDetail`).
- `storeToRefs` pour l'état Pinia.
- Vue : Composition API, props down / emit up.

## Integration Points

`PATCH /api/dossiers/{id}/fields` déjà en production. CSRF cookie Axios
global inchangé.

## Error Handling

400 / 403 / 409 → texte `detail` sous l'éditeur (ou `actionError` de la
vue, comme le statut).

## Performance Considerations

Un PATCH, pas de debounce (save explicite).

## Security Notes

- Ne pas envoyer les clés redactées non touchées.
- Le GET a déjà retiré les valeurs `sensitive` pour non-TA.
- Inputs d'édition : un manager **peut** poser une nouvelle valeur
  sensitive (D-SENSITIVE-AUDIT backend) ; il ne relit pas l'ancienne.
