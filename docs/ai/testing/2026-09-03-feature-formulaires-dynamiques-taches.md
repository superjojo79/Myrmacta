---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy — `formulaires-dynamiques-taches`

> Dérivé des success criteria/edge cases du requirements et des composants du design
> ([2026-09-03](../requirements/2026-09-03-feature-formulaires-dynamiques-taches.md) /
> [design](../design/2026-09-03-feature-formulaires-dynamiques-taches.md)). Feature touchant
> `frontend/**` : Vitest **et** Playwright obligatoires (pas de dérogation « backend-only »).

## Test Coverage Goals

- Jacoco LINE ≥85 % / BRANCH ≥75 % tenus (seuils existants du dépôt) sur tout le code
  nouveau/modifié.
- Chaque décision tranchée en requirements (D-FORM-KIND-SCOPE, `documentTemplateKeys` vide,
  dégradation kind inconnu) a au moins un test qui la prouve, pas seulement le chemin nominal.
- Vitest (unitaire/composants) + Playwright (e2e) verts, mocks e2e mis à jour si les shapes API
  changent (`MyWorkflowTaskResponse`, `WorkflowTaskView`, `DossierTypeResponse`).

## Unit Tests (backend, domaine + use cases)

### `DossierType` (domaine)
- [x] Construction avec `documentTemplateKeys` non fourni → défaut `List.of()` (comme
  `fieldDefinitions`).
- [x] `documentTemplateKeys` défensivement copié (`List.copyOf`), immuable depuis l'extérieur.
- [x] `withUpdate(...)` propage/replace `documentTemplateKeys`.

### `ListDocumentTemplatesForDossierTypeUseCaseImpl` (nouveau)
- [x] `DossierType` avec `documentTemplateKeys` vide → retourne `List.of()` (pas d'appel au
  repository de templates).
- [x] `DossierType` avec 2 clés, une seule ayant un template `ACTIVE` → seul ce template est
  retourné (l'autre clé n'a peut-être qu'un `DRAFT`/`ARCHIVED`) — couvert par le test JPA
  `findActiveByTemplateKeysIn_returnsOnlyActiveMatchingKeys`.
- [x] `DossierType` introuvable pour le code donné → exception cohérente avec
  `GetDossierTypeUseCase.getByCode` (404 au niveau contrôleur).
- [x] **Écart trouvé** : l'accès n'est pas seulement « authentifié » mais soumis à la même
  vérification `VIEW` (`DossierAccessPolicy`) que `GET /api/dossier-types/{code}` — testé en réel
  (403 sans ACL, 200 après `grant(..., "VIEW")`), voir implementation § M3.
- [ ] Isolation tenant explicite au niveau use case (déléguée implicitement au repository JPA,
  pas testée par un double-tenant dédié dans ce cycle — la requête JPA est bornée par `tenantId`
  comme toutes les autres méthodes du repository, patron déjà couvert ailleurs dans la suite).

### `DocumentTemplateRepository.findActiveByTemplateKeysIn` (port + adaptateur JPA)
- [x] Liste de clés vide → `List.of()` (`findActiveByTemplateKeysIn_emptyKeys_returnsEmpty`).
- [x] Filtre `status = ACTIVE` appliqué (un `DRAFT`/`ARCHIVED` avec la même clé n'apparaît pas).

### `ListMyTasksUseCaseImpl` / `FlowableWorkflowTaskInboxAdapter`
- [x] Tâche Flowable avec `formKey` défini → propagé tel quel jusqu'à `MyWorkflowTask.formKey`.
- [x] Tâche sans `formKey` (`null`) → `MyWorkflowTask.formKey() == null`, comportement identique à
  avant la feature (non-régression explicite).

### `FlowableWorkflowVisualizationAdapter`
- [x] `toTaskView` propage `task.getFormKey()` dans `WorkflowTaskView.formKey`.

### `CreateDossierTypeUseCaseImpl` / `UpdateDossierTypeUseCaseImpl`
- [ ] `documentTemplateKeys` transmis tel quel à la construction/mise à jour du `DossierType`.
- [ ] `PATCH` avec `documentTemplateKeys` absent (`null`) conserve la valeur existante (même
  patron que `fieldDefinitions`/A-PATCH-INHERIT) ; `[]` explicite la vide.

## Integration Tests (backend)

- [x] `DossierApiTest` (4 nouveaux tests) : `POST`/`PATCH /api/dossier-types` round-trip
  `documentTemplateKeys`, défaut vide, préservation sur `PATCH` sans le champ, remplacement
  explicite par `[]`. 82/82 tests verts sur la classe (zéro régression).
- [x] **Nouveau** `GET /api/dossier-types/{code}/document-templates` :
  - [x] Réponse ne contient jamais `htmlContent`/`status`/`version` (vérification explicite du
    schéma JSON de réponse via `not(hasKey(...))`, pas seulement absence de valeur).
  - [x] Accessible à un utilisateur authentifié **sans** rôle `TENANT_ADMIN` **muni d'une entrée
    ACL `VIEW`** (contrairement à `GET /api/document-templates`) — test de régression sur l'écart
    de sécurité trouvé en design, précisé en implémentation (VIEW ACL requis, pas un accès
    inconditionnel).
  - [x] `documentTemplateKeys` vide → `200` avec liste vide (pas de `404`/erreur).
  - [x] Code de `DossierType` inconnu → `404`.
- [x] `WorkflowApiTest` : `GET /api/workflow-tasks/mine` expose `formKey` dans le JSON de réponse
  (présent, valeur `null` sur `smokeTask` qui n'en porte pas — non-régression prouvée en réel).
- [x] `WorkflowApiTest` : `formKey` exposé sur `GET /api/workflow-links/{id}/tasks` (même preuve).
- [x] **Test d'intégration réel contre le moteur Flowable** (pas mocké) : `FormKeySmokeIT`
  (`form-key-smoke.bpmn20.xml`) — déploie un process avec un `userTask` portant
  `flowable:formKey=dossier-fields`, vérifie que `taskService.createTaskQuery()...getFormKey()`
  retourne bien la valeur **sans** le module `flowable-form` sur le classpath. **Confirmé**
  (2026-09-03, M0).
- [x] `DossierPersistenceTest.savesAndLoadsDossierTypeWithDocumentTemplateKeys` : round-trip réel
  H2. **Non couvert explicitement** (risque jugé faible, pattern déjà prouvé en production par
  `007-workflow-viz-columns.yaml`/`observer_user_ids`, identique) : simulation d'un `DossierType`
  déjà présent **avant** que la migration `019` ne s'applique (le schéma de test H2 applique
  toujours tout `db.changelog-master.yaml` avant le premier test, donc aucune ligne pré-existante
  à backfiller dans ce dépôt) — à surveiller uniquement si un vrai run Postgres pré-`019` révèle un
  écart.
- [x] ArchUnit (`ArchitectureRulesTest`/`ArchitectureRulesFixtureTest`, 8 tests) : toujours vert
  après les changements (`documentTemplateKeys`/`formKey` restent des types Java purs dans le
  domaine, aucune dépendance Flowable/JPA introduite dans
  `com.flowforge.domain`).

## Frontend — Vitest

### `taskFormRegistry.ts` (nouveau)
- [x] `resolveTaskFormComponent('dossier-fields')` → composant `DossierFieldsTaskForm`.
- [x] `resolveTaskFormComponent('document-generation')` → composant `DocumentGenerationTaskForm`.
- [x] `resolveTaskFormComponent(null)` → `null` (tâche sans `formKey`, bouton actuel).
- [x] `resolveTaskFormComponent('kind-inconnu')` → `null` (dégradation silencieuse, décision
  tranchée D-FORM-KIND-SCOPE/kind inconnu).

### `DossierFieldsTaskForm.vue` (nouveau)
- [x] Rend `DynamicFields.vue` avec le schéma complet du `DossierType` du dossier lié.
- [x] Soumission appelle `PATCH /api/dossiers/{id}/fields` avec `expectedVersion` propagé, puis
  émet `ready` (le parent complète la tâche).
- [x] Erreur de permission/concurrence (409/403 du `PATCH`) émise via `error`, pas masquée.

### `DocumentGenerationTaskForm.vue` (nouveau)
- [x] Appelle `GET /api/dossier-types/{code}/document-templates`, peuple le picker (une entrée par
  combinaison `templateKey`/`variantKey`/`locale`).
- [x] Liste vide reçue → message « aucun template disponible », pas d'erreur bloquante (edge case
  requirements).
- [x] Soumission émet `ready` avec exactement `{templateKey, variantKey, locale}`, **aucun**
  appel direct à un endpoint de génération de document.

### `MobileTasksView.vue` / `TaskList.vue` (modifiés)
- [x] Tâche sans `formKey` → bouton « Compléter » actuel inchangé (`data-testid="complete-task"`,
  non-régression testée explicitement).
- [x] Tâche avec `formKey=dossier-fields` → composant de formulaire affiché à la place du bouton
  nu, soumission déclenche bien `store.complete(taskId, variables)`.

### `flowablePropertiesProvider.spec.ts` (modifié)
- [x] Nouveau groupe `bpmn:UserTask` avec entrée `flowable:formKey`, round-trip get/set comme les
  entrées `serviceTask` existantes (y compris effacement → `undefined`).
- [x] Un élément qui n'est pas un `bpmn:UserTask` ne voit pas apparaître ce groupe (couvert par le
  test existant `adds no custom group for an element without a relevant flowable:* attribute`,
  `bpmn:StartEvent`).

### `DocumentTemplateKeysEditor.vue` (nouveau, admin)
- [x] Coche/décoche une clé → émet la liste à jour.
- [x] Dédoublonnage correct des `templateKey` à partir de plusieurs versions/variantes du même
  template actif ; ignore `DRAFT`/`ARCHIVED`.

## End-to-End Tests (Playwright)

- [x] Parcours admin (`E2E-FORM-3`) : éditer un `DossierType` existant, cocher un `templateKey`
  actif, sauvegarder → payload `PATCH` contient bien `documentTemplateKeys`.
- [x] Parcours BPMN (`E2E-FORM-4`) : poser `flowable:formKey=document-generation` sur un `userTask`
  dans l'éditeur réel (pas mocké), enregistrer le brouillon, vérifier l'attribut présent dans le
  XML exporté — **a révélé un vrai bug** (namespace `flowable` non déclaré sur le gabarit vierge,
  corrigé, voir implementation § M7) plutôt que de simplement le confirmer.
- [x] Parcours porteur de tâche `dossier-fields` (`E2E-FORM-1`) : tâche assignée avec ce `formKey`
  → formulaire de champs affiché (valeur pré-remplie réelle) → édition → `PATCH` réel intercepté →
  tâche disparaît de la liste après complétion.
- [x] Parcours porteur de tâche `document-generation` (`E2E-FORM-2`) : tâche assignée → picker
  peuplé par le nouvel endpoint M3 → sélection → `POST .../complete` intercepté, variables exactes
  vérifiées.
- [x] Non-régression : `E2E-5`/`E2E-7` (existants, inchangés) prouvent qu'une tâche sans `formKey`
  reste complétable à l'identique. Suite Playwright complète : 63/63 verts (59 existants + 4
  nouveaux), zéro régression.

## Test Data

- Fixtures : `DossierPersistenceTest`/`ListDocumentTemplatesForDossierTypeUseCaseImplTest`
  couvrent `documentTemplateKeys` vide et non vide (2 clés, une seule `ACTIVE`).
- Process BPMN de test réel `form-key-smoke.bpmn20.xml`
  (`src/main/resources/processes/`, patron `callback-smoke.bpmn20.xml`) pour `FormKeySmokeIT` (M0).

## Test Reporting & Coverage

- `./mvnw verify` (portée `Test*`/`*Test`/`*Tests`, ce dépôt n'a pas de plugin Failsafe — `*IT.java`
  en est exclu) : **1566 tests, 0 échec** (Jacoco LINE ≥85 %/BRANCH ≥75 % tenus), mesuré en isolation
  (`rm -rf target/surefire-reports` puis run seul, sans exécution Maven concurrente).
- `*IT.java` du périmètre de cette feature, exécutées explicitement (`FormKeySmokeIT`,
  `DocumentGenerationFormSmokeIT`, `DocumentTemplatePersistenceIT`, `OpenApiContractIT`) :
  **13 tests, 0 échec**.
  *(Correction Phase 9 : le chiffre « 1572 tests » précédemment consigné ici conflait les deux
  portées et était en outre contaminé par des rapports Surefire résiduels d'exécutions ciblées
  antérieures dans la session — voir `implementation.md` pour le détail.)*
- `cd frontend && npm run test` : **70 fichiers, 278/278 verts** (9 nouveaux tests suite à la revue
  adversariale : 5 sur `DossierFieldsTaskForm.spec.ts`, 5 sur `taskFormRegistry.spec.ts` — voir
  `implementation.md` §M8).
- `cd frontend && npm run test:e2e` : **63/63 verts** (59 existants + 4 `E2E-FORM-*`, dont E2E-FORM-2
  corrigé pour verrouiller le bon contrat de variables après la revue adversariale). 1 échec isolé
  sur `E2E-EDIT-BPMN-3` (non lié à cette feature) confirmé flaky, vert en isolation répétée ×2.
- `npm run typecheck`/`npm run lint` : propres.

## Manual Testing

- Vérification visuelle de l'écran Admin → Types (nouvelle section templates) et du picker
  `document-generation` sur mobile (viewport réduit, cohérent avec `MobileTasksView.vue`).

## Bug Tracking

- Tout écart réel trouvé pendant l'implémentation/les tests est documenté dans
  `docs/ai/implementation/2026-09-03-feature-formulaires-dynamiques-taches.md`, pas seulement
  corrigé silencieusement (patron des features précédentes du dépôt).
