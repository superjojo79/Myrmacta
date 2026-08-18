---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: in-progress
created: 2026-08-18
updated: 2026-08-18
feature: edition-champs-dossier
---

# Testing Strategy — `edition-champs-dossier`

## Test Coverage Goals

- Backend : **aucun code Java** — pas de nouveau Jacoco ciblé. Ne pas
  déclarer Playwright hors scope.
- Frontend unit : Vitest (`cd frontend && npm run test`).
- Frontend E2E : Playwright (`cd frontend && npm run test:e2e`).

## Unit Tests

### `diffDossierFields`

- [ ] Champ modifié → présent dans le patch, les autres omis.
- [ ] Aucun changement → `{}`.
- [ ] Optionnel vidé (`''`) alors qu'il était dans le snapshot → `null`.
- [ ] Clé absente du snapshot et draft blank → omise (sensitive redacté).
- [ ] Clé absente du snapshot et draft renseigné → envoyée.
- [ ] `BOOLEAN` `false` n'est pas traité comme blank.
- [ ] `BOOLEAN` inchangé (`true`/`true`) → omis.

### `updateDossierFields` (api)

- [ ] `axios.patch('/api/dossiers/{id}/fields', { fields, expectedVersion })`.

### `useDossierStore.patchFields`

- [ ] Envoie `dossier.id`, patch, `dossier.version`.
- [ ] Remplace `dossier` par la réponse (version avancée).

### `DynamicFields`

- [ ] `applyDefaults=false` n'émet pas les `defaultValue` sur clés absentes.
- [ ] Défaut actuel (`true`) inchangé (création).

### `DossierFieldsEditor`

- [ ] Viewer / `canEdit=false` : pas de bouton, champs readonly.
- [ ] `locked=true` : pas de bouton, readonly.
- [ ] Éditeur : change un champ, save émet uniquement le diff.
- [ ] Diff vide : save n'émet pas.

### `DossierDetailView`

- [ ] Manager voit « Enregistrer les champs » ; viewer non.
- [ ] Erreur PATCH affichée (ProblemDetail), comme le statut.

## Integration Tests

Aucun MockMvc nouveau (API déjà couverte par `patch-dossier-fields`).

## End-to-End Tests

- [ ] **E2E-22** : manager ouvre un dossier, modifie `priority`, clique
      « Enregistrer les champs » ; la requête PATCH `/fields` contient
      `{ fields: { priority: 'HIGH' }, expectedVersion: 0 }` (ou version
      du mock) et **pas** d'autres clés.
- [ ] **E2E-23** : viewer n'a pas le bouton « Enregistrer les champs ».
- [ ] Régression : E2E-3 « Enregistrer statut » toujours distinct ;
      E2E-18 viewer sans « Ajouter ».

Mocks : `fieldDefinitions` non vide sur CLAIM (`priority` STRING) ;
handler `PATCH` `**/fields`.

## Test Data

- Dossier mock existant `fields: { priority: 'NORMAL' }`.
- Type CLAIM : une `FieldDefinition` `priority` STRING required.

## Test Reporting & Coverage

```bash
cd frontend && npm run test
cd frontend && npm run test:e2e
```

- Enregistrer commandes + comptes pass avant `verified`.

## Manual Testing

Hors CI : Keycloak réel, dossier avec champ `sensitive` en tant que
`DOSSIER_MANAGER` (clé absente au GET, non renvoyée au save).

## Performance Testing

N/A — un PATCH, même coût que le statut.

## Bug Tracking

Suivi standard `TODO.md` / `DEFERRED-FEATURES.md`.
