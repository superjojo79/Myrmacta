# §55 — Nouveau kind de tâche générique — questions/choix utilisateur indépendants du dossier

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §55 · [registre](README.md)

**Statut (2026-09-05) : clos.** Cycle `dev-lifecycle` Phases 1–9 complet,
`questions-utilisateur-taches`, milestones M1–M9 enchaînés en TDD sur demande explicite. Prolonge
le mécanisme `formKey`/`taskFormRegistry.ts` livré par TODO §52 avec un 3ᵉ kind générique
`user-question:<schemaKey>` : un concepteur de workflow définit un mini-formulaire (un ou
plusieurs champs texte/nombre/date/choix, réutilise `FieldDefinition`/`FieldType` tel quel) dans un
nouvel écran admin (**Admin → Questions de tâche**), sans qu'un développeur code un composant
dédié. La réponse part uniquement comme variables de process (nom = `FieldDefinition.name`),
**jamais écrite dans le dossier**, et reste tracée (`TaskQuestionAnswer`).

**Décisions tranchées par l'utilisateur au lancement du cycle** (2 séries de questions en
requirements, revues en Phase 2/3) : réutilisation de `FieldDefinition`/`FieldType`
(dépendance `domain.workflow` → `domain.dossier.model` déjà existante et assumée) ; garde-fou de
collision de noms de variables validé **à la sauvegarde du schéma** (`WorkflowVariables
.isReservedOrKnownKey`, nouvelle méthode) ; traçabilité à portée minimale (réponses brutes, pas de
lien reporting/SLA) ; schéma **non versionné** (modification directe, suppression dégrade comme un
`formKey` inconnu) ; RBAC `TENANT_ADMIN`+`WORKFLOW_ADMIN` (cohérent avec la conception BPMN, pas
`TENANT_ADMIN` seul comme `DossierType`) ; pas d'i18n.

**2 gaps réels trouvés en revue Phase 2 (requirements)**, non anticipés par l'analyse initiale :
`CompleteTaskUseCase.complete(...)` (port existant) ne transmettait aucune identité d'appelant
alors que le contrôleur dispose déjà d'`Authentication` — la signature a évolué (`actorUserId`)
pour renseigner `TaskQuestionAnswer.submittedBy` (D-USERQ-SUBMITTED-BY). L'endpoint runtime de
lecture d'un schéma n'a, par construction, aucun `DossierType`/permission `VIEW` à vérifier —
corrigé en exigeant `taskId` et en vérifiant que le `formKey` de cette tâche référence bien le
`schemaKey` demandé (D-USERQ-RUNTIME-SCOPE).

**1 vrai compromis d'architecture trouvé et tranché en revue Phase 3 (design)**, seul point
explicitement soumis à confirmation (les 2 autres écarts de cette phase — alignement de la
concurrence sur `DossierType` plutôt que `Dossier.fields()`, scission d'un use case couvrant en
réalité 2 endpoints aux autorisations différentes — ont été corrigés directement, l'utilisateur
ayant relevé après coup que je ne l'avais pas signalé assez clairement) : la validation serveur du
kind doit résoudre le `schemaKey` depuis `Task.getFormKey()` lu **côté serveur**
(`WorkflowEnginePort.findFormKeyByTaskId`, nouveau), jamais depuis une variable soumise par le
client — sinon réintroduction de la faille D-TASK-FORM-DOCTYPE-SCOPE déjà corrigée sur
`document-generation` (TODO §52 Phase 9). Accepté comme exception **documentée et bornée** au
principe « `formKey` opaque côté backend » (`TaskQuestionFormKeys`, source de vérité unique
partagée avec son miroir TypeScript).

**Écarts réels trouvés en implémentation (Phase 5), corrigés ou documentés sans les cacher** :
`WorkflowTaskController.complete()` devient `@Transactional` (précédent : `DossierExportController`)
pour que l'écriture de `TaskQuestionAnswer` et `completeTask(...)` roulent back ensemble.
Simplification assumée du component breakdown : un seul `TaskQuestionSchemaUseCaseImpl`
(Save/Get/Delete) au lieu des 5 use cases séparés du design initial — pattern
`WorkflowTypeConfigUseCaseImpl` déjà établi, RBAC entièrement au contrôleur, aucun garde domaine.
`DynamicFields.vue` n'avait déjà aucun binding dossier (le design supposait à tort qu'il fallait
« en retirer » un binding). `FieldDefinitionDefsEditor.vue` réutilisé sans fork (`FieldType.FILE`
reste proposé côté UI, rejeté côté serveur avec message explicite) pour ne pas risquer de
régression sur `DossierTypesAdminView` pour un seul gain UX. `taskFormRegistry.ts` ne passait que
`dossierId` à tout composant résolu — `taskId`/`formKey` ajoutés comme props sur les 2 sites
d'appel (`MobileTasksView.vue`, `TaskList.vue`), nécessaires à l'endpoint runtime scopé par tâche.

**Non fait, documenté comme risque assumé plutôt que silencieusement ignoré** : pas de test de
rollback transactionnel dédié (nécessiterait un mock partiel du moteur Flowable) ; pas de garde
applicative dédiée au `schemaKey` dupliqué (repose sur la contrainte unique DB, comme
`DossierType.code`).

Prouvé contre le **vrai** moteur Flowable (`UserQuestionFormSmokeIT`, fixture
`user-question-smoke.bpmn20.xml`) : la passerelle exclusive en aval lit effectivement la variable
produite par la complétion, pas seulement émise (même classe de bug que celle trouvée en Phase 9
de TODO §52). RBAC et garde-fou de portée runtime vérifiés contre la **vraie** chaîne Spring
Security (`TaskQuestionSchemaApiTest`, pas `standaloneSetup`).

**Écart réel trouvé et corrigé en Phase 7 (revue d'implémentation)** : la validation serveur des
réponses (`assertAndRecordUserQuestionAnswers`) ne vérifiait initialement que `required` et les
`options` d'un champ `SELECT` — un champ `NUMBER`/`DATE`/`BOOLEAN` acceptait silencieusement une
valeur non conforme au type. Corrigé en réutilisant `DossierFieldValidator`
(`domain.dossier.model`, déjà utilisé pour les champs dossier) au lieu d'une validation « maison »
partielle, sans nouvelle classe. Test de régression ajouté.

**Écarts réels trouvés et corrigés en Phase 8 (write tests, balayage Jacoco classe par classe,
pas seulement à l'agrégat)** : 4 trouvailles. (1) `GetTaskQuestionSchemaForTaskUseCaseImpl` —
la branche « le `formKey` référence bien un `schemaKey` connu mais le schéma a été supprimé
entre-temps » n'était pas couverte ; test `getForTask_rejects_whenFormKeyMatchesButSchemaWasDeleted`
ajouté. (2) `TaskQuestionSchemaController.update` — la branche 404 (`schemaKey` inconnu) n'était
pas couverte ; test `update_unknownSchemaKey_returns404` ajouté (au passage,
`create_withDuplicateSchemaKeyForTheSameTenant_returns409` ajouté aussi pour la même raison sur la
contrainte unique DB). (3) `TaskQuestionFormKeys.formKeyFor` était du code mort côté production
(seul `schemaKeyOf` avait des appelants réels) : plutôt que de le supprimer, 3 fichiers de test
(`CompleteTaskUseCaseImplTest`, `GetTaskQuestionSchemaForTaskUseCaseImplTest`,
`UserQuestionFormSmokeIT`) ont été corrigés pour l'appeler au lieu de dupliquer le littéral
`"user-question:orientation-dossier"` en dur — la méthode a désormais de vrais appelants et le
risque de dérive entre préfixe et littéraux de test disparaît. (4) `TaskQuestionAnswerJpaEntity
.toDomain()` : 0% de couverture confirmée (aucun appelant, `TaskQuestionAnswerRepository` est
volontairement write-only — D-USERQ-TRACE-SCOPE). Supprimée plutôt que couverte par un test
artificiel, avec un commentaire expliquant pourquoi et quand la réintroduire (cohérent avec le YAGNI
déjà appliqué ailleurs dans ce dépôt plutôt que du code mort gardé « au cas où »). Deux tests
d'erreur serveur ajoutés côté frontend (`TaskQuestionSchemasAdminView.spec.ts`) pour la même
raison (branches d'erreur de `create`/`update` non exercées).

`./mvnw verify` : 1591 tests, 0 échec, ArchUnit conforme, couverture Jacoco tenue (« All coverage
checks have been met »). `UserQuestionFormSmokeIT` exécutée séparément (`*IT.java` exclu par
défaut, convention du dépôt). Vitest 289/289 (287 existants + 2 nouveaux, 0 régression),
`vue-tsc`/`eslint` propres, Playwright 65/65 (inchangé, aucun scénario E2E supplémentaire en Phase
8). `docs/guides/GUIDE-UTILISATEUR.md` (§0 + nouvelle §10), `docs/guides/GUIDE-CONCEPTION-BPMN.md` (§3bis étendu) mis à
jour. `CHANGELOG.md` différé (aucune PR ouverte à ce stade, même convention que TODO §47).
Détail complet : [`docs/ai/implementation/2026-09-05-feature-questions-utilisateur-taches.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-05-feature-questions-utilisateur-taches.md),
[`docs/ai/testing/2026-09-05-feature-questions-utilisateur-taches.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-09-05-feature-questions-utilisateur-taches.md).

**Bug réel trouvé et corrigé après Phase 9, via un subagent dédié à une analyse adversariale
demandée explicitement par l'utilisateur (pas dans le contrat des skills `dev-lifecycle`)** :
`CompleteTaskUseCaseImpl.assertAndRecordUserQuestionAnswers` appelait
`fieldValidator.validate(schema.fields(), submitted, allowUnknownFields=true)` — codé en dur — et
`complete(...)` transmettait ensuite les `variables` **brutes** (pas la map `answers` filtrée aux
champs du schéma) à `workflowEnginePort.completeTask(...)`. Résultat : un détenteur de tâche
authentifié pouvait soumettre, en plus des champs déclarés par le schéma, n'importe quelle autre
clé (`decision`, etc.) — jamais validée, jamais filtrée — qui atteignait quand même le moteur
Flowable comme variable de process, capable de piloter une passerelle exclusive ailleurs dans le
workflow, **sans jamais apparaître dans la trace `TaskQuestionAnswer`** dont c'est pourtant
l'unique raison d'être. Cela allait à l'encontre de la promesse même de la feature (un schéma
admin borne les choix possibles indépendamment du dossier). Corrigé : `allowUnknownFields=false`
(toute clé hors schéma est rejetée en 400) et `complete(...)` transmet désormais la map `answers`
schéma-filtrée (pas `variables` brute) au moteur pour ce kind de tâche — le schéma devient une
vraie liste blanche des variables de process qu'une tâche `user-question` peut produire, pas
seulement un contrôle sur les champs qu'il reconnaît. Test de régression ajouté
(`complete_withUserQuestionFormKey_variableNotDeclaredByTheSchema_rejectsAndNeverReachesTheEngine`).
`./mvnw verify` re-exécuté : **1592 tests**, 0 échec, Jacoco tenu ; `UserQuestionFormSmokeIT`
re-confirmée verte ; Vitest/Playwright inchangés (correctif backend seul).

**Détail :** [23-questions-utilisateur-taches.md](../specs/23-questions-utilisateur-taches.md).
