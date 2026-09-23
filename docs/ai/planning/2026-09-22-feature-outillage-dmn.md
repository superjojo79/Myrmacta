---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: done
created: 2026-09-22
updated: 2026-09-23
feature: outillage-dmn
phase4: 2026-09-23
phase5: 2026-09-23
---

# Project Planning & Task Breakdown — `outillage-dmn`

Phase 4, 2026-09-23. Chaque scénario du testing doc a une tâche.
Ordre TDD : test rouge, puis code.

## Milestones

- [x] M1 — Évaluation ad hoc (domaine, port, adaptateur, REST, MCP)
- [x] M2 — Jeux de tests JSON et CSV
- [x] M3 — Historique des versions
- [x] M4 — Redéployer la version N
- [x] M5 — UI éditeur (Tester, upload, historique) + Vitest + Playwright
- [x] M6 — Guide, OpenAPI golden, catalogue MCP

M1 avant M2 (le jeu de tests appelle l’évaluation). M3 avant M4.
M5 après les contrats REST. M6 en dernier.

## Task Breakdown

### M1 — Évaluation

- [x] T1.1 Records, exceptions (`UnknownDecisionVersionException`, `DecisionEvaluationException`, `DecisionVersionAlreadyCurrentException`), `DmnEvaluationPort`, extension `DmnDeploymentPort`. Tests : compilation des tests de use case.
- [x] T1.2 `EvaluateDecisionUseCaseImpl` : garde, version par défaut, 404, liste vide, 400 moteur. Tests : T-EVAL-1 à T-EVAL-6, T-API-3.
- [x] T1.3 `FlowableDmnEvaluationAdapter` : query version + `parentDeploymentId` + `disableHistory`. Tests : T-IT-1, T-IT-2, T-IT-6.
- [x] T1.4 `POST /evaluate` et tool `evaluate_decision`. Tests : T-API-1 (evaluate), T-API-2, T-MCP-1.

### M2 — Jeux de tests

- [x] T2.1 `TestDecisionUseCaseImpl` : boucle, ordre, cap 100, coercion, JSON vs chaîne. Tests : T-TEST-1 à T-TEST-4, T-TEST-7 à T-TEST-9.
- [x] T2.2 Parseur CSV infrastructure (`input.` / `expected.`). Tests : T-TEST-5, T-TEST-6.
- [x] T2.3 `POST /test` (json et text/csv) + tool `test_decision`. Tests : T-API-1 (test).

### M3 — Historique

- [x] T3.1 `ListDecisionTableVersionsUseCaseImpl` + `findAllVersions` (ordre croissant, `deployedAt`). Tests : T-HIST-1, T-HIST-2, T-IT-3.
- [x] T3.2 `GET /{key}/versions` + `list_decision_table_versions`. Tests : T-API-1 (versions), T-MCP-2.

### M4 — Redéploiement

- [x] T4.1 `RedeployDecisionTableUseCaseImpl` : XML de N, allowlist, 409 si courante, 404. Tests : T-RB-1 à T-RB-4, T-IT-4, T-IT-5.
- [x] T4.2 `POST /redeploy/{version}` + `redeploy_decision_table`. Tests : T-API-1 (409), T-MCP-2.
- [x] T4.3 Catalogue MCP 23 → 27 (`McpEndpointApiTest`).

### M5 — UI

- [x] T5.1 Client `workflows.ts` + composable. Pas d’appel au montage (les E2E deploy existants routent tout `/api/dmn-decision-deployments`).
- [x] T5.2 Panneaux Tester (JSON), upload, historique avec confirmation. Tests : T-E2E-1 à T-E2E-6, Vitest vue.

### M6 — Docs et contrat

- [x] T6.1 `GUIDE-UTILISATEUR.md` section DMN.
- [x] T6.2 `openapi/openapi.yaml` via `-Dopenapi.update=true`.
- [x] T6.3 `CHANGELOG.md` de la PR.

## Dependencies

- M2 dépend de M1. M4 dépend de M3 et de `DmnParserPort` / `deploy` existants.
- M5 dépend des paths REST figés en Phase 3.
- Pas de Liquibase, pas de variable d’environnement.

## Risks

- `parentDeploymentId` ne cible la version que si `alwaysLookupLatestDefinitionVersion` reste `false` (défaut Flowable, non surchargé dans le dépôt). T-IT-2 est la preuve.
- Le route Playwright `**/api/dmn-decision-deployments` intercepte les sous-chemins. L’UI ne doit pas appeler l’API tant que l’utilisateur n’ouvre pas un panneau.
- Le CSV ne porte qu’une ligne attendue par cas. `COLLECT` multi-lignes = JSON (design).

## Progress

Phase 4 rédigée le 2026-09-23. Phase 5 livrée le même jour : M1 à M6 cochées.
