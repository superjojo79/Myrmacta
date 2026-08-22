---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
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

### `FlowableWorkflowDeploymentAdapterTest` (fait, H2/Flowable embarqué)
- [x] Déploiement réel via `RepositoryService` sur un BPMN de test → nouvelle version créée, visible immédiatement sans redémarrage (démarrage d'un processus avec cette clé juste après le déploiement)
- [x] Redéploiement de la même clé → version incrémentée, instances existantes sur l'ancienne version non affectées (comportement natif Flowable, observé pas forcé)
- [x] `latestVersion`/`activeInstanceCount` corrects après un déploiement + démarrage d'instances

## Frontend Tests

### `BpmnCanvas.vue` (nouveau, Vitest)
- [ ] Rend le XML fourni via bpmn-js `importXML`
- [ ] Applique les marqueurs optionnels (`activeIds`/`completedIds`/`failedIds`) si fournis, aucun marqueur si absents

### `BpmnViewer.vue` (non-régression après refactor, Vitest existant)
- [ ] Comportement inchangé : tous les tests Vitest existants restent verts sans modification (contrat public préservé)

### `BpmnUploadPreview.vue` (nouveau, Vitest)
- [ ] Sélection de fichier → lecture locale (`FileReader`), rendu via `BpmnCanvas.vue`, **aucun appel réseau** avant confirmation
- [ ] Après sélection, appel `GET .../info` pour afficher version actuelle/instances actives si la clé existe
- [ ] Fichier illisible/non-XML côté client → message d'erreur clair, pas de tentative de déploiement
- [ ] Confirmation → `POST` multipart, gestion des erreurs `400`/`403`/`413`/clé inconnue

### `WorkflowConfigsAdminView.vue` (Vitest, section ajoutée)
- [ ] Nouvelle section BPMN visible pour les rôles `TENANT_ADMIN`/`WORKFLOW_ADMIN`

## End-to-End Tests (Playwright — obligatoire, feature touche le frontend)

- [ ] Parcours complet : navigation vers `/admin/workflow-types` → sélection fichier BPMN valide → prévisualisation affichée → confirmation → déploiement réussi affiché
- [ ] Fichier BPMN invalide → erreur affichée, pas de déploiement
- [ ] Redéploiement d'une clé existante → info version/instances actives affichée avant confirmation
- [ ] Mocks/assertions `frontend/e2e/**` mis à jour pour les nouvelles routes API (`GET .../info`, `POST /api/workflow-bpmn-deployments`)
- [ ] `cd frontend && npm run test:e2e` (ou `test:all`) exécuté avec preuve fraîche (commande, code de sortie, compte de passes) consignée ici en fin de Phase 8

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
