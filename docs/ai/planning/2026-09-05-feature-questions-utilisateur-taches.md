---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown

> Feature `questions-utilisateur-taches`. Découpage dérivé des documents
> [requirements](../requirements/2026-09-05-feature-questions-utilisateur-taches.md),
> [design](../design/2026-09-05-feature-questions-utilisateur-taches.md) et
> [testing](../testing/2026-09-05-feature-questions-utilisateur-taches.md). Suit l'ordre
> backend → frontend → E2E déjà utilisé pour TODO §47/§52 (M1…Mn enchaînés en TDD).
>
> **Statut (2026-09-05) : clos.** Tous les milestones livrés sur demande explicite (« lance la
> phase 5, enchaîne les milestones et les tâches »). Quelques déviations réelles par rapport au
> découpage initial, documentées à leur emplacement (M3, M6/M7, M8, M9) et dans le document
> implementation § Déviations — aucune n'a bloqué la livraison.

## Milestones

- [x] M1 : Modèle domaine + validation de schéma (`TaskQuestionSchema`, garde-fou collision)
- [x] M2 : Persistance (JPA/Liquibase) + `WorkflowEnginePort.findFormKeyByTaskId`
- [x] M3 : Use cases CRUD + lecture runtime scopée par tâche
- [x] M4 : Contrôleurs (admin CRUD + endpoint runtime)
- [x] M5 : Intégration `CompleteTaskUseCaseImpl` (validation, traçabilité, `submittedBy`)
- [x] M6 : Écran admin frontend
- [x] M7 : Composant de tâche frontend + registre
- [x] M8 : Fixtures BPMN + Playwright + non-régression
- [x] M9 : Vérification finale, documentation

## Task Breakdown

### M1 — Modèle domaine + validation (backend, TDD, pur domaine)

- [x] 1.1 `domain.workflow.model.TaskQuestionSchema` (record), réutilise `FieldDefinition`/
  `FieldType` (`domain.dossier.model`).
- [x] 1.2 `TaskQuestionAnswer` (record de traçabilité, avec `submittedBy`).
- [x] 1.3 `UnknownTaskQuestionSchemaException` — **pas** de `TaskQuestionSchemaValidationException`
  distincte : les erreurs de validation restent de simples `IllegalArgumentException` (pattern déjà
  utilisé par `DossierType`/`assertDocumentTypeAllowed`, mappées en 400 par le handler existant,
  aucune nouvelle classe nécessaire).
- [x] 1.4 Validation à la sauvegarde : rejet `FieldType.FILE`, rejet nom de champ collisionnant
  avec `WorkflowVariables.RESERVED` **et** `WorkflowVariables.isReservedOrKnownKey(...)` (nouvelle
  méthode publique, pas un ensemble `KNOWN_BUSINESS_KEYS` exposé directement). **Non fait** : pas
  de garde applicative dédiée au `schemaKey` dupliqué — repose sur la contrainte unique DB (comme
  `DossierType.code`), cohérent mais non testé isolément ici.
- Couvre : Unit Tests §« `TaskQuestionSchema` / validation (domaine) » (testing doc).

### M2 — Persistance + port moteur (backend, TDD)

- [x] 2.1 `TaskQuestionSchemaJpaEntity`/`TaskQuestionAnswerJpaEntity` (colonne `fields`/`answers` en
  `@JdbcTypeCode(SqlTypes.JSON)`, pattern `DossierTypeJpaEntity`).
- [x] 2.2 Adaptateurs JPA + `SpringData*Repository`, contrainte unique `(tenant_id, schema_key)`.
- [x] 2.3 Migration Liquibase `020-task-question-tables.yaml`.
- [x] 2.4 `WorkflowEnginePort.findFormKeyByTaskId(String taskId): Optional<String>` + implémentation
  Flowable (`TaskService.getFormKey()`).
- Couvre : Integration Tests (exercé via `TaskQuestionSchemaApiTest`/`UserQuestionFormSmokeIT`, pas
  un test JPA isolé dédié).
- **Dépend de** M1.

### M3 — Use cases (backend, TDD)

- [x] 3.1 **Déviation** : un seul `TaskQuestionSchemaUseCaseImpl` (implémente `Save`/`Get`/
  `DeleteTaskQuestionSchemaUseCase`) au lieu des 5 use cases séparés initialement prévus (copiés du
  pattern `DossierType` sans vérifier le précédent le plus proche) — pattern
  `WorkflowTypeConfigUseCaseImpl` déjà établi dans le même package (`save()` sert indifféremment
  création et mise à jour). RBAC entièrement au contrôleur, aucun garde domaine (à la différence de
  `DossierType`/`TenantAdminGuard` — jugé non pertinent ici, artefact de conception workflow).
- [x] 3.2 `GetTaskQuestionSchemaForTaskUseCaseImpl` (runtime, vérifie via `findFormKeyByTaskId`).
- Couvre : `GetTaskQuestionSchemaForTaskUseCaseImplTest`, exercé aussi via `TaskQuestionSchemaApiTest`.
- **Dépend de** M2.

### M4 — Contrôleurs (backend, TDD)

- [x] 4.1 `TaskQuestionSchemaController` : `POST`/`GET`/`GET {schemaKey}`/`PUT`/`DELETE`
  (`hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')`), `GET {schemaKey}/runtime?taskId=...`.
- [x] 4.2 Test RBAC contre la vraie chaîne Spring Security (`TaskQuestionSchemaApiTest`, pas
  `standaloneSetup`).
- Couvre : Integration Tests §« `TaskQuestionSchemaController` », « Endpoint runtime ».
- **Dépend de** M3.

### M5 — Intégration complétion de tâche (backend, TDD)

- [x] 5.1 `CompleteTaskUseCase.complete(...)` gagne un paramètre `actorUserId` ;
  `WorkflowTaskController.complete()` le renseigne depuis `Authentication`.
- [x] 5.2 `CompleteTaskUseCaseImpl.assertAndRecordUserQuestionAnswers` : résout le `schemaKey` via
  `findFormKeyByTaskId` (jamais depuis les variables client), valide champs requis/`options`, écrit
  `TaskQuestionAnswer`. `WorkflowTaskController.complete()` devient `@Transactional` (même
  rationale documentée que `DossierExportController`) pour que cette écriture et `completeTask(...)`
  roulent back ensemble.
- [x] 5.3 Test d'intégration réel contre `user-question-smoke.bpmn20.xml` (`UserQuestionFormSmokeIT`)
  — confirme la lecture serveur du `formKey` **et** que la passerelle exclusive en aval lit
  effectivement la variable produite.
- [ ] 5.4 **Non fait** : pas de test de rollback transactionnel dédié (échec `completeTask` après
  écriture de `TaskQuestionAnswer`) — nécessiterait un mock partiel du moteur Flowable dans un
  contexte Spring réel, jugé hors périmètre pour cette itération (risque documenté dans le document
  implementation, pas silencieusement ignoré).
- [x] 5.5 Non-régression `assertDocumentTypeAllowed` après le changement de signature du port.
- [x] 5.6 **Ajouté en Phase 7** : validation de type des champs `NUMBER`/`DATE`/`BOOLEAN` — la
  validation initiale (5.2) ne couvrait que `required`/`SELECT`, acceptant silencieusement une
  valeur non conforme sur les autres types. Corrigé par réutilisation de `DossierFieldValidator`.
- **Dépend de** M2, M3.

### M6 — Écran admin (frontend, TDD)

- [x] 6.1 `TaskQuestionSchemasAdminView.vue` (liste + panneau d'édition inline), même famille que
  `DossierTypesAdminView.vue`.
- [ ] 6.2 **Non fait** : `FieldType.FILE` toujours proposé par `FieldDefinitionDefsEditor.vue`
  (réutilisé sans fork) — décision de planification pour ne pas risquer de régression sur
  `DossierTypesAdminView` pour un seul gain UX ; rejeté côté serveur (`TaskQuestionSchemaTest`)
  avec message explicite.
- [ ] 6.3 Pas de test dédié à l'affichage de l'erreur serveur de collision de nom de champ dans
  `TaskQuestionSchemasAdminView.spec.ts` — le chemin d'affichage générique existe (identique à
  `DossierTypesAdminView`) mais n'est pas exercé avec ce message précis.
- [x] 6.4 Client API `frontend/src/api/taskQuestionSchemas.ts`.
- **Dépend de** M4.

### M7 — Composant de tâche + registre (frontend, TDD)

- [x] 7.1 `UserQuestionTaskForm.vue` — charge le schéma via `GET .../runtime?taskId=...`, réutilise
  `DynamicFields.vue` **tel quel** (il n'avait déjà aucun binding dossier — le design supposait à
  tort qu'il fallait « en retirer » le binding, en réalité déjà générique par construction),
  `emit('ready', variables)`/`emit('error', ...)`.
- [ ] 7.2 Pas de test qui échouerait si un appel `PATCH /api/dossiers/{id}/fields` réapparaissait —
  garantie structurelle par absence d'import, non prouvée par un spy dédié.
- [x] 7.3 Entrée `user-question:<schemaKey>` (résolution par préfixe, pas une clé fixe) dans
  `taskFormRegistry.ts` + non-régression des entrées existantes.
- [x] **Déviation supplémentaire non anticipée** : le registre ne recevait jusqu'ici que
  `dossierId` — `taskId` et `formKey` ajoutés comme nouvelles props sur les 2 sites d'appel
  (`MobileTasksView.vue`, `TaskList.vue`), nécessaires pour l'endpoint runtime scopé par tâche et
  pour extraire le `schemaKey` côté client ; fallthrough sans effet sur les 2 kinds existants.
- [x] **Hors périmètre V1 (confirmé)** : pas de sélecteur `schemaKey` dédié dans
  `flowablePropertiesProvider.ts` — le champ texte libre `formKey` existant suffit.
- **Dépend de** M4.

### M8 — Fixtures & bout en bout

- [x] 8.1 Fixture BPMN `user-question-smoke.bpmn20.xml` : `userTask`
  `formKey = user-question:orientation-dossier` suivi d'une passerelle exclusive.
- [x] 8.2 **Déviation** : `E2E-USERQ-1`/`E2E-USERQ-2` scindés en 2 scénarios Playwright indépendants
  (affichage+complétion du formulaire ; création admin) plutôt qu'un seul chaîné — meilleure
  isolation. La preuve que la passerelle lit effectivement la variable (Playwright ne touche jamais
  un vrai moteur Flowable, tout est mocké) est apportée par `UserQuestionFormSmokeIT` (M5.3), pas
  par un scénario Playwright — correction de portée par rapport au document testing d'origine.
- [x] 8.3 Non-régression complète `E2E-FORM-*` — 65/65 (63 existants + 2 nouveaux).
- **Dépend de** M5, M6, M7.

### M9 — Vérification finale & documentation

- [x] 9.1 `./mvnw verify` : 1591 tests (1587 en Phase 5-6, +1 Phase 7, +3 Phase 8), 0 échec, ArchUnit
  conforme, Jacoco bundle tenu. `UserQuestionFormSmokeIT` exécutée séparément (`-Dtest=`,
  convention du dépôt).
- [x] 9.2 Vitest 289/289 (287 en Phase 5-7, +2 Phase 8), `vue-tsc -b` et `eslint .` propres,
  Playwright 65/65 (inchangé depuis Phase 5).
- [x] 9.3 **Fait en Phase 8** (dev-testing) : balayage Jacoco fichier par fichier après `mvn clean
  test`. 4 écarts réels trouvés et corrigés (2 branches jamais exercées, 1 méthode jamais appelée
  réutilisée dans les tests au lieu de littéraux dupliqués, 1 méthode de code mort supprimée) — voir
  document testing § Test Reporting & Coverage.
- [x] 9.4 `GUIDE-UTILISATEUR.md` (§0 + nouvelle §10) et `GUIDE-CONCEPTION-BPMN.md` (§3bis étendu)
  mis à jour.
- [x] 9.5 `analysis doc/00-index.md` (§23 marqué livré), `TODO.md` §55 clos, `FILE-ATTENTE.md` à jour.
- [ ] 9.6 `CHANGELOG.md` — différé (aucune PR ouverte à ce stade), conformément à la convention du
  dépôt déjà appliquée pour `schema-champs-dynamiques` (TODO §47).
- **Dépend de** M8.

## Dependencies

- Séquencement effectif M1 → M2 → M3 → M4/M5 → M6/M7 → M8 → M9, comme prévu.
- Aucune dépendance externe nouvelle (pas de nouvelle librairie Maven/npm).
- Réutilise des composants déjà livrés : `taskFormRegistry.ts`, `DynamicFields.vue`,
  `FieldDefinitionDefsEditor.vue`, `FieldDefinition`/`FieldType`, `CompleteTaskUseCaseImpl`,
  `WorkflowEnginePort`.
- Ressource : un seul développeur/session (`no-worktree`, branche
  `claude/form-user-questions-ehw2dg`).

## Timeline & Estimates

Pas d'estimation calendaire chiffrée (convention du dépôt). Réalisé en une session continue
(« enchaîne les milestones et les tâches »), volume final comparable à TODO §52.

## Risks & Mitigation

- **Exception `formKey` opaque (D-USERQ-FORMKEY-PARSING)** — mitigé par la documentation explicite
  en design et un commentaire de code (`TaskQuestionFormKeys`) renvoyant à cette décision.
- **`WorkflowEnginePort.findFormKeyByTaskId` sur Flowable 8.0.0** — risque confirmé faible, validé
  par `UserQuestionFormSmokeIT` (test réel, pas supposé).
- **RBAC controller testé en `standaloneSetup`** — mitigé : `TaskQuestionSchemaApiTest` utilise la
  vraie chaîne Spring Security de bout en bout.
- **Couverture Jacoco** — bundle tenu ; balayage fichier par fichier fait en Phase 8 (9.3), 4 écarts
  réels trouvés et corrigés, risque levé.
- **Portée du sélecteur BPMN (ergonomie)** — confirmé hors périmètre V1, documenté, pas un
  glissement silencieux.
- **Rollback transactionnel non testé (5.4)** — risque résiduel documenté, pas corrigé dans cette
  itération.

## Resources Needed

- Aucune nouvelle infrastructure/service externe.
- Outils déjà en place : Maven/ArchUnit/Jacoco, Vitest/Playwright, Liquibase, `ai-devkit`.
- Connaissance : mécanisme `formKey`/`taskFormRegistry.ts` (TODO §52), pattern `WorkflowTypeConfig`
  (RBAC contrôleur, pas de garde domaine, `save()` unique pour create/update).
