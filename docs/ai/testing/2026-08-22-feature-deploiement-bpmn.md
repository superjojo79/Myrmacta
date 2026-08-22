---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: done
created: 2026-08-22
updated: 2026-08-22
feature: deploiement-bpmn
design_ref: docs/ai/design/2026-08-22-feature-deploiement-bpmn.md
---

# Testing Strategy — `deploiement-bpmn`

## Test Coverage Goals

- Jacoco `BUNDLE` LINE ≥ 85% / BRANCH ≥ 75% (seuils déjà en place,
  `fiabilisation-tests-plateforme`) — doit rester vert après ajout de
  cette feature.
- Chaque nouveau composant backend (use cases, adaptateur, contrôleur)
  couvert par au moins un test direct, pas seulement transitivement.
- Frontend : Vitest pour les nouveaux composants
  (`BpmnCanvas.vue`, `BpmnUploadPreview.vue`) + non-régression sur
  `BpmnViewer.vue` après refactor ; Playwright pour le parcours complet
  upload → prévisualisation → déploiement (feature touche le frontend,
  E2E obligatoire).

## Unit Tests

### `DeployBpmnProcessUseCaseImplTest` (fait)
- [x] BPMN valide + `processDefinitionKey` connu (`WorkflowTypeConfig` existant) → déploiement délégué au port out, retourne la version déployée
- [x] `BpmnParserPort` lève `InvalidBpmnException` → propagée, port out et repository jamais appelés
- [x] `processDefinitionKey` extrait sans `WorkflowTypeConfig` correspondant pour le tenant → `UnknownWorkflowKeyException`, port out jamais appelé (D-BPMN-KEY-VALIDATION)
- Note d'implémentation : le parsing BPMN a été extrait dans un port dédié (`BpmnParserPort`, voir
  ci-dessous) pour respecter la règle ArchUnit domaine-sans-dépendance-framework ; les scénarios XML
  malformé / XML bien formé mais non-BPMN sont donc couverts au niveau de l'adaptateur, pas du use case.

### `FlowableBpmnParserAdapterTest` (nouveau, non prévu au testing doc initial — découvert en implémentation)
- [x] BPMN valide → retourne l'id du process principal
- [x] XML malformé (non bien formé) → `InvalidBpmnException`
- [x] XML bien formé mais pas un BPMN 2.0 valide (namespace incorrect) → `InvalidBpmnException`

### `GetProcessDeploymentInfoUseCaseImplTest` (fait)
- [x] Clé déjà déployée → retourne version actuelle + nombre d'instances actives
- [x] Clé jamais déployée → résultat vide (pas d'erreur)

### `BpmnUploadProperties` (fait)
- [x] Content-type dans l'allowlist → autorisé
- [x] Content-type hors allowlist → refusé
- [x] Défauts appliqués si aucune config fournie (`application/xml`, `text/xml`)

## Integration Tests

### `WorkflowBpmnDeploymentApiTest` (fait)
- [x] `POST /api/workflow-bpmn-deployments` sans rôle `TENANT_ADMIN`/`WORKFLOW_ADMIN` → `403`
- [x] `POST` avec BPMN valide + clé connue → `201`, corps avec `processDefinitionKey`/`deployedVersion`/`deploymentId`
- [x] `POST` avec BPMN invalide → `400`
- [x] `POST` avec clé inconnue → `404` (groupé avec `WorkflowTypeConfigNotFoundException`)
- [x] `POST` sans fichier ou fichier vide → `400`
- [x] `GET /api/workflow-bpmn-deployments/info?processDefinitionKey=...` avec clé déployée → `200` + info
- [x] `GET .../info` avec clé jamais déployée → `204`
- [x] `POST` avec `originalFilename` vide (`""`, cas simulable via `MockMultipartFile`) → ne plante pas, déploie normalement (Phase 7, voir implementation doc Design Deviation 3)

### `FlowableWorkflowDeploymentAdapterTest` (fait, H2/Flowable embarqué)
- [x] Déploiement réel via `RepositoryService` sur un BPMN de test → nouvelle version créée, visible immédiatement sans redémarrage (démarrage d'un processus avec cette clé juste après le déploiement)
- [x] Redéploiement de la même clé → version incrémentée, instances existantes sur l'ancienne version non affectées (comportement natif Flowable, observé pas forcé)
- [x] `latestVersion`/`activeInstanceCount` corrects après un déploiement + démarrage d'instances
- [x] `filename` `null` (cas réel non simulable via `MockMultipartFile`, testé directement sur l'adaptateur) → ne lève pas de `NullPointerException`, déploie avec un nom de ressource par défaut (Phase 7, bug réel trouvé et corrigé)

## Frontend Tests

### `BpmnCanvas.vue` (fait, Vitest)
- [x] Rend le XML fourni via bpmn-js `importXML`
- [x] Applique les marqueurs optionnels (`activeIds`/`completedIds`/`failedIds`) si fournis, aucun marqueur si absents

### `BpmnViewer.vue` (non-régression après refactor, Vitest existant)
- [x] Comportement inchangé : tous les tests Vitest existants restent verts sans modification (contrat public préservé)

### `BpmnUploadPreview.vue` (fait, Vitest)
- [x] Sélection de fichier → lecture locale (`File.text()`), rendu via `BpmnCanvas.vue`, **aucun appel de déploiement** (`POST`) avant confirmation (le `GET .../info` informatif, lui, est attendu après sélection — cf. scénario suivant)
- [x] Après sélection, appel `GET .../info` pour afficher version actuelle/instances actives si la clé existe
- [x] Fichier illisible/non-XML côté client → message d'erreur clair, pas de tentative de déploiement
- [x] Confirmation → `POST` multipart, gestion des erreurs serveur (détail `ProblemDetail` affiché)
- [x] Extraction du `processDefinitionKey` robuste aux guillemets simples XML (`id='...'`), pas seulement doubles (Phase 7, robustesse trouvée et corrigée)

### `WorkflowConfigsAdminView.vue` (fait, Vitest, section ajoutée)
- [x] Nouvelle section BPMN visible pour les rôles `TENANT_ADMIN`/`WORKFLOW_ADMIN`

## End-to-End Tests (Playwright — obligatoire, feature touche le frontend)

- [x] Parcours complet : navigation vers `/admin/workflow-types` → sélection fichier BPMN valide → prévisualisation affichée → confirmation → déploiement réussi affiché (`E2E-BPMN-1`)
- [x] Fichier BPMN invalide → erreur affichée, pas de déploiement (`E2E-BPMN-2`)
- [x] Redéploiement d'une clé existante → info version/instances actives affichée avant confirmation (`E2E-BPMN-3`)
- [x] Mocks/assertions `frontend/e2e/**` mis à jour pour les nouvelles routes API (`GET .../info`, `POST /api/workflow-bpmn-deployments`)
- [x] `cd frontend && PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium npx playwright test` exécuté — **43 passed (26.0s)**, exit code 0, 0 régression sur les 40 scénarios existants, 3 nouveaux (`E2E-BPMN-1/2/3`) verts. Preuve fraîche : 2026-08-22, cette session.

## Test Data

- Fixtures BPMN de test : un `.bpmn20.xml` valide minimal (process id
  correspondant à un `workflowKey` de test), un XML malformé, un XML
  bien formé mais non-BPMN (ex. un autre schéma XML).
- Pas de nouvelle donnée de seed en base — les `WorkflowTypeConfig` de
  test existants (`smokeConfig`, pattern déjà utilisé dans
  `WorkflowPersistenceTest`) suffisent.

## Test Reporting & Coverage

- Backend : `./mvnw verify` (Jacoco `BUNDLE` LINE ≥ 85%, BRANCH ≥ 75%).
- Frontend : `npm run test` (Vitest), `npm run typecheck`, `npm run lint`, `npm run test:e2e` (Playwright) — preuve fraîche consignée en fin de Phase 8, pas affirmée par défaut.

## Manual Testing

Non applicable — couvert par Playwright (parcours admin complet).

## Performance Testing

Non applicable — opération admin ponctuelle, pas de chemin chaud.

## Bug Tracking

Aucun processus dédié — anomalies traitées comme tout défaut de test
découvert en TDD.

## Requirements → Tests traceability

| Success criterion (requirements doc) | Test(s) |
|---|---|
| Section upload dans `/admin/workflow-types`, rôles gardés | E2E parcours complet + Vitest `WorkflowConfigsAdminView.vue` + intégration `403` |
| Prévisualisation avant tout appel réseau | Vitest `BpmnUploadPreview.vue` (aucun appel réseau avant confirmation) |
| Rejet BPMN invalide | `DeployBpmnProcessUseCaseImplTest` + intégration `400` + E2E |
| Rejet clé inconnue | `DeployBpmnProcessUseCaseImplTest` + intégration |
| Déploiement visible sans redémarrage | `FlowableWorkflowDeploymentAdapterTest` |
| `README.md`/`05-workflow-engine.md` corrigés | Hors tests — revue manuelle Phase 5 |
| TODO §35 clos | Hors tests — mise à jour doc post-implémentation |
