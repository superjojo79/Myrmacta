---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

## Development Setup

Workspace : branche `cursor/admin-connector-form-schema-52bc` (no-worktree, convention
cloud agent). Task tracing indisponible (`ai-devkit task` absent). Frontend : `npm ci`
déjà exécuté dans la session.

## Code Structure

```
frontend/src/api/admin.ts                          # ConnectorDefinition / ConfigField
frontend/src/components/connectors/
  connectorConfig.ts                               # defaultsFromSchema, configFromFormValues, schemaOf
  connectorConfig.spec.ts
  ConnectorConfigForm.vue
  ConnectorConfigForm.spec.ts
frontend/src/views/admin/ConnectorsAdminView.vue
frontend/src/views/admin/ConnectorsAdminView.spec.ts
frontend/e2e/pilotage.spec.ts                      # E2E-CONN-1 / E2E-CONN-2 + mocks définitions
```

Aucun fichier Java.

## Implementation Notes

### M1–M3 (TDD, 2026-09-08)

- T1.2 rouge : import `./connectorConfig` manquant (vitest 1 failed suite).
- T2.1 rouge : `ConnectorConfigForm.vue` manquant.
- T3.1 rouge : 5/5 `ConnectorsAdminView.spec.ts` (textarea encore présent).
- Vert : 13 tests composant + 5 vue + régression `adminActions` / `adminViews` / `phase8Api`.

### Patterns

- View thin, formulaire extrait, utils purs (pas un composable — pas d’état partagé).
- `defineModel` sur `ConnectorConfigForm`.
- `watch(connectorType)` reset via `defaultsFromSchema` (D-SWITCH-TYPE).
- Fallback textarea seulement si `definitions.length === 0`.

### Design deviations

Aucune à ce stade.

## Integration Points

`GET /api/connector-definitions` / `POST /api/connectors` inchangés.

## Error Handling

`JSON.parse` du fallback + erreurs API → `toProblemDetail` existant. Validation métier serveur.

## Security Notes

PASSWORD = `type=password` `autocomplete=new-password`. `helpText` en texte, pas `v-html`.
Liste : `secretConfigured` inchangé.

## Phase 7 / 8

À remplir après revue fichier par fichier et preuves Vitest + Playwright fraîches.
