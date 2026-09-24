---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: done
created: 2026-09-22
updated: 2026-09-23
feature: outillage-dmn
phase5: 2026-09-23
phase7: 2026-09-23
phase8: 2026-09-23
phase9: 2026-09-23
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

## Phase 7 — check implementation (2026-09-23)

Revue fichier par fichier contre le design (`status: reviewed`) et les
requirements. Les quatre use cases, le port d’évaluation, le CSV
d’infrastructure, les quatre tools MCP, le contrôleur REST et les
panneaux de `/admin/dmn-editor` correspondent aux décisions D-TECH-*.
`DeployDecisionTableUseCaseImpl` n’est pas modifié. Le redéploiement
appelle `DmnDeploymentPort.deploy` après l’allowlist et n’efface pas le
brouillon.

Écarts corrigés :

- L’échec moteur renvoyait « Decision evaluation failed… » sans la cause.
  Le détail HTTP 400 inclut désormais le message du moteur
  (`FlowableDmnEvaluationAdapter`). Le test était rouge avant le correctif.
- Une cellule CSV vide est un `null`. `Map.copyOf` rejetait cette valeur
  et le parseur répondait « CSV is malformed ». La copie conserve le null.
- L’entrée CHANGELOG de la PR #134 avait absorbé le texte de la PR #132
  (alertes SLA). Les deux entrées sont de nouveau distinctes.

Écarts acceptés :

- `deployedAt` vaut `Instant.EPOCH` si Flowable ne fournit pas
  d’horodatage de déploiement. Le record exige un `Instant`.
- La comparaison des lignes est privée dans `TestDecisionUseCaseImpl`
  (ArchUnit : une classe `..usecase..` concrète se termine par `Impl`).
- `disableHistory()` sur l’évaluation ad hoc, pour ne pas remplir
  l’historique DMN Flowable à chaque essai.

## Durcissement (revue adversariale)

- Le redéploiement refuse le XML dont l’id de décision diffère de la clé
  demandée (`IllegalArgumentException`, HTTP 400), après l’allowlist.
- Le parseur CSV rejette la 101e ligne avant de construire le cas
  (`at most 100 cases are allowed`).
- `test_decision` refuse un payload plus grand que
  `spring.servlet.multipart.max-file-size`, avant le parse JSON ou CSV.
- Une version historique hors allowlist actuelle reste exécutable par
  evaluate/test ; seul le redéploiement la republie, et il la refuse.

## Phase 8 — write tests (2026-09-23)

Couverture relue dans `target/site/jacoco/jacoco.csv` après
`./mvnw verify -Djava.version=21 -Dskip.frontend=true` : **2133** tests,
0 échec, « All coverage checks have been met ».

Branches ajoutées : clé nulle, lignes attendues nulles, littéral `false`
et flottant, CSV sans colonne `input` ou avec un préfixe vide, version
XML absente, payload JSON sans `cases`, message moteur vide ou présent.

Frontend : Vitest **358** (80 fichiers). Playwright **84** passed
(`PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/google/chrome/chrome`).

Durcissement post-revue : `./mvnw verify -Djava.version=21 -Dskip.frontend=true`
→ **2136** tests, 0 échec, Jacoco tenu.

## Phase 9 — review (2026-09-23)

Revue holistique après les correctifs de la Phase 7. Aucun blocage.

- Design : les quatre surfaces (domaine, REST, MCP, éditeur) suivent les
  D-TECH-*. Pas de table Liquibase, pas de variable d’environnement, pas
  d’évaluation de brouillon.
- Sécurité : garde `TenantAdminGuard` avant les ports ; redéploiement
  rejoue l’allowlist ; `DOSSIER_VIEWER` rejeté avant le port.
- Contrat : golden OpenAPI et catalogue de 27 tools inchangés par cette
  revue. Le message 400 d’évaluation inclut la cause moteur.
- Tests : preuves fraîches de cette session (2133, 358, 84).
- Docs : implementation, testing, deployment, monitoring, requirements,
  TODO §66, FILE-ATTENTE, analyse 28, CHANGELOG (entrée #132 restaurée).

**Checklist :** design ✅ · pas de gap logique ouvert ✅ · sécurité ✅ ·
intégrations vérifiées ✅ · tests ✅ · docs ✅. Prêt pour la revue de la
PR #134.
