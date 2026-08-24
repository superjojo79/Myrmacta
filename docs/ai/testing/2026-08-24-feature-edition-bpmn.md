---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: planned
created: 2026-08-24
updated: 2026-08-24
feature: edition-bpmn
design_ref: docs/ai/design/2026-08-24-feature-edition-bpmn.md
---

# Testing Strategy — `edition-bpmn`

> Scénarios dérivés des success criteria (requirements) et des
> composants (design). Statut `planned` — aucun test encore écrit,
> Phase 4 (planning) uniquement. Les cases seront cochées `[x]` en Phase
> 5/7/8 avec preuve d'exécution fraîche (skill `verify`).

## Test Coverage Goals

- Jacoco `BUNDLE` LINE ≥ 85% / BRANCH ≥ 75% (seuils en place depuis
  `fiabilisation-tests-plateforme`) — doit rester vert après ajout de
  cette feature.
- Chaque nouveau composant backend (use cases, port, adaptateur,
  contrôleur) couvert par au moins un test direct, pas seulement
  transitivement.
- Frontend : Vitest pour tous les nouveaux composants/composables
  (`BpmnEditorCanvas.vue`, `flowablePropertiesProvider.ts`,
  `useBpmnDraft.ts`, `useBpmnDeployment.ts`, `BpmnEditorView.vue`) +
  non-régression sur `BpmnUploadPreview.vue` après extraction du
  composable. Playwright pour le parcours complet (feature touche le
  frontend, E2E obligatoire).

## Unit Tests

### `SaveBpmnDraftUseCaseImplTest` (nouveau)
- [ ] XML bien formé BPMN 2.0 → délégué au port out (`save`), retourne le `BpmnDraft` avec `updatedAt`/`updatedBy`
- [ ] XML malformé → `BpmnParserPort` lève `InvalidBpmnException`, propagée, port out jamais appelé
- [ ] `processDefinitionKey` du chemin ≠ id du process extrait du XML → rejet (D-EDIT-XML-KEY-MATCH), port out jamais appelé
- [ ] **Pas** de vérification `WorkflowTypeConfigRepository` (contrairement à `DeployBpmnProcessUseCaseImplTest`) — un brouillon peut exister sans config workflow correspondante

### `GetBpmnDraftUseCaseImplTest` (nouveau)
- [ ] Brouillon existant pour la clé → retourné
- [ ] Aucun brouillon pour la clé → résultat vide (pas d'erreur)

### `DeployBpmnProcessUseCaseImplTest` (modifié)
- [ ] Déploiement réussi → le brouillon existant pour cette clé est supprimé via `BpmnDraftRepository` (D-DESIGN-DRAFT-LIFECYCLE)
- [ ] Déploiement échoué (`InvalidBpmnException`/`UnknownWorkflowKeyException`) → le brouillon existant n'est **pas** supprimé
- [ ] Scénarios existants (3, cf. testing doc `deploiement-bpmn`) non régressés

### `FlowableWorkflowDeploymentAdapterTest` (modifié — nouvelle méthode)
- [ ] `findDeployedXml` sur une clé déployée → renvoie le XML de la dernière version (`RepositoryService.getProcessModel`)
- [ ] `findDeployedXml` sur une clé jamais déployée → résultat vide
- [ ] Scénarios existants (4, cf. testing doc `deploiement-bpmn`) non régressés

## Integration Tests

### `BpmnDraftApiTest` (nouveau)
- [ ] `GET /api/workflow-bpmn-drafts/{key}` sans rôle `TENANT_ADMIN`/`WORKFLOW_ADMIN` → `403`
- [ ] `GET .../{key}` avec brouillon existant → `200` + corps (`processDefinitionKey`, `xml`, `updatedAt`, `updatedBy`)
- [ ] `GET .../{key}` sans brouillon → `204`
- [ ] `PUT .../{key}` avec XML valide et clé cohérente → `200`, upsert (écrase un brouillon existant pour la même clé)
- [ ] `PUT .../{key}` avec XML malformé → `400`
- [ ] `PUT .../{key}` avec clé du chemin ≠ id du process dans le XML → `400`

### `WorkflowBpmnDeploymentApiTest` (modifié — nouvel endpoint)
- [ ] `GET /api/workflow-bpmn-deployments/{key}/xml` avec version déployée → `200`, corps `text/xml`
- [ ] `GET .../{key}/xml` sans version déployée → `204`
- [ ] `GET .../{key}/xml` sans rôle requis → `403`
- [ ] `POST` de déploiement réussi sur une clé ayant un brouillon → le brouillon est supprimé (vérifié via `GET /api/workflow-bpmn-drafts/{key}` → `204` après)
- [ ] Scénarios existants (8, cf. testing doc `deploiement-bpmn`) non régressés

### `JpaBpmnDraftRepositoryAdapterTest` (nouveau, JPA réel H2)
- [ ] `save` puis `findByTenantIdAndProcessDefinitionKey` → round-trip correct
- [ ] `save` avec la même clé composite deux fois → écrase l'enregistrement existant (pas de doublon, comportement merge Hibernate)
- [ ] `deleteByTenantIdAndProcessDefinitionKey` sur une clé existante → supprimé ; sur une clé absente → no-op sans erreur
- [ ] Isolation tenant : deux tenants avec la même `processDefinitionKey` → brouillons indépendants (clé composite inclut `tenant_id`)

## Frontend Tests

### `flowablePropertiesProvider.ts` (nouveau, Vitest)
- [ ] Élément `serviceTask` → expose les champs `async`, `delegateExpression`, `expression` dans le panel
- [ ] Élément `signal` → expose le champ `scope`
- [ ] Élément sans attribut `flowable:*` pertinent (ex. `startEvent`) → aucun groupe custom ajouté
- [ ] Attribut `flowable:*` présent dans le XML importé mais non couvert par le provider → préservé après `saveXML()` (pas de perte silencieuse — vérifie empiriquement le comportement du moddle, cf. point ouvert design)

### `BpmnEditorCanvas.vue` (nouveau, Vitest)
- [ ] Monte `bpmn-js` `Modeler` (pas `NavigatedViewer`) et importe le XML fourni
- [ ] Émet un événement/état « modifications non enregistrées » après une commande utilisateur (écoute `commandStack.changed`)
- [ ] `saveXML()` exposé/appelable par le parent, retourne un XML bien formé après édition

### `useBpmnDraft.ts` (nouveau composable, Vitest)
- [ ] `load(key)` avec brouillon existant → retourne le XML du brouillon
- [ ] `load(key)` sans brouillon → retombe sur `getDeployedBpmnXml(key)`
- [ ] `load(key)` sans brouillon ni diagramme déployé → état vide (nouveau diagramme)
- [ ] `save(key, xml)` → appelle `PUT`, réinitialise l'état « modifications non enregistrées »
- [ ] Écouteur `beforeunload` actif seulement si l'état « non enregistré » est vrai

### `useBpmnDeployment.ts` (nouveau composable, extrait de `BpmnUploadPreview.vue`, Vitest)
- [ ] Appel `GET .../info`, affichage version/instances actives si la clé existe
- [ ] Confirmation → `POST` multipart, gestion des erreurs serveur (`ProblemDetail`)
- [ ] Comportement identique à l'implémentation actuelle de `BpmnUploadPreview.vue` (non-régression fonctionnelle après extraction)

### `BpmnUploadPreview.vue` (modifié — refactor vers `useBpmnDeployment.ts`)
- [ ] Tous les scénarios Vitest existants (5, cf. testing doc `deploiement-bpmn`) restent verts sans changement de comportement observable

### `BpmnEditorView.vue` (nouveau, Vitest)
- [ ] Sans `processDefinitionKey` en query param → canvas vierge (nouveau diagramme)
- [ ] Avec `processDefinitionKey` et brouillon existant → charge le brouillon
- [ ] Avec `processDefinitionKey`, pas de brouillon, diagramme déployé existant → charge le diagramme déployé
- [ ] Sélection d'un fichier local (import) → charge le XML du fichier dans le canvas
- [ ] Bouton « Enregistrer le brouillon » → appelle `useBpmnDraft.save`
- [ ] Bouton « Déployer » → réutilise `useBpmnDeployment` (même flux que `BpmnUploadPreview.vue`)

### `WorkflowConfigsAdminView.vue` (modifié — lien vers l'éditeur)
- [ ] Lien « Éditer » par config redirige vers `/admin/bpmn-editor?processDefinitionKey=...`

## End-to-End Tests (Playwright — obligatoire, feature touche le frontend)

- [ ] Création vierge → édition (ajout d'une tâche) → sauvegarde du brouillon → rechargement de la page → le brouillon est repris (`E2E-EDIT-BPMN-1`)
- [ ] Édition d'un diagramme déjà déployé (pas de brouillon) → le diagramme déployé est chargé, modification, sauvegarde brouillon, puis déploiement → succès affiché (`E2E-EDIT-BPMN-2`)
- [ ] Import d'un fichier local dans l'éditeur → rendu dans le canvas (`E2E-EDIT-BPMN-3`)
- [ ] Déploiement réussi depuis l'éditeur → le brouillon de cette clé n'est plus proposé à la réouverture de l'éditeur sur cette clé (D-DESIGN-DRAFT-LIFECYCLE, `E2E-EDIT-BPMN-4`)
- [ ] Mocks/assertions `frontend/e2e/**` mis à jour pour les nouvelles routes API (`GET`/`PUT /api/workflow-bpmn-drafts/{key}`, `GET /api/workflow-bpmn-deployments/{key}/xml`)
- [ ] `cd frontend && PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium npx playwright test` exécuté, 0 régression sur les scénarios existants — preuve fraîche à consigner en Phase 8.

## Test Data

- Fixtures BPMN de test : réutilise les `.bpmn20.xml` déjà utilisés par
  `deploiement-bpmn` (process id correspondant à un `workflowKey` de
  test) ; un XML minimal supplémentaire avec `flowable:async`/
  `flowable:delegateExpression`/`flowable:expression`/`flowable:scope`
  pour exercer `flowablePropertiesProvider.ts`.
- Pas de nouvelle donnée de seed en base au-delà des `WorkflowTypeConfig`
  de test existants.

## Test Reporting & Coverage

- Backend : `./mvnw verify` (Jacoco `BUNDLE` LINE ≥ 85%, BRANCH ≥ 75%).
- Frontend : `npm run test` (Vitest), `npm run typecheck`, `npm run lint`,
  `npm run test:e2e` (Playwright) — preuve fraîche consignée en fin de
  Phase 8, pas affirmée par défaut.

## Manual Testing

Non applicable — couvert par Playwright (parcours admin complet).

## Performance Testing

Non applicable — opération admin ponctuelle, pas de chemin chaud. Le
brouillon est un `text` unique par clé (pas de volumétrie particulière).

## Bug Tracking

Aucun processus dédié — anomalies traitées comme tout défaut de test
découvert en TDD.

## Requirements → Tests traceability

| Success criterion (requirements doc) | Test(s) |
|---|---|
| Nouvel écran dédié, gardé par rôles existants | E2E parcours complet + Vitest `BpmnEditorView.vue` + intégration `403` |
| 3 points d'entrée (vierge, import fichier, diagramme déployé) | Vitest `BpmnEditorView.vue` (3 scénarios) + `useBpmnDraft.ts` + E2E `E2E-EDIT-BPMN-2/3` |
| Panel de propriétés standard + provider custom | Vitest `flowablePropertiesProvider.ts` (4 scénarios) |
| Brouillon persisté côté backend, un par clé, écrasé | `SaveBpmnDraftUseCaseImplTest`, `GetBpmnDraftUseCaseImplTest`, `BpmnDraftApiTest`, `JpaBpmnDraftRepositoryAdapterTest` |
| Bouton déployer intégré, logique partagée non dupliquée | `useBpmnDeployment.ts` + non-régression `BpmnUploadPreview.vue` |
| Confirmation `beforeunload` si modifications non enregistrées | Vitest `useBpmnDraft.ts` |
| Aucune app tierce introduite | Hors tests — revue manuelle des dépendances `package.json` |
| Brouillon supprimé après déploiement réussi (design) | `DeployBpmnProcessUseCaseImplTest`, `WorkflowBpmnDeploymentApiTest`, `E2E-EDIT-BPMN-4` |
