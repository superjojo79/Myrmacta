---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide — `formulaires-dynamiques-taches`

> Suivi vivant, mis à jour à chaque milestone (voir
> [planning](../planning/2026-09-03-feature-formulaires-dynamiques-taches.md)). TDD : chaque
> comportement listé ci-dessous a été rouge avant d'être vert.

## M0 — Spike de vérification technique (terminé, 2026-09-03)

**Résultat : hypothèse confirmée.** `Task.getFormKey()` est bien lisible sur ce dépôt (Flowable
8.0.0, `flowable-spring-boot-starter` seul, sans `flowable-form`), sur le vrai moteur (H2 en test,
pas mocké).

- Nouveau process `src/main/resources/processes/form-key-smoke.bpmn20.xml` (`formKeySmoke`),
  `userTask` `formKeySmokeTask` avec `flowable:formKey="dossier-fields"`.
- Nouveau test `FormKeySmokeIT` (`activeUserTask_exposesFlowableFormKey_withoutFlowableFormModule`) :
  rouge d'abord (`FlowableObjectNotFoundException: Process definition with key 'formKeySmoke' ...
  was not found`), vert après ajout du process BPMN.
- `./mvnw test -Dtest=FormKeySmokeIT -Dmaven.compiler.release=21` : 1 test, 0 échec.
- Le wrapper Maven de ce dépôt nécessite bien `-Dmaven.compiler.release=21` en environnement
  sandbox (JDK 21 installé, `pom.xml` cible Java 25) — confirmé conforme à la note déjà présente
  dans le skill `flowforge-conventions`.
- Conséquence pour M2 : aucun changement de design nécessaire, la plomberie `formKey` opaque
  peut être implémentée telle que prévue.

## M1 — `DossierType.documentTemplateKeys` (terminé, 2026-09-03)

- `DossierType` (domaine) : nouveau composant `documentTemplateKeys` (position après
  `slaTargetHours`, avant `createdBy`/`updatedBy`), copié défensivement (`List.copyOf`), défaut
  `List.of()` si `null`.
- **Choix d'implémentation (écart mineur vs plan initial, pattern déjà établi dans ce dépôt)** :
  plutôt que de mettre à jour les ~10 sites d'appel du constructeur canonique existants, ajout
  d'un **constructeur de convenance** (ancienne signature à 13 arguments, delègue avec
  `documentTemplateKeys = List.of()`) — exactement le patron déjà utilisé par `FieldDefinition`
  pour son champ `sensitive` (constructeur à 7 arguments déléguant vers celui à 8). Le seul appel
  qui a vraiment besoin du nouveau constructeur à 14 arguments (persistence, `CreateDossierTypeUseCaseImpl`)
  a été mis à jour explicitement.
- `withUpdate(...)` : **pas** de raccourci de convenance ici — nouveau paramètre `documentTemplateKeys`
  rendu obligatoire à dessein. Une commande de mise à jour représente l'état final voulu ; un
  défaut implicite à `List.of()` aurait silencieusement effacé une association déjà configurée
  chez un appelant utilisant l'ancienne forme (risque réel identifié en traçant
  `DefineDossierTypeTool.defineUpdate` — corrigé pour propager explicitement
  `existing.documentTemplateKeys()`, même patron que la préservation déjà en place pour
  `retentionDays`/`retentionNever`/`slaTargetHours`).
- `CreateDossierTypeUseCase`/`UpdateDossierTypeUseCase` (Command records) : même distinction —
  convenience constructor pour `Create` (sûr), champ obligatoire pour `Update` (6 sites d'appel
  dans `UpdateDossierTypeUseCaseImplTest` mis à jour explicitement + 1 nouveau test
  `update_persistsDocumentTemplateKeys`).
- `DossierTypeJpaEntity` : colonne `document_template_keys` (JSON, même mécanisme que
  `field_definitions`) ; migration Liquibase `019-dossier-type-document-template-keys.yaml`
  (patron `007-workflow-viz-columns.yaml` : `addColumn` nullable → backfill `'[]'` → `NOT NULL`).
- `DossierApiDtos`/`DossierTypeController` : `documentTemplateKeys` sur create/update/response,
  patron A-PATCH-INHERIT (`null` en `PATCH` = conserver l'existant, `[]` explicite = vider).
- Tests : `DossierTypeTest` (+4), `CreateDossierTypeUseCaseImplTest` (compile inchangé via
  convenience ctor), `UpdateDossierTypeUseCaseImplTest` (+1 test dédié, 6 sites migrés),
  `DossierPersistenceTest` (+1 round-trip réel H2), `DossierApiTest` (+4 tests REST, 82/82 verts
  au total sur la classe). `DefineDossierTypeToolTest`/`McpToolCallIntegrationTest`/
  `RetentionPersistenceIT`/`GetSlaSummaryUseCaseImplTest`/`GetSlaReportUseCaseImplTest`/
  `EvaluateDossierRetentionUseCaseImplTest`/`RetentionPolicyResolverTest`/`ListMyTasksUseCaseImplTest`
  — zéro régression (compilent et passent inchangés grâce au constructeur de convenance).
  `ArchitectureRulesTest`/`ArchitectureRulesFixtureTest` verts (domaine toujours pur).

## M2 — `formKey` de bout en bout côté backend (terminé, 2026-09-03)

- `WorkflowTaskInboxPort.ActiveTaskSnapshot`, `MyWorkflowTask`, `WorkflowTaskView` : chacun gagne un
  champ `formKey` nullable, avec constructeur de convenance (ancienne forme) pour ne pas casser les
  sites d'appel non liés à cette feature — même patron que M1.
- `FlowableWorkflowTaskInboxAdapter`/`FlowableWorkflowVisualizationAdapter` : lisent
  `task.getFormKey()` (API core `TaskService`/`Task`, confirmée par M0) et le propagent tel quel,
  sans transformation.
- `ListMyTasksUseCaseImpl` : propage `ActiveTaskSnapshot.formKey()` → `MyWorkflowTask.formKey()`
  sans logique additionnelle.
- `WorkflowTaskController.MyWorkflowTaskResponse` : `formKey` exposé sur `GET /api/workflow-tasks/mine`.
- `WorkflowLinkVisualizationController.tasks` : **aucun changement nécessaire** — `WorkflowTaskView`
  est déjà renvoyé directement (pas de DTO wrapper séparé), `formKey` apparaît automatiquement dès
  que le record domaine le porte (T2.5 du planning, confirmé par lecture du contrôleur).
- Tests : `FlowableWorkflowTaskInboxAdapterTest` (+2), `ListMyTasksUseCaseImplTest` (+1),
  `FlowableWorkflowVisualizationAdapterTest` (+1), `WorkflowApiTest` (+2 assertions bout-en-bout
  réelles sur `smokeProcess` — `formKey` présent avec valeur `null` sur une tâche sans
  `flowable:formKey`, preuve de non-régression). Tous verts, aucune régression sur
  `WorkflowLinkVisualizationUseCaseImplTest` (18/18, pass-through déjà couvert par équivalence
  d'objet).

## M3 — Endpoint `document-templates` filtré par type de dossier (terminé, 2026-09-03)

- **Écart réel trouvé en implémentation (affine le design)** : `GetDossierTypeUseCase.getByCode`
  appelle déjà `DossierAccessPolicy.assertCanViewType` — un utilisateur sans rôle admin ET sans
  entrée ACL `VIEW` explicite sur ce `DossierType` reçoit **403**, pas un accès libre. Le design
  disait « accessible sans rôle admin » sans préciser ce mécanisme. `ListDocumentTemplatesForDossierTypeUseCaseImpl`
  **réutilise `GetDossierTypeUseCase.getByCode(tenantId, code, ctx)`** comme unique porte d'entrée
  — hérite gratuitement de la même vérification `VIEW` que `GET /api/dossier-types/{code}`, sans
  dupliquer de logique d'autorisation. Confirmé par un test API réel : un utilisateur sans aucun
  rôle reçoit 403 tant qu'aucune entrée ACL `VIEW` n'est accordée (`grant(code, userId, "VIEW")`,
  mécanisme déjà existant), puis 200 une fois l'ACL posée — exactement le parcours réel d'un
  porteur de tâche `DOSSIER_MANAGER`.
- `DocumentTemplateRepository.findActiveByTemplateKeysIn(tenantId, templateKeys)` (nouveau port +
  impl JPA, requête dérivée Spring Data `findByTenantIdAndTemplateKeyInAndStatus`).
- `ListDocumentTemplatesForDossierTypeUseCase`/`Impl` (nouveau, `domain.document.usecase`) :
  liste vide → `List.of()` sans appel repository ; sinon délègue au port. Dépendance domaine
  `document` → domaine `dossier` (`AccessContext`/`DossierType`/`GetDossierTypeUseCase`) —
  vérifié licite par ArchUnit (cross-bounded-context autorisé entre modules `domain.*`, seule la
  direction infrastructure→domain est contrainte).
- `DossierTypeController.documentTemplates` (nouveau `GET /api/dossier-types/{code}/document-templates`) :
  DTO dédié `AllowedDocumentTemplateResponse(templateKey, variantKey, locale, label)` — **jamais**
  `htmlContent`/`status`/`version`, contrairement à `TemplateResponse` (admin). Vérifié par un test
  API qui inspecte l'absence des clés dans le JSON (`not(hasKey("htmlContent"))`), pas seulement
  l'absence de valeur.
- Bean Spring : `DocumentConfig.listDocumentTemplatesForDossierTypeUseCase` (dépend de
  `GetDossierTypeUseCase`, déjà bean via `DossierConfig`).
- Tests : `ListDocumentTemplatesForDossierTypeUseCaseImplTest` (4, nouveau),
  `DocumentTemplatePortsContractTest` (implémenteur anonyme mis à jour + 1 assertion),
  `DocumentTemplatePersistenceIT` (+2 tests réels : filtre `ACTIVE`, clés vides), `DossierApiTest`
  (+3 tests : contenu absent + accès non-admin avec ACL, liste vide, 404 type inconnu — 85/85
  verts sur la classe). `ArchitectureRulesTest`/`ArchitectureRulesFixtureTest` toujours verts.

## M4 — Éditeur BPMN : `flowable:formKey` (terminé, 2026-09-03)

- `flowablePropertiesProvider.ts` : nouveau groupe `userTaskGroup()` pour `is(element,
  'bpmn:UserTask')`, une entrée `flowable:formKey` (patron `textFieldEntry`, identique à
  `delegateExpression`/`expression`/`scope`). Commentaire d'en-tête du fichier mis à jour
  (`formKey` n'est plus hors scope, D-EDIT-SCOPE).
- Round-trip XML déjà prouvé par un test existant antérieur à cette feature
  (`preserves a flowable:* attribute not covered by the provider through an import/saveXML
  round-trip`, qui utilisait déjà `flowable:formKey` comme exemple d'attribut générique préservé
  par `bpmn-moddle`) — confirme que l'ajout d'une entrée dédiée ne casse rien côté sérialisation.
- Tests : `flowablePropertiesProvider.spec.ts` (+2 : présence du groupe sur `userTask` seulement,
  get/set round-trip incluant l'effacement → `undefined`). Suite Vitest complète du dépôt : 66
  fichiers, 249/249 tests verts (zéro régression).

## M5 — Frontend : registre de kinds + formulaires de tâche (terminé, 2026-09-03)

- `api/types.ts` : `MyWorkflowTask.formKey`, `WorkflowTaskView.formKey`, `DossierType.documentTemplateKeys`
  (tous optionnels, rétrocompatibles), nouveau type `AllowedDocumentTemplate`.
- `api/dossierTypes.ts` : `listDossierTypeDocumentTemplates(code)` ;
  `createDossierType`/`updateDossierType` payloads gagnent `documentTemplateKeys`.
- `taskFormRegistry.ts` (`components/workflow/task-forms/`) : `Record<string, Component>`,
  `resolveTaskFormComponent(formKey)` → `null` si absent/inconnu (dégradation décidée).
- `DossierFieldsTaskForm.vue` : **écart volontaire vs plan initial** — réutilise `DynamicFields.vue`
  directement plutôt que `DossierFieldsEditor.vue` (déjà existant sur la fiche dossier). Raison
  trouvée en implémentation : `DossierFieldsEditor` n'émet `save` que si le brouillon diffère des
  valeurs actuelles (comportement voulu sur la page dossier, où « rien à sauvegarder » est un
  no-op légitime) — mais compléter une tâche doit rester possible même sans modification. Le
  formulaire de tâche soumet donc toujours l'état courant complet des champs, pas un diff.
- `DocumentGenerationTaskForm.vue` : un seul `<select>` sur `AllowedDocumentTemplate.label` (pas 3
  champs séparés) — chaque ligne renvoyée par l'API est déjà la combinaison exacte
  `templateKey`/`variantKey`/`locale` à soumettre, inutile de les désassembler puis réassembler
  côté UI. Émet `ready({templateKey, variantKey, locale})` sans jamais appeler de endpoint de
  génération.
- `stores/tasks.ts`/`stores/dossier.ts` : `complete(taskId, variables = {})` — signature étendue de
  façon rétrocompatible (défaut `{}`, aucun appelant existant à modifier).
- `MobileTasksView.vue`/`TaskList.vue` : résolution via le registre ; composant de formulaire à la
  place du bouton nu quand `formKey` est connu, bouton `data-testid="complete-task"` sinon (non
  régression). `TaskList.vue` gagne un prop `dossierId` (la page dossier n'a qu'un seul dossier en
  contexte, contrairement à `MobileTasksView.vue` où chaque tâche porte le sien) et un événement
  `form-error` propagé jusqu'à `DossierDetailView.vue` (`actionError`, patron déjà en place pour
  les autres actions de la page).
- Tests : `taskFormRegistry.spec.ts` (4), `DossierFieldsTaskForm.spec.ts` (3),
  `DocumentGenerationTaskForm.spec.ts` (3), `MobileTasksView.spec.ts` (+2, dont non-régression
  bouton aveugle), `TaskList.spec.ts` (+1), `stores/tasks.spec.ts` (+1). Suite Vitest complète :
  69 fichiers, 263/263 verts. `npm run typecheck` et `npm run lint` propres.

## M6 — Frontend : administration `documentTemplateKeys` (terminé, 2026-09-03)

- `DocumentTemplateKeysEditor.vue` (`components/dossier/`) : liste à cocher, réutilise
  `GET /api/document-templates` (admin-scopé, existant, `api/admin.ts`) via `listAllPages` pour
  dédupliquer les `templateKey` `ACTIVE` — **pas** le nouvel endpoint M3
  (`/document-templates` sur `DossierTypeController`), réservé au picker non-admin du task-form.
- `DossierTypesAdminView.vue` : nouvelle section (`<fieldset>`) dans le formulaire de création et
  dans le panneau « Modifier les champs » existant — pas de nouvel écran, conforme à la décision
  design (réutilisation de l'écran Types). `documentTemplateKeys` propagé dans
  `createDossierType`/`updateDossierType`.
- Tests : `DocumentTemplateKeysEditor.spec.ts` (4, dédoublonnage + filtre `ACTIVE` + coché/décoché),
  `DossierTypesAdminView.spec.ts` (+1, wiring bout en bout création→édition→sauvegarde). Suite
  Vitest complète : 70 fichiers, 268/268 verts. `npm run typecheck`/`npm run lint` propres.

## M7 — Vérification globale, documentation, clôture (terminé, 2026-09-03)

- **Écart réel majeur trouvé en écrivant les tests Playwright** (pas en Phase 3/design — un
  E2E réel, pas les tests Vitest mockés au niveau `useService`) : le tout premier attribut
  `flowable:*` posé sur un diagramme qui ne déclare pas encore `xmlns:flowable` (dont le gabarit
  BPMN **vierge** de l'éditeur — `EMPTY_BPMN_XML`) round-trip correctement via
  `getValue()`/`setValue()` (confirmé en resélectionnant l'élément) mais est **silencieusement
  perdu par `saveXML()`** — aucune extension moddle n'est enregistrée pour l'espace de noms
  `flowable` dans cet éditeur (`moddle-xml` ne peut pas résoudre le préfixe pour un attribut ajouté
  dynamiquement au `$attrs` générique s'il n'a jamais vu son URI). Affecte potentiellement
  `async`/`delegateExpression`/`expression`/`scope` de la même façon, pas seulement `formKey` —
  latent depuis `edition-bpmn` (TODO §39), jamais détecté faute de test round-trip réel
  précédent. **Corrigé** : `EMPTY_BPMN_XML` déclare désormais `xmlns:flowable`
  (`BpmnEditorCanvas.vue`), couvre le cas le plus fréquent (diagramme démarré vierge dans cet
  éditeur, seul point d'entrée de création — pas de Flowable Modeler/Cockpit). Risque résiduel
  documenté (`GUIDE-CONCEPTION-BPMN.md` D-FLOWABLE-NS) pour un diagramme importé d'un outil externe
  n'ayant jamais utilisé d'attribut `flowable:*` — hors périmètre de cette feature, pré-existant.
- Playwright : `e2e/pilotage.spec.ts` (+4 tests — `E2E-FORM-1` à `4` — parcours porteur de tâche
  `dossier-fields`/`document-generation` réels bout en bout, parcours admin association templates,
  parcours éditeur BPMN formKey avec preuve XML réelle). `E2E-5` (existant) sert déjà de preuve de
  non-régression pour une tâche sans `formKey`. Vitest `BpmnEditorCanvas.spec.ts` +1 (garde de
  régression sur `xmlns:flowable` du gabarit vierge).
- **Correction Phase 9 (écart de vérification réel trouvé et corrigé)** : le chiffre « 1572 tests »
  initialement rapporté pendant la Phase 5/7 était contaminé par des rapports Surefire résiduels
  d'exécutions ciblées `-Dtest=SomeIT` faites plus tôt dans la session (Maven ne nettoie pas les
  rapports des classes non exécutées lors d'une invocation suivante). Découverte en Phase 9 en
  vérifiant l'intégrité du contrat OpenAPI : ce dépôt **n'a pas de plugin Failsafe** — les motifs
  d'inclusion par défaut de Surefire (`**/*Test.java`, `**/*Tests.java`, `**/Test*.java`,
  `**/*TestCase.java`) **excluent `*IT.java`**, confirmé empiriquement (`rm -rf
  target/surefire-reports` puis `./mvnw clean verify` isolé : zéro fichier de rapport `*IT.txt`
  généré). Chiffres exacts, mesurés isolément (pas d'exécution Maven concurrente) :
  - `./mvnw clean verify -Dmaven.compiler.release=21` (portée `Test*`/`*Test`/`*Tests` uniquement,
    254 classes) : **1561 tests, 0 échec, 0 erreur, 0 skip**, seuils Jacoco tenus.
  - `*IT.java` du périmètre de cette feature, exécutées explicitement en isolation
    (`FormKeySmokeIT`, `DocumentTemplatePersistenceIT`, `OpenApiContractIT`) : **9 tests, 0 échec**.
  - Cette lacune d'exécution (`*IT.java` jamais lancées par un `mvnw verify` « normal ») est
    pré-existante à cette feature (héritée de la configuration Maven du dépôt) et hors périmètre
    d'une correction ici ; signalée pour un futur ticket d'outillage (ajouter Failsafe).
  - En corollaire, cette même lacune a laissé passer un vrai écart : `openapi/openapi.yaml` (fichier
    de référence testé par `OpenApiContractIT`, jamais exécuté sous `mvnw verify`) n'avait pas été
    régénéré après l'ajout de l'endpoint et des champs de cette feature. `OpenApiContractIT` exécutée
    directement confirmait l'échec ; régénéré (`-Dopenapi.update=true`) puis revérifié vert — corrige
    aussi, incidemment, une dérive préexistante et non liée sur `/api/callbacks/{instanceId}`.
  - `npm run test` : 70 fichiers, 269/269. `npm run test:e2e` : 63/63 (59 existants + 4 nouveaux).
    `npm run typecheck`/`npm run lint` propres.
- Documentation : `GUIDE-CONCEPTION-BPMN.md` (nouvelle §3bis `flowable:formKey`, tableau des
  attributs éditables mis à jour, note D-FLOWABLE-NS) ; `GUIDE-UTILISATEUR.md` (§1 nouvelle section
  templates autorisés, §6 comportement de l'écran Tâches) ; `CHANGELOG.md` (entrée `[Non publié]`,
  PR à resynchroniser à l'ouverture). Aucune nouvelle variable d'environnement, aucun nouveau rôle
  — `.env.example`/`GETTING-STARTED.md` inchangés.

## M8 — Revue adversariale Phase 9 (terminé, 2026-09-03)

Après clôture de la Phase 9 (revue finale), un subagent dédié a mené une revue adversariale
approfondie du diff complet (74 fichiers) — pas une relecture polie, mais une recherche active
d'exploits et de scénarios de perte de données. Six écarts réels ont été trouvés et corrigés,
chacun avec un test de non-régression dédié :

- **C1 (Critique) — le kind `document-generation` était non fonctionnel de bout en bout.** Le
  formulaire émettait `templateKey`/`variantKey`/`locale` (les noms de champs du DTO
  `AllowedDocumentTemplateResponse`), alors que `DocumentGenerationTask.java` (le `serviceTask`
  `${documentGenerationTask}` suivant, volontairement laissé inchangé par le design) lit
  `documentType`/`documentVariant`/`documentLocale` via `execution.getVariable(...)`. Personne
  n'avait vérifié le contrat réel du delegate — tous les tests existants (unitaire du delegate,
  Vitest du formulaire, E2E Playwright) exerçaient chaque côté de la frontière isolément avec le
  même nom erroné des deux côtés, donc verrouillaient le bug au lieu de le révéler.
  **Corrigé** : `DocumentGenerationTaskForm.vue` émet désormais les bons noms ; `documentType` est
  devenu une constante partagée `WorkflowVariables.DOCUMENT_TYPE` utilisée à la fois par
  `DocumentGenerationTask.java` et par la nouvelle validation E4 (ci-dessous), pour qu'un futur
  renommage casse la compilation plutôt que de se reproduire silencieusement.
  `GUIDE-CONCEPTION-BPMN.md` §3bis corrigé (il se contredisait déjà lui-même entre son tableau des
  kinds et son tableau des variables `${documentGenerationTask}`). Nouveau fixture BPMN
  `document-generation-form-smoke.bpmn20.xml` + `DocumentGenerationFormSmokeIT` : fait tourner le
  moteur Flowable réel à travers les deux tâches (`userTask` complétée → `serviceTask` déclenché),
  seul niveau de test qui aurait pu révéler ce mismatch puisqu'il traverse la frontière au lieu de
  la mocker des deux côtés.
- **E2/E3 (Élevé) — `DossierFieldsTaskForm` soumettait l'état complet des champs, pas un diff.**
  Deux conséquences réelles : (a) un champ `NUMBER` touché était envoyé comme chaîne brute
  (`DynamicFields.vue` renvoie toujours `HTMLInputElement.value`), rejeté par
  `DossierFieldValidator.matchesType()` — la tâche devenait impossible à compléter dès qu'un type
  de dossier avait un champ numérique ; (b) un champ `sensitive` — retiré de la réponse par
  `DossierFieldRedactor` pour tout acteur non-`TENANT_ADMIN` — s'affichait comme un input vide, et
  le simple fait de cliquer dedans puis d'en ressortir (ou d'y taper puis d'effacer) envoyait une
  chaîne vide explicite dans le PATCH, effaçant silencieusement la vraie valeur que l'utilisateur
  n'avait jamais pu lire. `DossierFieldsEditor.vue` (l'écran d'édition normal) évite déjà ce dernier
  cas précis en construisant son PATCH via `diffDossierFields()`, qui traite « absent avant » et
  « vide après » comme « rien à envoyer ». **Corrigé** : `DossierFieldsTaskForm.vue` réutilise la
  même fonction pure pour construire son PATCH (garde un `snapshot` séparé du `draft` lié à
  `DynamicFields`), ce qui règle (b) directement et (a) en corollaire (`diffDossierFields` coerce
  déjà les champs `NUMBER` en `Number`). Complétion sans aucune modification : le patch est vide,
  aucun appel réseau n'est fait. 7 tests Vitest dédiés (`DossierFieldsTaskForm.spec.ts`), dont un
  qui reproduit exactement le scénario « tapé puis effacé » sur un champ redacté.
- **E4 (Élevé) — `documentTemplateKeys` n'était appliqué qu'à la découverte, jamais à l'usage.**
  `POST /api/workflow-tasks/{taskId}/complete` acceptait n'importe quelle valeur de `documentType`
  dans les variables de complétion, sans vérifier qu'elle appartient à
  `DossierType.documentTemplateKeys()` — rendant le filtrage annoncé dans le design (« jamais le
  catalogue complet à un rôle non admin ») purement décoratif dès qu'on regarde le chemin de
  complétion plutôt que celui de découverte. **Corrigé** : `CompleteTaskUseCaseImpl` reçoit
  désormais `dossierId` (déjà résolu par le contrôleur pour la vérification EDIT) et, quand
  `documentType` est présent dans les variables, résout le type du dossier
  (`DossierRepository`/`DossierTypeRepository`, injectés au même titre que dans
  `ListMyTasksUseCaseImpl`) et rejette (400) toute clé non explicitement autorisée. 6 tests
  unitaires (`CompleteTaskUseCaseImplTest`) + 1 test HTTP bout en bout
  (`WorkflowApiTest#complete_withDocumentTypeNotAllowedForTheDossierType_returns400AndNeverReachesTheEngine`)
  prouvant que la tâche reste active côté moteur quand la validation rejette.
- **M5 (Moyen) — `taskFormRegistry` vulnérable aux clés héritées d'`Object.prototype`.** Un
  `flowable:formKey` égal à `"toString"`, `"constructor"`, `"hasOwnProperty"`, `"valueOf"` ou
  `"__proto__"` résolvait à une valeur *truthy* via le lookup sur objet littéral, cassant le rendu
  de la liste de tâches (erreurs Vue ou disparition du bouton « Compléter ») au lieu de dégrader
  vers le comportement par défaut annoncé (D-FORM-BACKWARD-COMPAT). Pas de RCE ni de XSS possible
  (aucun `v-html`/`eval`/résolution dynamique de composant par nom sur ce chemin, vérifié). **Corrigé** :
  le registre est construit sur `Object.create(null)` (sans prototype), donc ces clés ne résolvent
  plus jamais à rien. 5 tests `it.each` dédiés (`taskFormRegistry.spec.ts`).
- **M7 (Moyen) — la version attendue n'était jamais rafraîchie après un PATCH réussi.** Une
  deuxième soumission du même formulaire de tâche (après un échec du `POST /complete` qui suit)
  renvoyait l'ancienne version, garantissant un 409. **Corrigé** : `snapshot`/`fields`/`version`
  sont mis à jour depuis le `Dossier` renvoyé par `updateDossierFields`, couvert par le test
  « refreshes its expected version from the PATCH response ».

Écarts de sévérité Faible identifiés mais **non corrigés** (hors du périmètre validé pour cette
clôture — voir TODO §52 pour le suivi) : pas de plafond de taille/longueur sur
`documentTemplateKeys` (F8) ; 404 vs 403 sur le nouvel endpoint pour un code de type inexistant vs
sans ACL, comportement déjà identique à `GET /api/dossier-types/{code}` donc non introduit ici
(F9) ; paramètre `requestTenantId` mort dans `ListDocumentTemplatesForDossierTypeUseCaseImpl` (F10) ;
absence de tri explicite sur `findActiveByTemplateKeysIn` (F11) ; une clé associée devenue
non-`ACTIVE` reste invisible et indécochable dans l'admin (F12) ; rollback destructif de la
migration 019 non documenté dans `DEPLOIEMENT.md` (F13).

Pistes explorées et confirmées **non exploitables** après lecture du code réel (résultats négatifs
utiles, pas de correctif nécessaire) : fuite inter-tenant via `findActiveByTemplateKeysIn`
(isolation par schéma structurelle) ; filtrage par statut `ACTIVE` (déjà correct et testé) ; XSS via
`formKey` (jamais rendue en texte/attribut, aucun `v-html`) ; TOCTOU sur `updateDossierFields`
(`expectedVersion` déjà protégé) ; contournement du contrôle d'accès au niveau champ côté serveur
(le formulaire de tâche appelle le même endpoint avec les mêmes gardes) ; autres call sites des
constructeurs de convenance (`DossierType`, `ActiveTaskSnapshot`, `MyWorkflowTask`,
`WorkflowTaskView`) — aucun ne recopie silencieusement une valeur existante.

Vérification finale après ces 6 correctifs : `./mvnw clean verify` 1566 tests (0 échec, seuils
Jacoco tenus) ; `*IT.java` du périmètre (`FormKeySmokeIT`, `DocumentGenerationFormSmokeIT`,
`DocumentTemplatePersistenceIT`, `OpenApiContractIT`) 13 tests (0 échec) ; Vitest 278/278 ;
Playwright 63/63 (1 échec isolé sur `E2E-EDIT-BPMN-3`, non lié — confirmé flaky, vert en isolation
répétée ×2, non touché par ce cycle).

## Security Notes

- Le nouvel endpoint `GET /api/dossier-types/{code}/document-templates` (M3) ne doit **jamais**
  exposer `htmlContent` — vérifié par un test API dédié qui inspecte le schéma JSON de réponse, pas
  seulement l'absence de valeur. Confirmé aussi que l'accès n'est pas inconditionnel : soumis à la
  même vérification `VIEW` (`DossierAccessPolicy`) que `GET /api/dossier-types/{code}` (403 sans
  ACL, testé en réel).
