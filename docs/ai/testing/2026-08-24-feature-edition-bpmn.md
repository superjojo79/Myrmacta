---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: done
created: 2026-08-24
updated: 2026-08-24
feature: edition-bpmn
design_ref: docs/ai/design/2026-08-24-feature-edition-bpmn.md
---

# Testing Strategy — `edition-bpmn`

> Scénarios exécutés (Phase 5/7/8, 2026-08-24). Preuves fraîches
> consignées ci-dessous (`./mvnw verify`, `npm run test`/`typecheck`/
> `lint`/`test:coverage`, `npx playwright test`), cette session.

## Test Coverage Goals

- Jacoco `BUNDLE` LINE ≥ 85% / BRANCH ≥ 75% — **atteint : LINE 93.84% /
  BRANCH 75.27%** (état final après Phase 7, 1110 tests). Coverage
  par classe vérifiée en Phase 8 (`jacoco.csv`) pour toutes les classes
  `edition-bpmn` : 100% sur la quasi-totalité, seuls 2 blocs `catch
  (IOException)` défensifs restent non couverts (`WorkflowBpmnDeploymentController`
  — code préexistant de `deploiement-bpmn`, hors scope ; `FlowableWorkflowDeploymentAdapter.findDeployedXml`
  — traduction d'exception non-branchante, difficile à déclencher avec
  un moteur Flowable réel sans corrompre le stockage interne, jugé
  acceptable).
- Chaque nouveau composant backend couvert par au moins un test direct —
  atteint (voir détail par classe ci-dessous).
- Frontend : Vitest sur tous les nouveaux composants/composables + 0
  régression sur `BpmnUploadPreview.vue` après extraction du composable ;
  Playwright sur le parcours complet — **196 tests Vitest, 47 scénarios
  Playwright, tous verts**. Coverage frontend (`npm run test:coverage`) :
  **statements 86.84% / branches 77.59% / functions 86.05% / lines
  88.71%** (≥ seuils configurés 80/75/70/60). **Écart réel trouvé et
  comblé en Phase 8** : `flowablePropertiesProvider.ts` — les 5 tests
  initiaux ne couvraient que `getGroups()` (quels groupes pour quel
  type d'élément), pas les fonctions `getValue`/`setValue` de chaque
  entrée (33% branch coverage) — c'est-à-dire le pont lecture/écriture
  réel entre l'UI et les attributs `flowable:*`, la logique la plus
  spécifique de cette feature (D-EDIT-SCOPE). 3 tests ajoutés
  (`Async`, `flowable:delegateExpression`, `flowable:scope` —
  `getValue`/`setValue` invoqués directement via les mocks
  `CheckboxEntry`/`TextFieldEntry` capturant leurs props), tous verts
  du premier coup (pas de bug trouvé, juste une confiance manquante sur
  du code jamais réellement exercé) → **100% lines/branches/functions/statements**
  sur ce fichier.

## Unit Tests

### `SaveBpmnDraftUseCaseImplTest` (nouveau, 5 scénarios — +2 trouvés en Phase 7)
- [x] XML bien formé BPMN 2.0 → délégué au port out (`save`), retourne le `BpmnDraft` avec `updatedAt`/`updatedBy`
- [x] XML malformé → `BpmnParserPort` lève `InvalidBpmnException`, propagée, port out jamais appelé
- [x] `processDefinitionKey` du chemin ≠ id du process extrait du XML → rejet (D-EDIT-XML-KEY-MATCH), port out jamais appelé
- [x] **Pas** de vérification `WorkflowTypeConfigRepository` (contrairement à `DeployBpmnProcessUseCaseImplTest`) — un brouillon peut exister sans config workflow correspondante
- [x] `xml` `null` → `InvalidBpmnException` (pas `NullPointerException`), `BpmnParserPort`/port out jamais appelés — **bug réel trouvé en Phase 7**, voir doc implementation
- [x] `xml` blanc (`"   "`) → même rejet contrôlé

### `GetBpmnDraftUseCaseImplTest` (nouveau, 2 scénarios)
- [x] Brouillon existant pour la clé → retourné
- [x] Aucun brouillon pour la clé → résultat vide (pas d'erreur)

### `DeployBpmnProcessUseCaseImplTest` (modifié, +2 scénarios, 5 au total)
- [x] Déploiement réussi → le brouillon existant pour cette clé est supprimé via `BpmnDraftRepository` (D-DESIGN-DRAFT-LIFECYCLE)
- [x] Déploiement échoué (`InvalidBpmnException`/`UnknownWorkflowKeyException`) → le brouillon existant n'est **pas** supprimé
- [x] 3 scénarios existants (cf. testing doc `deploiement-bpmn`) non régressés

### `FlowableWorkflowDeploymentAdapterTest` (modifié, +2 scénarios)
- [x] `findDeployedXml` sur une clé déployée → renvoie le XML de la dernière version (`RepositoryService.getProcessModel`)
- [x] `findDeployedXml` sur une clé jamais déployée → résultat vide
- [x] 4 scénarios existants (cf. testing doc `deploiement-bpmn`) non régressés

### `BpmnDraftJpaEntityTest` (nouveau, 7 scénarios — non prévu au testing doc initial)
- [x] Mapping domaine ↔ entité JPA testé isolément (même précédent que `FlowableBpmnParserAdapterTest` pour `deploiement-bpmn`) : construction, `toDomain()`/`fromDomain()`, égalité de la clé composite.

## Integration Tests

### `BpmnDraftApiTest` (nouveau, 8 scénarios — +2 trouvés en Phase 7)
- [x] `GET /api/workflow-bpmn-drafts/{key}` sans rôle `TENANT_ADMIN`/`WORKFLOW_ADMIN` → `403`
- [x] `GET .../{key}` avec brouillon existant → `200` + corps (`processDefinitionKey`, `xml`, `updatedAt`, `updatedBy`)
- [x] `GET .../{key}` sans brouillon → `204`
- [x] `PUT .../{key}` avec XML valide et clé cohérente → `200`, upsert (écrase un brouillon existant pour la même clé)
- [x] `PUT .../{key}` avec XML malformé → `400`
- [x] `PUT .../{key}` avec clé du chemin ≠ id du process dans le XML → `400`
- [x] `PUT .../{key}` sans rôle requis → `403` (couvert implicitement par `@PreAuthorize` classe, testé explicitement en Phase 7)
- [x] `PUT .../{key}` avec corps `{}` (champ `xml` manquant) → `400`, pas `500` — **bug réel trouvé en Phase 7**

### `WorkflowBpmnDeploymentApiTest` (modifié, +4 scénarios)
- [x] `GET /api/workflow-bpmn-deployments/{key}/xml` avec version déployée → `200`, corps `text/xml`
- [x] `GET .../{key}/xml` sans version déployée → `204`
- [x] `GET .../{key}/xml` sans rôle requis → `403`
- [x] `POST` de déploiement réussi sur une clé ayant un brouillon → le brouillon est supprimé (vérifié via `GET /api/workflow-bpmn-drafts/{key}` → `204` après)
- [x] 8 scénarios existants (cf. testing doc `deploiement-bpmn`) non régressés

### `JpaBpmnDraftRepositoryAdapterTest` (nouveau, 5 scénarios, JPA réel H2)
- [x] `save` puis `findByTenantIdAndProcessDefinitionKey` → round-trip correct
- [x] `save` avec la même clé composite deux fois → écrase l'enregistrement existant (pas de doublon, comportement merge Hibernate)
- [x] `deleteByTenantIdAndProcessDefinitionKey` sur une clé existante → supprimé ; sur une clé absente → no-op sans erreur
- [x] Isolation tenant : deux tenants avec la même `processDefinitionKey` → brouillons indépendants (clé composite inclut `tenant_id`)

## Frontend Tests

### `flowablePropertiesProvider.ts` (nouveau, Vitest, 8 scénarios — +3 en Phase 8)
- [x] Élément `serviceTask` → expose les champs `async`, `delegateExpression`, `expression` dans le panel
- [x] Élément `signal` → expose le champ `scope`
- [x] Élément sans attribut `flowable:*` pertinent (ex. `startEvent`) → aucun groupe custom ajouté
- [x] Attribut `flowable:*` présent dans le XML importé mais non couvert par le provider → préservé après `saveXML()` — **risque du design levé empiriquement** : le moddle `bpmn-js` préserve génériquement les attributs d'extension inconnus (`$attrs`/`get()`/`set()`, re-sérialisés verbatim), pas besoin d'extension moddle dédiée.
- [x] `flowable-async` : `getValue()` lit `flowable:async` comme booléen (`'true'` → `true`, absent → `false`), `setValue()` écrit `'true'`/`undefined` — **écart de couverture réel trouvé et comblé en Phase 8** (33% branch avant, 100% après)
- [x] `flowable:delegateExpression` : `getValue()`/`setValue()` lisent/écrivent l'attribut, `''` → `undefined` (suppression)
- [x] `flowable:scope` : `getValue()` retourne `''` par défaut si l'attribut est absent

### `BpmnEditorCanvas.vue` (nouveau, Vitest, 3 scénarios)
- [x] Monte `bpmn-js` `Modeler` (pas `NavigatedViewer`) et importe le XML fourni
- [x] Émet un événement/état « modifications non enregistrées » après une commande utilisateur (écoute `eventBus.on('commandStack.changed', ...)`)
- [x] `saveXML()` exposé/appelable par le parent, retourne un XML bien formé après édition

**Bug réel trouvé en E2E (T8.3), pas en Vitest** : le code initial écoutait
`modeler.get('commandStack').on(...)` — `CommandStack` de diagram-js n'a
pas de méthode `.on()` propre, les événements passent par l'`eventBus`.
Le test Vitest ci-dessus ne pouvait pas le détecter : il mockait
entièrement `bpmn-js/lib/Modeler`, y compris un `commandStack` doté d'un
`.on()` fictif — masquant l'erreur réelle
(`"p.get(...).on is not a function"`), qui n'apparaissait qu'au premier
rendu dans un vrai navigateur. Corrigé dans le code de production
(`BpmnEditorCanvas.vue`) et dans les mocks Vitest concernés
(`BpmnEditorCanvas.spec.ts`, `BpmnEditorView.spec.ts`).

### `useBpmnDraft.ts` (nouveau composable, Vitest, 5 scénarios)
- [x] `load(key)` avec brouillon existant → retourne le XML du brouillon
- [x] `load(key)` sans brouillon → retombe sur `getDeployedBpmnXml(key)`
- [x] `load(key)` sans brouillon ni diagramme déployé → état vide (nouveau diagramme)
- [x] `save(key, xml)` → appelle `PUT`, réinitialise l'état « modifications non enregistrées »
- [x] Écouteur `beforeunload` actif seulement si l'état « non enregistré » est vrai

### `useBpmnDeployment.ts` (nouveau composable, extrait de `BpmnUploadPreview.vue`, Vitest, 4 scénarios)
- [x] Appel `GET .../info`, affichage version/instances actives si la clé existe
- [x] Confirmation → `POST` multipart, gestion des erreurs serveur (`ProblemDetail`)
- [x] Comportement identique à l'implémentation actuelle de `BpmnUploadPreview.vue` (non-régression fonctionnelle après extraction)

### `BpmnUploadPreview.vue` (modifié — refactor vers `useBpmnDeployment.ts`)
- [x] Tous les scénarios Vitest existants (5, cf. testing doc `deploiement-bpmn`) restent verts **sans modification**

### `BpmnEditorView.vue` (nouveau, Vitest, 6 scénarios)
- [x] Sans `processDefinitionKey` en query param → canvas vierge (nouveau diagramme)
- [x] Avec `processDefinitionKey` et brouillon existant → charge le brouillon
- [x] Avec `processDefinitionKey`, pas de brouillon, diagramme déployé existant → charge le diagramme déployé
- [x] Sélection d'un fichier local (import) → charge le XML du fichier dans le canvas
- [x] Bouton « Enregistrer le brouillon » → appelle `useBpmnDraft.save`
- [x] Bouton « Déployer » → réutilise `useBpmnDeployment` (même flux que `BpmnUploadPreview.vue`)

**Bug réel trouvé et corrigé en TDD (pas en E2E)** : `BpmnEditorCanvas`
était monté avant que `useBpmnDraft.load()` (appelé dans le `onMounted` du
parent) n'ait résolu — Vue monte les enfants avant que le `onMounted` du
parent ne s'exécute, créant une course entre l'import du template vide et
le vrai import (brouillon/déployé). Corrigé par un flag `ready` qui ne
bascule qu'une fois le chargement initial réglé.

### `WorkflowConfigsAdminView.vue` (modifié — lien vers l'éditeur, 1 scénario)
- [x] Lien « Éditer » par config redirige vers `/admin/bpmn-editor?processDefinitionKey=...`

## End-to-End Tests (Playwright)

- [x] Création vierge (pas de brouillon, pas de diagramme déployé pour la
      clé) → sauvegarde du brouillon (`E2E-EDIT-BPMN-1`)
- [x] Édition d'un diagramme déjà déployé (pas de brouillon) → chargement,
      puis déploiement → succès affiché (`E2E-EDIT-BPMN-2`)
- [x] Import d'un fichier local dans l'éditeur → rendu réel dans le canvas
      (élément importé visible), puis sauvegarde sous la clé extraite du
      fichier (`E2E-EDIT-BPMN-3`)
- [x] Le brouillon existant prend priorité sur le diagramme déployé au
      premier chargement ; après un déploiement réussi (qui supprime le
      brouillon côté backend, D-DESIGN-DRAFT-LIFECYCLE), rouvrir l'éditeur
      sur la même clé retombe correctement sur le diagramme déployé — pas
      d'état client périmé (`E2E-EDIT-BPMN-4`)
- [x] Mocks/assertions `frontend/e2e/**` mis à jour pour les nouvelles
      routes API
- [x] `cd frontend && PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium npx playwright test`
      exécuté — **47 passed (23.4s)**, exit code 0, 0 régression sur les
      43 scénarios existants, 4 nouveaux (`E2E-EDIT-BPMN-1..4`) verts.
      Preuve fraîche : 2026-08-24, cette session.

**Écarts découverts en écrivant ces scénarios (pas prévus en Phase 4),
tous documentés dans le doc planning** : navigation par clic obligatoire
(le build en chemins relatifs casse `page.goto` direct sur une route
imbriquée — limitation préexistante du dépôt) ; attente explicite du
rendu réel du `Modeler` avant toute interaction (race condition côté
test) ; fixtures BPMN avec DI complète nécessaires pour le `Modeler`
(contrairement au `NavigatedViewer` en lecture seule qui ne vérifie
jamais que le rendu réussit) ; le bug `eventBus`/`commandStack` a été
trouvé et corrigé à cette étape.

## Test Data

- Fixtures BPMN de test : réutilise les `.bpmn20.xml` existants pour les
  tests unitaires/intégration backend. Côté E2E, nouvelles fixtures
  dédiées avec DI complète (`bpmndi:BPMNDiagram`) pour l'éditeur réel,
  distinctes des fixtures sans DI déjà utilisées par `E2E-BPMN-1/2/3`
  (upload/preview en lecture seule, qui tolèrent l'absence de DI).
- Pas de nouvelle donnée de seed en base au-delà des `WorkflowTypeConfig`
  de test existants.

## Test Reporting & Coverage

- Backend : `./mvnw verify` — **1110 tests, 0 échec** (1106 + 4 ajoutés en
  Phase 7 pour le bug `xml` `null`/manquant), Jacoco `BUNDLE`
  LINE 93.84% / BRANCH 75.27% (≥ 85%/75%).
- Frontend : `npm run test` — **196/196** (193 + 3 ajoutés en Phase 8 sur
  `flowablePropertiesProvider.ts`) ; `npm run test:coverage` —
  statements 86.84% / branches 77.59% / functions 86.05% / lines 88.71%
  (≥ seuils 80/75/70/60) ; `npm run typecheck` — propre ; `npm run lint`
  — propre ; `npm run test:e2e` — **47/47**. Preuves fraîches consignées
  ci-dessus, 2026-08-24.

## Manual Testing

Non applicable — couvert par Playwright (parcours admin complet).

## Performance Testing

Non applicable — opération admin ponctuelle, pas de chemin chaud. Le
brouillon est un `text` unique par clé (pas de volumétrie particulière).

## Bug Tracking

Aucun processus dédié. 4 bugs réels trouvés et corrigés durant cette
feature (tous documentés ci-dessus et dans le doc implementation) :
`eventBus`/`commandStack` (frontend, trouvé en écrivant l'E2E),
race condition de montage `BpmnEditorCanvas`/`useBpmnDraft.load()`
(frontend, trouvé en TDD), `NullPointerException` sur `xml`
`null`/manquant → `500` au lieu de `400` (backend, trouvé en Phase 7,
revue fichier par fichier dédiée), plus 1 défaut de fixture de test
(BPMN sans DI incompatible avec le `Modeler` réel, trouvé en écrivant
l'E2E — pas un bug de production). Phase 8 : pas de nouveau bug, mais un
écart de couverture réel comblé (`flowablePropertiesProvider.ts`, voir
Test Coverage Goals).

## Requirements → Tests traceability

| Success criterion (requirements doc) | Test(s) |
|---|---|
| Nouvel écran dédié, gardé par rôles existants | E2E parcours complet + Vitest `BpmnEditorView.vue` + intégration `403` |
| 3 points d'entrée (vierge, import fichier, diagramme déployé) | Vitest `BpmnEditorView.vue` (3 scénarios) + `useBpmnDraft.ts` + E2E `E2E-EDIT-BPMN-1/2/3` |
| Panel de propriétés standard + provider custom | Vitest `flowablePropertiesProvider.ts` (5 scénarios) |
| Brouillon persisté côté backend, un par clé, écrasé | `SaveBpmnDraftUseCaseImplTest`, `GetBpmnDraftUseCaseImplTest`, `BpmnDraftApiTest`, `JpaBpmnDraftRepositoryAdapterTest` |
| Bouton déployer intégré, logique partagée non dupliquée | `useBpmnDeployment.ts` + non-régression `BpmnUploadPreview.vue` |
| Confirmation `beforeunload` si modifications non enregistrées | Vitest `useBpmnDraft.ts` |
| Aucune app tierce introduite | Hors tests — revue manuelle des dépendances `package.json` (`bpmn-js-properties-panel` seul ajout, MIT) |
| Brouillon supprimé après déploiement réussi (design) | `DeployBpmnProcessUseCaseImplTest`, `WorkflowBpmnDeploymentApiTest`, `E2E-EDIT-BPMN-4` |
