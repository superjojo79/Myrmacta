---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

> Feature `questions-utilisateur-taches` (TODO §55). Cycle `dev-lifecycle` Phases 1–9 complet,
> milestones M1–M9 enchaînés en TDD sur demande explicite (« lance la phase 5, enchaîne les
> milestones et les tâches »).

## Code Structure

- Backend : `domain.workflow.model.{TaskQuestionSchema,TaskQuestionAnswer}`,
  `domain.workflow.{WorkflowVariables,TaskQuestionFormKeys}`, ports `domain.workflow.port.{in,out}`,
  use cases `domain.workflow.usecase.{TaskQuestionSchemaUseCaseImpl,
  GetTaskQuestionSchemaForTaskUseCaseImpl}`, persistance
  `infrastructure.persistence.workflow.{TaskQuestionSchemaJpaEntity,TaskQuestionAnswerJpaEntity,...}`,
  web `infrastructure.web.workflow.{TaskQuestionSchemaController,TaskQuestionSchemaApiDtos}`.
- Frontend : `api/taskQuestionSchemas.ts`, `views/admin/TaskQuestionSchemasAdminView.vue`,
  `components/workflow/task-forms/UserQuestionTaskForm.vue`, `workflow/taskQuestionFormKeys.ts`
  (miroir TS de `TaskQuestionFormKeys`), entrée dans `taskFormRegistry.ts`.

## Implementation Notes

### Core Features

- **Kind `user-question:<schemaKey>`** : le backend lit `formKey` côté serveur
  (`WorkflowEnginePort.findFormKeyByTaskId`, nouveau) pour résoudre le schéma à valider — jamais
  depuis une variable soumise par le client (D-USERQ-FORMKEY-PARSING).
- **`CompleteTaskUseCase.complete(...)`** (port existant) a gagné un paramètre `actorUserId` pour
  renseigner `TaskQuestionAnswer.submittedBy` (D-USERQ-SUBMITTED-BY) — seul appelant impacté :
  `WorkflowTaskController.complete()`.
- **Endpoint runtime** `GET /api/task-question-schemas/{schemaKey}/runtime?taskId=...` exige
  `taskId` et vérifie via `GetTaskQuestionSchemaForTaskUseCaseImpl` que le `formKey` de cette tâche
  référence bien le `schemaKey` demandé (D-USERQ-RUNTIME-SCOPE) — pas de `DossierType`/`VIEW` à
  vérifier pour ce concept indépendant du dossier.
- **Transaction** : `WorkflowTaskController.complete()` porte désormais `@Transactional` (même
  rationale documentée que `DossierExportController`) pour que l'écriture de `TaskQuestionAnswer`
  et `completeTask(...)` (Flowable, participe à la transaction Spring ambiante via
  `flowable-spring-boot-starter`) roulent back ensemble.

### Patterns & Best Practices

- **Réutilisation** : `FieldDefinition`/`FieldType` (`domain.dossier.model`) réutilisés tels quels
  (D-USERQ-FIELD-REUSE) ; `DynamicFields.vue` réutilisé tel quel côté frontend — il n'avait déjà
  aucun binding dossier, contrairement à l'hypothèse du document design (« pas son binding dossier »
  était en réalité déjà vrai par construction, pas quelque chose à retirer).
- **RBAC** : entièrement au contrôleur (`hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')`), pas de
  garde domaine — pattern `WorkflowTypeConfigUseCaseImpl`/`WorkflowTypeConfigController`, plus
  simple que le pattern `DossierType`/`TenantAdminGuard` (jugé plus proche car `TaskQuestionSchema`
  est un artefact de conception de workflow, pas une donnée `Dossier`-scopée).
- **Pas de concurrence optimiste** sur `TaskQuestionSchema` (D-USERQ-SCHEMA-CONCURRENCY) — corrigé
  en Phase 3 après avoir initialement copié à tort le pattern `expectedVersion` de
  `Dossier.fields()` ; le bon précédent est `DossierType` (aucune concurrence optimiste non plus).
- **Convention `formKey` partagée** : `TaskQuestionFormKeys` (backend) et
  `workflow/taskQuestionFormKeys.ts` (frontend) sont l'unique source de vérité du préfixe
  `"user-question:"` de chaque côté — leçon retenue de TODO §52 Phase 9 (mismatch de noms entre
  deux côtés testés isolément).

## Écarts réels trouvés en Phase 8 (dev-testing, balayage Jacoco), corrigés

Après le correctif Phase 7, un `mvn clean test` complet a été relancé pour régénérer un rapport
Jacoco fiable (le précédent était partiellement obsolète après les modifications), puis balayé
fichier par fichier — pas seulement le ratio global du bundle. 4 écarts réels trouvés :

- **`GetTaskQuestionSchemaForTaskUseCaseImpl`** : la branche « le `formKey` de la tâche correspond
  bien au `schemaKey` demandé, mais le schéma a été supprimé depuis » n'était jamais exercée — les
  tests existants ne couvraient que les cas de désaccord de `formKey`. Test ajouté
  (`getForTask_rejects_whenFormKeyMatchesButSchemaWasDeleted`).
- **`TaskQuestionSchemaController.update(...)`** : la branche 404 (`schemaKey` inconnu) n'était
  jamais exercée. Test ajouté (`update_unknownSchemaKey_returns404`). Dans la foulée, le chemin
  409 (`schemaKey` dupliqué, contrainte DB) n'était pas non plus testé — ajouté aussi
  (`create_withDuplicateSchemaKeyForTheSameTenant_returns409`, confirmé par le log Hibernate
  `23505` observé en sortie de test, preuve que c'est bien la contrainte DB qui rejette).
- **`TaskQuestionFormKeys.formKeyFor(...)`** : jamais appelée en dehors de sa propre classe —
  3 fichiers de test (`CompleteTaskUseCaseImplTest`, `GetTaskQuestionSchemaForTaskUseCaseImplTest`,
  `UserQuestionFormSmokeIT`) codaient en dur le littéral `"user-question:orientation-dossier"`
  au lieu de construire la chaîne via cette méthode — remplacé, à la fois plus sûr (une seule
  source de vérité, cohérent avec la raison d'être de cette classe) et lui donnant enfin une
  vraie couverture.
- **`TaskQuestionAnswerJpaEntity.toDomain()`** : code mort — 0 appelant (`TaskQuestionAnswerRepository`
  est volontairement write-only, D-USERQ-TRACE-SCOPE, aucun endpoint de consultation en V1).
  **Supprimé** plutôt que testé artificiellement (écrire un test qui appelle une méthode sans
  appelant réel en production n'aurait rien prouvé) — à rajouter avec le premier vrai lecteur.

Écarts de couverture restants, jugés à faible valeur et non corrigés : deux branches `null` vs
`blank`/valeur omise dans les méthodes privées utilitaires du contrôleur (`required(...)`,
`listOrEmpty(...)`) — comportement visible identique des deux côtés côté client. Couverture
apparente faible de `TaskQuestionAnswerJpaEntity`/`JpaTaskQuestionAnswerRepositoryAdapter` sous
`mvn test` seul : artefact connu de la convention du dépôt (`*IT.java` exclu du run par défaut,
la preuve réelle vient de `UserQuestionFormSmokeIT`), cohérent avec d'autres classes du dépôt
dans la même situation (ex. `FlowableWorkflowEngineAdapter.correlateMessage`, couvert
principalement par `CallbackSmokeIT`).

`./mvnw clean verify` : **1591 tests** (0 échec, +3 depuis la Phase 7), ArchUnit conforme,
couverture Jacoco tenue. Vitest **289/289** (+2), Playwright **65/65** (inchangé, zéro
régression), `vue-tsc`/`eslint` propres.

## Écart réel trouvé en Phase 7 (revue d'implémentation), corrigé

- **Validation de type incomplète** : `CompleteTaskUseCaseImpl.assertAndRecordUserQuestionAnswers`
  ne vérifiait initialement que `required` et l'appartenance aux `options` d'un champ `SELECT` —
  un champ `NUMBER` acceptait silencieusement une valeur non numérique (idem `DATE`/`BOOLEAN`),
  aucune conformité de type n'était vérifiée pour ces types. Corrigé en réutilisant
  `DossierFieldValidator` (`domain.dossier.model`, déjà utilisé par
  `UpdateDossierFieldsUseCaseImpl`/`CheckDossierTypeSchemaCompatibilityUseCaseImpl`) au lieu d'une
  validation « maison » partielle — couvre `required`, conformité de type
  (`STRING`/`NUMBER`/`DATE`/`BOOLEAN`/`SELECT`) et options `SELECT` en un seul appel
  (`fieldValidator.validate(schema.fields(), submitted, allowUnknownFields=true)`), sans classe
  d'erreur ni logique dupliquée. Nouveau test de régression
  (`complete_withUserQuestionFormKey_wrongTypeForNumberField_rejects`,
  `CompleteTaskUseCaseImplTest`). `./mvnw verify` re-exécuté après correctif : 1588 tests
  (0 échec), `UserQuestionFormSmokeIT`/`TaskQuestionSchemaApiTest` re-confirmées vertes.

## Déviations réelles par rapport au document design

- **Component breakdown simplifié** : le design listait 5 use cases distincts
  (`Create/Update/Delete/List/GetTaskQuestionSchemaUseCase`) — implémenté avec un seul
  `TaskQuestionSchemaUseCaseImpl` regroupant Save/Get/Delete (`save()` sert indifféremment
  création et mise à jour), pattern `WorkflowTypeConfigUseCaseImpl` déjà établi dans le même
  package plutôt que le pattern `DossierType` (5 use cases séparés) initialement copié dans le
  design sans vérifier le précédent le plus proche.
- **`FieldDefinitionDefsEditor.vue` non forké** : le design évoquait « `FILE` retiré des types
  proposés » côté admin ; implémenté sans modifier ce composant partagé (risque de régression sur
  `DossierTypesAdminView` pour un gain UX mineur) — `FILE` reste au menu déroulant, rejeté
  serveur avec message explicite (`TaskQuestionSchema` constructor), testé
  (`TaskQuestionSchemaTest.constructor_rejectsFileFieldType`).
- **Props du registre** (`taskFormRegistry.ts`) étendues : le contrat documenté par TODO §52 ne
  passait que `dossierId` à tout composant résolu. `user-question` a aussi besoin de `taskId`
  (D-USERQ-RUNTIME-SCOPE) et de `formKey` (pour en extraire le `schemaKey` côté client) — ajoutés
  comme props supplémentaires sur les 2 sites d'appel (`MobileTasksView.vue`, `TaskList.vue`),
  fallthrough sans effet sur `DossierFieldsTaskForm`/`DocumentGenerationTaskForm` (props non
  déclarées, ignorées).
- **Pas de test de rollback transactionnel dédié** : couvert indirectement par
  `UserQuestionFormSmokeIT` (chemin nominal réel) et `CompleteTaskUseCaseImplTest` (validations
  avant écriture) ; un test explicite « `completeTask` échoue après l'écriture de
  `TaskQuestionAnswer`, la transaction annule les deux » nécessiterait un mock partiel du moteur
  Flowable dans un contexte Spring réel, jugé hors périmètre pour cette itération (risque documenté,
  pas silencieusement ignoré).

## Security Notes

- Validation serveur systématique à la complétion (jamais confiance au frontend seul), même
  standard que `assertDocumentTypeAllowed`.
- Endpoint runtime scopé par `taskId` (D-USERQ-RUNTIME-SCOPE) — empêche un utilisateur authentifié
  quelconque de lire un schéma par un `schemaKey` deviné sans détenir la tâche correspondante.
- RBAC admin vérifié contre la **vraie** chaîne Spring Security (`TaskQuestionSchemaApiTest`, pas
  `standaloneSetup`) — leçon retenue de `connecteur-callback` F0 (TODO §51), une route peut être
  inatteignable en production malgré des tests verts qui contournent la sécurité réelle.
- **Le schéma est une vraie liste blanche des variables de process, pas juste un contrôle des
  champs qu'il reconnaît** (trouvaille adversariale post-Phase-9, voir section dédiée ci-dessous) :
  toute clé absente du schéma est rejetée en 400, jamais transmise au moteur ni silencieusement
  ignorée.

## Écart réel trouvé et corrigé après Phase 9 (analyse adversariale demandée par l'utilisateur)

- **Schéma non réellement contraignant sur les variables de process** :
  `assertAndRecordUserQuestionAnswers` appelait `fieldValidator.validate(..., allowUnknownFields=
  true)` codé en dur, et `complete(...)` transmettait ensuite les `variables` **brutes** (pas la
  map `answers` filtrée aux champs du schéma) à `workflowEnginePort.completeTask(...)`. Un
  détenteur de tâche authentifié pouvait donc soumettre, en plus des champs déclarés par le
  schéma, n'importe quelle autre clé (ex. `decision`) — ni validée ni filtrée — qui atteignait
  quand même le moteur comme variable de process, capable de piloter une passerelle exclusive
  ailleurs dans le workflow, **sans jamais apparaître dans la trace `TaskQuestionAnswer`** dont
  c'est pourtant l'unique raison d'être. Contraire à la promesse même de la feature (un schéma
  admin borne les choix possibles indépendamment du dossier). Démontrable directement avec la
  fixture fournie (`user-question-smoke.bpmn20.xml`, passerelle sur `${decision}`) si le schéma de
  la tâche ne déclare pas ce champ. Trouvé par un subagent dédié à une analyse adversariale
  (demande explicite de l'utilisateur, hors contrat des skills `dev-lifecycle`), vérifié
  manuellement avant correction plutôt que pris pour argent comptant. Corrigé :
  `allowUnknownFields=false` (rejet en 400 de toute clé hors schéma) et `complete(...)` transmet
  désormais la map `answers` schéma-filtrée — jamais `variables` brute — au moteur pour ce kind de
  tâche. Test de régression :
  `complete_withUserQuestionFormKey_variableNotDeclaredByTheSchema_rejectsAndNeverReachesTheEngine`
  (`CompleteTaskUseCaseImplTest`).

## Vérification finale

- `./mvnw clean verify` : **1592 tests**, 0 échec, ArchUnit conforme (dont
  `usecase_impl_naming_convention`), couverture Jacoco tenue (« All coverage checks have been met »)
  — après les correctifs Phase 7 (+1), Phase 8 (+3) et l'analyse adversariale post-Phase-9 (+1) sur
  la base des 1587 de la Phase 6.
- `UserQuestionFormSmokeIT` (test d'intégration réel contre Flowable 8.0.0, fixture
  `user-question-smoke.bpmn20.xml`) : la passerelle exclusive lit effectivement la variable
  produite par la complétion — pas seulement émise.
- Frontend : Vitest **289/289** (269 existants + 20 nouveaux dont 2 ajoutés en Phase 8, 0
  régression), `vue-tsc` et `eslint` propres, Playwright **65/65** (63 existants + 2 nouveaux
  `E2E-USERQ-*` depuis la Phase 5, 0 régression, inchangé en Phase 8).
- `GUIDE-UTILISATEUR.md` (§0 rôles + nouvelle §10), `GUIDE-CONCEPTION-BPMN.md` (§3bis étendu) mis
  à jour. `CHANGELOG.md` différé (aucune PR ouverte à ce stade, convention du dépôt).
