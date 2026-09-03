---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown — `formulaires-dynamiques-taches`

> Référence : [requirements](../requirements/2026-09-03-feature-formulaires-dynamiques-taches.md),
> [design](../design/2026-09-03-feature-formulaires-dynamiques-taches.md),
> [testing](../testing/2026-09-03-feature-formulaires-dynamiques-taches.md). TDD (`tdd` +
> `verify`) sur chaque tâche : test qui échoue → code → `./mvnw verify` (ou scope ciblé) avant de
> passer à la suivante. Développement sur la branche imposée `claude/next-feature-cycle-tlg6he`
> (pas de worktree isolé, cf. requirements).

## Milestones

- [x] **M0** — Spike de vérification technique (`Task.getFormKey()` sans `flowable-form`) — **confirmé**
- [x] **M1** — Modèle domaine `DossierType.documentTemplateKeys` + migration + persistence
- [x] **M2** — `formKey` de bout en bout côté backend (lecture Flowable → REST)
- [x] **M3** — Nouvel endpoint `document-templates` filtré par type de dossier
- [x] **M4** — Éditeur BPMN : `flowable:formKey` sur `bpmn:UserTask`
- [x] **M5** — Frontend : registre de kinds + formulaires de tâche
- [x] **M6** — Frontend : administration `documentTemplateKeys`
- [x] **M7** — Vérification globale, documentation, clôture

## Task Breakdown

### M0 — Spike de vérification technique (risque le plus élevé, fait en premier)

Objectif : lever le point ouvert restant (requirements/design) avant d'investir dans la plomberie
qui en dépend — si l'hypothèse est fausse, tout M2 change de forme.

- [x] **T0.1** — `src/main/resources/processes/form-key-smoke.bpmn20.xml` (`formKeySmoke`), patron
  des autres smoke process (`callback-smoke.bpmn20.xml`), `userTask` `formKeySmokeTask` avec
  `flowable:formKey="dossier-fields"`.
- [x] **T0.2** — `FormKeySmokeIT` (patron `CallbackSmokeIT`) : rouge d'abord
  (`FlowableObjectNotFoundException`, process pas encore déployé), vert après T0.1. Contre le vrai
  moteur H2 de la suite existante, pas mocké.
- **Validation : hypothèse confirmée** (2026-09-03) — `Task.getFormKey()` core `flowable-engine`
  fonctionne sans `flowable-form`. Détail dans
  [implementation](../implementation/2026-09-03-feature-formulaires-dynamiques-taches.md) § M0.
- **Couvre testing** : § Integration Tests, item « Test d'intégration réel contre le moteur
  Flowable ».
- **Dépendances** : aucune.

### M1 — Modèle domaine `DossierType.documentTemplateKeys`

- [x] **T1.1** — `DossierTypeTest` (+4 tests) ; `DossierType.java` (+record component,
  +constructeur de convenance ancienne forme pour `Create`/construction pure, `withUpdate`
  avec paramètre obligatoire — voir implementation § M1 pour la justification du choix
  convenience-ctor vs fix-all-callers).
- [x] **T1.2** — `019-dossier-type-document-template-keys.yaml` + `DossierTypeJpaEntity` + mapper.
- [x] **T1.3** — `DossierPersistenceTest.savesAndLoadsDossierTypeWithDocumentTemplateKeys` (H2 réel).
- [x] **T1.4** — Command records + `DossierApiDtos`/`DossierTypeController` (+4 tests API,
  82/82 verts sur `DossierApiTest`).
- **Couvre testing** : § Unit Tests `DossierType`, § Integration Tests (round-trip
  `documentTemplateKeys`, migration Liquibase).
- **Dépendances** : aucune (indépendant de M0/M2).

### M2 — `formKey` de bout en bout côté backend

- [x] **T2.1** — `WorkflowTaskInboxPort.ActiveTaskSnapshot` (+1 champ `formKey` nullable) ;
  `FlowableWorkflowTaskInboxAdapter` lit `task.getFormKey()`. Test adaptateur existant
  (`FlowableWorkflowTaskInboxAdapterTest`) étendu : tâche avec/sans `formKey`.
- [x] **T2.2** — `MyWorkflowTask` (+1 champ) ; `ListMyTasksUseCaseImpl` propage
  `ActiveTaskSnapshot.formKey()` sans transformation. Test `ListMyTasksUseCaseImplTest` étendu.
- [x] **T2.3** — `WorkflowTaskController.MyWorkflowTaskResponse` (+1 champ) exposé sur
  `GET /api/workflow-tasks/mine`. Test API (`WorkflowTaskControllerTest`/`*ApiTest`) : JSON contient
  `formKey` (présent+`null`, présent+renseigné).
- [x] **T2.4** — `WorkflowTaskView` (+1 champ) ; `FlowableWorkflowVisualizationAdapter.toTaskView`
  lit `task.getFormKey()`. Test `WorkflowLinkVisualizationUseCaseImplTest` étendu.
- [x] **T2.5** — `WorkflowLinkVisualizationController` : vérifier `formKey` déjà exposé (réponse
  directe, pas de DTO wrapper) — test API dédié si absent.
- **Couvre testing** : § Unit Tests `ListMyTasksUseCaseImpl`/`FlowableWorkflowTaskInboxAdapter`/
  `FlowableWorkflowVisualizationAdapter`, § Integration Tests (`formKey` dans les 2 réponses REST).
- **Dépendances** : M0 (confirme que `task.getFormKey()` fonctionne comme attendu avant de
  s'appuyer dessus partout).

### M3 — Nouvel endpoint `document-templates` filtré par type de dossier

- [x] **T3.1** — `DocumentTemplateRepository.findActiveByTemplateKeysIn(tenantId, List<String>)`
  (port) + implémentation `JpaDocumentTemplateRepositoryAdapter` (JPQL `IN` + `status=ACTIVE` +
  `tenantId`). Test contrat de port (patron `DocumentTemplatePortsContractTest`) + test JPA
  dédié (filtre statut, isolation tenant).
- [x] **T3.2** — `ListDocumentTemplatesForDossierTypeUseCase`/`Impl` (nouveau) : `documentTemplateKeys`
  vide → `List.of()` sans appel repository ; sinon délègue à T3.1 ; `DossierType` introuvable →
  exception cohérente avec `GetDossierTypeUseCase`.
- [x] **T3.3** — Nouvel endpoint `GET /api/dossier-types/{code}/document-templates` sur
  `DossierTypeController` (nouveau DTO `AllowedDocumentTemplateResponse` :
  `templateKey`/`variantKey`/`locale`/`label` seulement, **jamais** `htmlContent`). Test API :
  schéma de réponse n'expose pas `htmlContent` ; accessible sans rôle `TENANT_ADMIN` ;
  `documentTemplateKeys` vide → `200`/liste vide ; code inconnu → `404`.
- **Couvre testing** : § Unit Tests (nouveau use case, port), § Integration Tests (nouvel
  endpoint, régression de sécurité).
- **Dépendances** : M1 (a besoin de `DossierType.documentTemplateKeys`).

### M4 — Éditeur BPMN : `flowable:formKey` sur `bpmn:UserTask`

- [x] **T4.1** — `flowablePropertiesProvider.ts` : nouveau groupe pour `is(element,
  'bpmn:UserTask')` avec une entrée `flowable:formKey` (réutilise `textFieldEntry`, patron
  `delegateExpression`/`expression`). Test `flowablePropertiesProvider.spec.ts` étendu (groupe
  apparaît seulement sur `userTask`, get/set round-trip).
- **Couvre testing** : § Vitest `flowablePropertiesProvider.spec.ts`.
- **Dépendances** : aucune (indépendant du backend).

### M5 — Frontend : registre de kinds + formulaires de tâche

- [x] **T5.1** — `frontend/src/api/types.ts` : `MyWorkflowTask.formKey`, `WorkflowTaskView.formKey`,
  nouveau type `AllowedDocumentTemplate`. `frontend/src/api/*` : fonction
  `listDossierTypeDocumentTemplates(code)`.
- [x] **T5.2** — `taskFormRegistry.ts` (nouveau) + tests : résolution `dossier-fields` /
  `document-generation` / `null` / kind inconnu → `null`.
- [x] **T5.3** — `DossierFieldsTaskForm.vue` (nouveau) : rend `DynamicFields.vue`, soumet via
  `PATCH /api/dossiers/{id}/fields` (avec `expectedVersion`), puis complète la tâche. Tests
  Vitest : soumission, erreur de permission/concurrence affichée.
- [x] **T5.4** — `DocumentGenerationTaskForm.vue` (nouveau) : picker via T3.3, soumission =
  `complete(taskId, {templateKey, variantKey, locale})`. Tests Vitest : liste vide → message,
  soumission envoie exactement ces 3 variables.
- [x] **T5.5** — `MobileTasksView.vue`/`TaskList.vue` : résolution via le registre avant d'afficher
  le bouton nu. Tests Vitest étendus (non-régression tâche sans `formKey` + affichage formulaire
  quand `formKey` connu).
- **Couvre testing** : § Vitest (tous les nouveaux composants + non-régression), § E2E (parcours
  porteur de tâche, dépend aussi de M2/M3/M4 pour les données réelles bout en bout).
- **Dépendances** : T5.1 après M2/M3 (types alignés sur les réponses backend réelles) ; T5.2 avant
  T5.3/T5.4/T5.5.

### M6 — Frontend : administration `documentTemplateKeys`

- [x] **T6.1** — `DocumentTemplateKeysEditor.vue` (nouveau) : liste à cocher, dédoublonnage par
  `templateKey` à partir de `GET /api/document-templates` (admin, existant). Tests Vitest.
- [x] **T6.2** — Intégration dans `DossierTypesAdminView.vue` (nouvelle section, patron
  `FieldDefinitionDefsEditor.vue`) — création + édition. Tests Vitest étendus.
- **Couvre testing** : § Vitest `DocumentTemplateKeysEditor.vue`, § E2E (parcours admin).
- **Dépendances** : M1 (contrat `documentTemplateKeys` sur `DossierTypeResponse`).

### M7 — Vérification globale, documentation, clôture

- [x] **T7.1** — `./mvnw verify` complet (Jacoco LINE ≥85 %/BRANCH ≥75 %) ; combler tout écart de
  couverture réel trouvé (patron des features précédentes — jamais de seuil abaissé).
- [x] **T7.2** — `cd frontend && npm run test` puis `npm run test:e2e` (ou `test:all`) — parcours
  E2E complets du § testing, non-régression complète.
- [x] **T7.3** — `GUIDE-UTILISATEUR.md` : nouvelle section admin Types (association templates) ;
  tableau de rôles §0 inchangé (`TENANT_ADMIN` déjà listé). `GUIDE-CONCEPTION-BPMN.md` : nouvel
  attribut `flowable:formKey` documenté pour les auteurs de process, avec les 2 kinds V1.
  `CHANGELOG.md` : entrée `[Non publié]` synthétique une fois la PR ouverte.
- [x] **T7.4** — `TODO.md` §52 : statut `[x]`, résumé des décisions et écarts trouvés (patron des
  entrées précédentes). `FILE-ATTENTE.md` : déplacer `formulaires-dynamiques-taches` vers
  « Récemment livré », dater le fichier, recalculer la file (probablement vide à nouveau, sauf le
  provisioning tenant hors file produit).
- [x] **T7.5** — Revue finale (Phase 9, `dev-review`) avant PR. Écart trouvé et corrigé :
  `openapi/openapi.yaml` stale (endpoint + champs manquants) — régénéré et revérifié vert.
  Écart de vérification trouvé et corrigé : chiffre « 1572 tests » de T7.1 contaminé par des
  rapports Surefire résiduels ; chiffres réels mesurés en isolation et corrigés dans
  `implementation.md`/`testing.md`/`TODO.md`.
  **Revue adversariale approfondie complémentaire** (subagent dédié, sur demande explicite) : 6
  écarts réels trouvés et corrigés (M8 dans `implementation.md`) — kind `document-generation` non
  fonctionnel de bout en bout (mismatch de noms de variables), `DossierFieldsTaskForm` pouvait
  effacer un champ sensible et rendait la tâche incomplétable avec un champ NUMBER,
  `documentTemplateKeys` non appliqué à la complétion de tâche, registre de formulaires vulnérable
  aux clés `Object.prototype`, version PATCH non rafraîchie. Chacun corrigé avec test dédié.
  Vérification finale : 1566 tests `Test*` (0 échec) + 13 tests `*IT.java` du périmètre feature
  (0 échec) ; Vitest 278/278 ; Playwright 63/63. Aucun autre écart bloquant trouvé (design/
  implémentation alignés, pas de callers cassés, migration 019 réversible, sécurité du nouvel
  endpoint et de la complétion de tâche vérifiée, E2E réelles couvrent les bugs trouvés et
  corrigés). Prêt à push/PR.
- **Dépendances** : tout ce qui précède.

## Dependencies

- **M0 bloque M2** (hypothèse technique centrale à confirmer avant de brancher `formKey` partout).
- **M1 bloque M3 et M6** (`documentTemplateKeys` doit exister avant le filtrage et l'admin UI).
- **M2 et M3 bloquent M5** (le frontend consomme leurs contrats REST réels, pas des mocks
  spéculatifs).
- **M4 est indépendant** — peut être fait en parallèle de M1-M3 (aucune dépendance backend).
- Aucune dépendance externe (pas de nouvelle lib, pas de service tiers).

## Risks & Mitigation

- **Risque technique principal (M0)** : `Task.getFormKey()` pourrait se comporter différemment que
  documenté par la recherche externe sur la version Flowable 8.0.0 réellement vendored dans ce
  dépôt. Mitigation : M0 en tout premier, avant tout code de plomberie qui en dépend ; si
  l'hypothèse est fausse, retour en Phase 3 avant d'avoir écrit M2-M6.
- **Risque de régression** : `MobileTasksView.vue`/`TaskList.vue` sont déjà couverts par des tests
  Vitest/Playwright existants — toute modification doit garder le chemin « tâche sans `formKey` »
  identique. Mitigation : test de non-régression explicite en T5.5 avant d'ajouter le nouveau
  comportement.
- **Risque de sécurité** : le nouvel endpoint `document-templates` (M3) est le point le plus
  sensible du design (a corrigé un vrai trou d'accès en Phase 3) — un mauvais copier-coller
  pourrait réintroduire `htmlContent` dans la réponse ou hériter d'un `@PreAuthorize` restrictif.
  Mitigation : test API dédié qui vérifie explicitement l'absence de `htmlContent` et
  l'accessibilité sans rôle admin (§ testing, déjà planifié en T3.3).
- **Risque de dérive de scope** : `documentTemplateKeys` vide = aucun template disponible rend la
  feature invisible sur tous les `DossierType` existants tant qu'un `TENANT_ADMIN` n'a rien
  configuré — ne pas confondre avec une régression pendant les tests manuels (T-manual, comportement
  voulu et documenté).

## Resources Needed

- Aucune ressource externe nouvelle : stack, dépendances et outillage CI existants suffisent
  (pas de nouvelle lib Maven/npm).
- `./mvnw verify`, `npm run test`, `npm run test:e2e` déjà configurés dans ce dépôt.
