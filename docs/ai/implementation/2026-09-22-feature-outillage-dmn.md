---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: done
created: 2026-09-22
updated: 2026-09-23
feature: outillage-dmn
phase5: 2026-09-23
---

# Implementation Guide — `outillage-dmn`

Phase 5, 2026-09-23. Les quatre use cases vivent dans le domaine et prennent
un `AccessContext.User`. `TenantAdminGuard.requireTenantOrWorkflowAdminOrApiService`
est appelé en premier. `DeployDecisionTableUseCaseImpl` n'est pas modifié.

## Évaluation

`EvaluateDecisionUseCaseImpl` résout la version omise via
`DmnDeploymentPort.findDeploymentInfo`. Clé inconnue :
`UnknownDecisionKeyException` (404). Version explicite absente :
`UnknownDecisionVersionException` levée par `FlowableDmnEvaluationAdapter` (404).

L'adaptateur charge `DmnDecision` (`decisionKey`, `decisionVersion`, tenant)
puis `ExecuteDecisionBuilder.decisionKey().tenantId().parentDeploymentId(deploymentId).disableHistory().variables().execute()`.
`disableHistory()` évite d'alimenter l'historique DMN Flowable à chaque essai
ad hoc. Aucune règle : liste vide, HTTP 200. Échec moteur :
`DecisionEvaluationException` (400).

## Jeux de tests

`TestDecisionUseCaseImpl` délègue chaque cas à `EvaluateDecisionUseCase`.
Plafond 100. Comparaison : même nombre de lignes, dans l'ordre, clés
attendues seulement. `coerceScalars=true` pour le CSV (littéral `1000` /
`true`) et `false` pour le JSON. Le parseur CSV
(`DmnCsvTestCaseParser`, commons-csv, RFC 4180) est dans l'infrastructure.

## Historique et redéploiement

`findAllVersions` interroge Flowable `orderByDecisionVersion().asc()` et
lit `DmnDeployment.getDeploymentTime()`. Clé inconnue : liste vide → 404.

`RedeployDecisionTableUseCaseImpl` refuse la version déjà courante
(`DecisionVersionAlreadyCurrentException`, 409) avant de relire le XML.
Sinon : `findDeployedXml(version)`, `DmnParserPort.extractDecisionKey`,
puis `DmnDeploymentPort.deploy`. Le port `deploy` existant n'efface pas
le brouillon (seul le use case de déploiement le fait).

## Surfaces

REST : `DmnDecisionToolingController`, même préfixe et mêmes rôles que
le déploiement. MCP : `evaluate_decision`, `test_decision`,
`list_decision_table_versions`, `redeploy_decision_table`. Catalogue 27.

UI : panneaux ouverts explicitement. Aucun appel
`/versions`, `/evaluate`, `/test` ou `/redeploy` au montage, parce que
le mock Playwright `**/api/dmn-decision-deployments` intercepte les
sous-chemins du déploiement existant.
