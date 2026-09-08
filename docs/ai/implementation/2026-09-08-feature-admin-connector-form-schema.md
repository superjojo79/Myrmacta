---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

## Development Setup

Workspace : branche `cursor/admin-connector-form-schema-52bc` (no-worktree, convention
cloud agent). Task tracing indisponible (`ai-devkit task` → `unknown command 'task'`).
Frontend : `npm ci` dans la session.

## Code Structure

```
frontend/src/api/admin.ts
frontend/src/api/http.ts
frontend/src/components/connectors/connectorConfig.ts
frontend/src/components/connectors/ConnectorConfigForm.vue
frontend/src/components/connectors/ConnectorIngressUrl.vue
frontend/src/views/admin/ConnectorsAdminView.vue
frontend/e2e/pilotage.spec.ts
```

Aucun fichier Java. PR [#117](https://github.com/superjojo79/FlowForge/pull/117).

## Implementation Notes

### M1–M5 (TDD)

- T1.2 rouge : import `./connectorConfig` manquant.
- T2.1 rouge : `ConnectorConfigForm.vue` manquant.
- T3.1 rouge : 5/5 specs vue (textarea encore présent).
- `adminActions` create : mock sans `configSchema` → schéma `[]` → `config: {}` (pas de
  casse). Cas http-rest dans `ConnectorsAdminView.spec.ts`.

### Patterns

View thin ; formulaire extrait ; utils purs. `defineModel`. `watch(connectorType)` reset.
Fallback textarea si `definitions.length === 0`.

### Design deviations

Aucune sur le formulaire. Addendum stakeholder 2026-09-08 : **D-WEBHOOK-URL = C** (URL webhook
+ callback dans la liste) — hors brief initial, acté en rejeu Phase 1–3. `mapping` reste STRING
(D-DESIGN-NO-JSON-EDITOR).

## Phase 7 — check implementation

Revue fichier par fichier vs design :

| Fichier | Alignement | Notes |
|---|---|---|
| `admin.ts` | OK | Types OpenAPI `ConfigField` / `ConnectorDefinition` |
| `connectorConfig.ts` | OK | D-COERCE, D-EMPTY-SCHEMA, omit blank, `ingressUrlFor` |
| `ConnectorConfigForm.vue` | OK | 6 types, password, helpText, pas de `v-html` |
| `ConnectorIngressUrl.vue` | OK | D-WEBHOOK-URL = C, Copier sans secret |
| `ConnectorsAdminView.vue` | OK | D-SWITCH-TYPE, D-FALLBACK-NO-DEFS, create-only, URL liste |
| e2e mocks | OK après Phase 8 | GET+POST même route ; CONN-3 callback |
| GUIDE §4 / `06-connecteurs.md` | OK | Catalogue 6 types, plus de JSON brut ; URL affichée |

Callers : `listConnectorDefinitions` uniquement vue admin + `phase8Api.spec`. Toggle/delete
inchangés. Pas de secret logué.

## Phase 8

`npm run test` 324/324 ; Playwright 68/68. Coverage agrégat au-dessus des seuils ;
`components/connectors` lines 98.55 %.

## Phase 9

Revue holistique delta (formulaire + **D-WEBHOOK-URL = C**, 2026-09-08), après rebase
sur `main` (A1 [PR #119](https://github.com/superjojo79/FlowForge/pull/119)).

Checklist :

- [x] Alignement design : D-* du formulaire + D-WEBHOOK-URL = C + `apiContextPath()`
- [x] Pas de trou de logique (schéma vide, fallback catalogue, types sans ingress)
- [x] Sécurité : PASSWORD masqué, pas de `v-html`, copie d’URL publique seulement
- [x] Intégration : `GET /api/connector-definitions` / `POST /api/connectors` inchangés
- [x] Tests : Vitest + Playwright `E2E-CONN-*` (preuves Phase 8)
- [x] Docs : GUIDE §4, `06-connecteurs.md`, CHANGELOG PR #117
- [x] Zéro `.java`, Liquibase, OpenAPI, npm dep
- [x] Rollback = revert FE
- [x] Scan : pas de `.only` / secret / debug

Callers de `ingressUrlFor` / `apiContextPath` : vue admin + specs. `DynamicFields.vue`
non réutilisé. Écart produit corrigé : préfixe `/admin` (Vite `base: './'`).

Findings : aucun bloquant. Nice-to-have hors scope : édition de config d’instance,
éditeur JSON `mapping`.

Prêt pour merge une fois la CI GitHub verte.

## Error Handling

Fallback `JSON.parse` + API → `toProblemDetail`. Validation métier serveur.

## Security Notes

PASSWORD `autocomplete=new-password`. Liste : `secretConfigured` seulement.
URL ingress : préfixe tenant = `axios.defaults.baseURL` au boot, jamais le segment Vue
`/admin` (écart E2E D-WEBHOOK-URL).
