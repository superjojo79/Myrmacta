---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [ ] M1 : Types API + utilitaires purs `connectorConfig.ts` (TDD)
- [ ] M2 : `ConnectorConfigForm.vue` (TDD)
- [ ] M3 : Intégration `ConnectorsAdminView.vue` (TDD, dépend M1–M2)
- [ ] M4 : Playwright + mocks définitions (dépend M3)
- [ ] M5 : `GUIDE-UTILISATEUR.md` §4 + `06-connecteurs.md` écran d’admin
- [ ] M6 : Vérification globale (Phases 7–9)

## Task Breakdown

### M1 — Contrat API client + coercition (indépendant)

- [ ] T1.1 : Exporter `ConfigField` / `ConnectorDefinition` depuis `frontend/src/api/admin.ts`
  (ou `types.ts` + re-export). `listConnectorDefinitions(): Promise<ConnectorDefinition[]>`.
  Validation : T-API-1 (`phase8Api.spec.ts` inchangé fonctionnellement).
- [ ] T1.2 : `frontend/src/components/connectors/connectorConfig.ts` + spec. Fonctions
  `defaultsFromSchema`, `configFromFormValues`, `schemaOf`. Rouge d’abord. Validation :
  T-UTIL-1…6.

### M2 — Composant formulaire (dépend T1.2 pour les types, pas pour le runtime)

- [ ] T2.1 : `ConnectorConfigForm.vue` + `ConnectorConfigForm.spec.ts`. `defineModel` values,
  widgets par type, `helpText`, `required`. Validation : T-FORM-1…5.

### M3 — Vue admin (dépend M1–M2)

- [ ] T3.1 : `ConnectorsAdminView.vue` — `configValues`, `activeSchema`, watch type, submit
  typé, empty schema, fallback textarea. Adapter mocks `adminActions.spec.ts` /
  `adminViews.spec.ts` (définitions **avec** `configSchema`). Validation : T-VIEW-1…6.
  **Régression :** le test actuel `create` soumet sans remplir le textarea (`{}`) — il devra
  remplir les champs requis du schéma mocké, ou mocker un schéma vide pour ce cas + un cas
  dédié http-rest.

### M4 — E2E (dépend M3)

- [ ] T4.1 : Enrichir `page.route('**/api/connector-definitions*')` dans `pilotage.spec.ts`
  avec http-rest + smtp (pas `[]`).
- [ ] T4.2 : `E2E-CONN-1` création http-rest.
- [ ] T4.3 : `E2E-CONN-2` création smtp / `config {}`.

### M5 — Documentation utilisateur (dépend M3 pour coller à l’UI réelle)

- [ ] T5.1 : `GUIDE-UTILISATEUR.md` §4 — formulaire champ par champ ; tableau des 6 types
  (`http-rest` BOTH, `flowable-signal`, `scheduler`, `flowable-callback`, `smtp`,
  `document-storage`) ; retirer « zone de texte JSON brut » et la note périmée « http-rest
  SERVICE n’est pas enregistré » (faux depuis PR #95).
- [ ] T5.2 : `analysis doc/06-connecteurs.md` § « Écran d'admin » — V1 livré = formulaire
  généré. Impacte FILE-ATTENTE *Récemment livré* / TODO §59 en clôture.

### M6 — Qualité

- [ ] T6.1 : `cd frontend && npm run test` + `test:coverage` + `test:e2e` ; typecheck/lint.
- [ ] T6.2 : Phase 7 fichier par fichier ; Phase 9 ; `CHANGELOG.md` ; PR.

## Dependencies

```
T1.1 ─┬─ T1.2 ─ T2.1 ─ T3.1 ─┬─ T4.1 ─ T4.2
      │                      │         T4.3
      │                      └─ T5.1 ─ T5.2
      └──────────────────────── T6.*
```

Pas de dépendance A1/A3. Interdit : Java, MCP, Compose, `SecurityConfig`.

## Timeline & Estimates

Sans calendrier (agent). Séquence unique, TDD par tâche.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Test `adminActions` « create » casse (textarea `{}`) | T3.1 : mock schéma vide **ou** remplir les champs |
| Mock E2E `connector-definitions []` casse un test qui cliquait Connecteurs | Grep avant T4.1 ; aucun test actuel ne visite l’écran (vérifié 2026-09-08) |
| `mapping` JSON saisi comme STRING | Accepté (D-DESIGN-NO-JSON-EDITOR) |
| Conflit git `GUIDE-UTILISATEUR.md` avec A1 | A1 surface = GETTING-STARTED / DEPLOIEMENT ; A2 = §4 connecteurs. Fusionner si collision |

## Resources Needed

- Skills : vue-best-practices, vue-testing, playwright-best-practices, tdd, verify.
- Task tracing : **indisponible** (`npx ai-devkit@latest task` → `unknown command 'task'`).
- Mode workspace : no-worktree, branche `cursor/admin-connector-form-schema-52bc` (convention
  cloud agent, pas `feature-*`).

## Traçabilité testing → tasks

| Scénario | Tâche |
|---|---|
| T-API-1 | T1.1 |
| T-UTIL-1…6 | T1.2 |
| T-FORM-1…5 | T2.1 |
| T-VIEW-1…6 | T3.1 |
| E2E-CONN-1 | T4.2 |
| E2E-CONN-2 | T4.3 |
| GUIDE | T5.1 |

Aucun scénario orphelin.
