---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
created: 2026-08-18
updated: 2026-08-18
feature: edition-champs-dossier
phase8: 2026-08-18
phase9: 2026-08-18
---

# Testing Strategy — `edition-champs-dossier`

## Test Coverage Goals

- Backend : **aucun code Java** — pas de nouveau Jacoco ciblé.
- Frontend unit : Vitest (`cd frontend && npm run test`).
- Frontend E2E : Playwright (`cd frontend && npm run test:e2e`).

## Unit Tests

### `diffDossierFields`

- [x] Champ modifié → présent dans le patch, les autres omis.
- [x] Aucun changement → `{}`.
- [x] Optionnel vidé (`''`) alors qu'il était dans le snapshot → `null`.
- [x] Clé absente du snapshot et draft blank → omise (sensitive redacté).
- [x] Clé absente du snapshot et draft renseigné → envoyée.
- [x] `BOOLEAN` `false` n'est pas traité comme blank.
- [x] `BOOLEAN` inchangé (`true`/`true`) → omis.
- [x] Input NUMBER string → nombre dans le patch.

### `updateDossierFields` (api)

- [x] `axios.patch('/api/dossiers/{id}/fields', { fields, expectedVersion })`.

### `useDossierStore.patchFields`

- [x] Envoie `dossier.id`, patch, `dossier.version`.
- [x] Remplace `dossier` par la réponse (version avancée).

### `DynamicFields`

- [x] `applyDefaults=false` n'émet pas les `defaultValue` sur clés absentes.
- [x] Défaut actuel (`true`) inchangé (création).

### `DossierFieldsEditor`

- [x] Viewer / `canEdit=false` : pas de bouton, champs readonly.
- [x] `locked=true` : pas de bouton, readonly.
- [x] Éditeur : change un champ, save émet uniquement le diff.
- [x] Diff vide : save n'émet pas.

### `DossierDetailView`

- [x] Manager voit « Enregistrer les champs » ; viewer non.
- [x] Erreur PATCH affichée (ProblemDetail), comme le statut.

## Integration Tests

Aucun MockMvc nouveau (API déjà couverte par `patch-dossier-fields`).

## End-to-End Tests

- [x] **E2E-22** : manager ouvre un dossier, modifie `priority`, clique
      « Enregistrer les champs » ; PATCH `/fields` =
      `{ fields: { priority: 'HIGH' }, expectedVersion: 0 }`.
- [x] **E2E-23** : viewer n'a pas le bouton « Enregistrer les champs ».
- [x] Régression : E2E-3 « Enregistrer statut » distinct ; suite 24 tests.

Mocks : `fieldDefinitions` `priority` STRING sur CLAIM ; handler PATCH
`/fields`.

## Test Data

- Dossier mock `fields: { priority: 'NORMAL' }`.
- Type CLAIM : `FieldDefinition` `priority` STRING required.

## Test Reporting & Coverage

**Phase 8 (2026-08-18, cette session) :**

```text
cd frontend && npm run test
# Test Files  36 passed (36)
# Tests  111 passed (111)
# exit 0

cd frontend && npm run build && npm run test:e2e
# 24 passed (5.3s) including E2E-22 and E2E-23
# exit 0

./mvnw verify -Dmaven.compiler.release=21
# tests=762 failures=0 errors=0 skipped=0  exit 0
# (aucun Java modifié ; non-régression)
```

- Vitest 111 = 94 (preview) + specs édition champs.
- Playwright 24 = 22 + E2E-22 + E2E-23.

## Manual Testing

Hors CI : Keycloak réel, dossier avec champ `sensitive` en tant que
`DOSSIER_MANAGER` (clé absente au GET, non renvoyée au save).

## Performance Testing

N/A — un PATCH, même coût que le statut.

## Bug Tracking

Suivi standard `TODO.md` / `DEFERRED-FEATURES.md`.
