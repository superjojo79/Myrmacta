---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy

> Feature `questions-utilisateur-taches`. Scénarios dérivés des critères de succès du
> [document requirements](../requirements/2026-09-05-feature-questions-utilisateur-taches.md) et
> des composants du [document design](../design/2026-09-05-feature-questions-utilisateur-taches.md).

## Test Coverage Goals

- Backend : couverture Jacoco LINE/BRANCH tenue sur tout le code nouveau (même standard que TODO
  §52 : 1572 tests, 0 échec, Jacoco tenu).
- Frontend : Vitest sur chaque nouveau composant/écran, Playwright pour au moins un scénario
  `E2E-USERQ-*` de bout en bout (création schéma admin → complétion tâche → variable de process
  lue par la passerelle en aval), zéro régression sur les `E2E-FORM-*` existants (TODO §52).
- Alignement critères de succès : chaque ligne du tableau « Success Criteria » du document
  requirements doit être couverte par au moins un test ci-dessous.

## Unit Tests

### `TaskQuestionSchema` / validation (domaine)

- [x] Rejette la création si un `FieldDefinition.name` collisionne avec une clé réservée
  (`WorkflowVariables.RESERVED`) — D-USERQ-VAR-COLLISION (`TaskQuestionSchemaTest`).
- [x] Rejette la création si un `FieldDefinition.name` collisionne avec un nom de variable métier
  connu (`documentType`) (`TaskQuestionSchemaTest`).
- [x] Rejette un `FieldType.FILE` dans un schéma de ce type (`TaskQuestionSchemaTest`) — **exclu de
  l'UI non fait** : décision de planification (M6) de ne pas forker `FieldDefinitionDefsEditor.vue`
  pour ce seul gain UX, voir doc implementation § Déviations.
- [x] Rejette un `schemaKey` dupliqué pour le même tenant — trouvé non testé en Phase 8
  (balayage de couverture), ajouté (`TaskQuestionSchemaApiTest.create_withDuplicateSchemaKeyForTheSameTenant_returns409`,
  confirme la contrainte unique DB + `DataIntegrityViolationException` → 409, log Hibernate
  `23505` observé en sortie de test — preuve que c'est bien la contrainte DB qui rejette, pas une
  garde applicative).
- [x] Accepte un schéma valide (`TaskQuestionSchemaTest.constructor_acceptsValidSchema`, type
  `SELECT` ; les autres types sont couverts transitivement par `FieldDefinition`/`FieldType`
  eux-mêmes, pas re-testés par type ici).

### `CompleteTaskUseCaseImpl` — nouvelle validation `user-question`

- [x] Complétion réussie : toutes les variables requises par le schéma sont présentes et valides
  → tâche complétée, `TaskQuestionAnswer` enregistré (`CompleteTaskUseCaseImplTest`).
- [x] Rejet : un champ `required` du schéma est absent des variables soumises.
- [x] Rejet : une valeur de champ `SELECT` n'appartient pas à `options`.
- [x] Rejet : une valeur de champ `NUMBER` n'est pas conforme au type (`TYPE_MISMATCH`) — trouvé
  manquant en revue Phase 7 (la validation initiale ne couvrait que `required`/`SELECT`), corrigé
  par réutilisation de `DossierFieldValidator` au lieu d'une validation partielle « maison ».
- [x] Rejet : le `schemaKey` référencé par la tâche n'existe plus (`UnknownTaskQuestionSchemaException`).
- [x] Non-régression : une tâche portant un `formKey` d'un autre kind n'est pas affectée.
- [x] `TaskQuestionAnswer.submittedBy` correspond bien à l'identité transmise par le contrôleur.
- [x] Non-régression : `assertDocumentTypeAllowed` toujours vert après l'ajout du paramètre.
- [x] `assertAndRecordUserQuestionAnswers` résout le `schemaKey` via
  `WorkflowEnginePort.findFormKeyByTaskId(taskId)` (tous les tests ci-dessus stubbent ce port,
  jamais une variable soumise par le client).
- [x] `GetTaskQuestionSchemaForTaskUseCase` : rejette si `findFormKeyByTaskId(taskId)` ne
  correspond pas au `schemaKey` demandé (`GetTaskQuestionSchemaForTaskUseCaseImplTest`).
- [x] **Ajouté en Phase 8** : rejette si le `formKey` correspond mais que le schéma a été
  supprimé entre-temps (`getForTask_rejects_whenFormKeyMatchesButSchemaWasDeleted`) — branche
  trouvée non couverte au balayage Jacoco.
- [x] **Ajouté en Phase 8** : `PUT` sur un `schemaKey` inconnu → 404
  (`TaskQuestionSchemaApiTest.update_unknownSchemaKey_returns404`) — branche trouvée non couverte.

### Frontend — `UserQuestionTaskForm.vue`

- [x] Charge le schéma via `GET .../runtime` et rend un champ par entrée de `fields`.
- [x] `emit('ready', variables)` avec une clé par `FieldDefinition.name` (`{decision: 'approve'}` testé).
- [ ] N'appelle jamais `PATCH /api/dossiers/{id}/fields` — non testé par un spy explicite (le
  composant n'importe simplement pas ce module, garantie structurelle par absence d'import, pas
  prouvée par un test qui échouerait si l'import réapparaissait).
- [x] Bouton de complétion désactivé tant qu'un champ `required` est vide.
- [x] Gestion de l'erreur de chargement (schéma introuvable / réseau) — `emit('error', message)`
  (testé via le message affiché, pas l'event émis directement, cohérent avec le pattern existant).

### Frontend — `taskFormRegistry.ts`

- [x] Résout `formKey = "user-question:<schemaKey>"` (n'importe quel `schemaKey`) vers
  `UserQuestionTaskForm`.
- [x] Non-régression : `dossier-fields`/`document-generation`/clé inconnue/clés `Object.prototype`
  toujours résolus correctement (tests existants inchangés, toujours verts).

### Frontend — écran admin `TaskQuestionSchemasAdminView.vue`

- [x] Liste, crée, modifie, supprime un schéma (réutilise `FieldDefinitionDefsEditor.vue`).
- [ ] `FieldType.FILE` absent des types proposés — **non fait**, décision de planification (voir
  doc implementation § Déviations) : rejeté serveur (`TaskQuestionSchemaTest`), pas caché côté UI.
- [x] Affiche l'erreur serveur si un nom de champ collisionne / si l'édition échoue — **ajouté en
  Phase 8** (`shows the server error message when creating a schema with a colliding field name`,
  `... when editing a schema fails`, `TaskQuestionSchemasAdminView.spec.ts`).

## Integration Tests

- [x] `TaskQuestionSchemaRepository`/JPA : round-trip complet (création, lecture, mise à jour,
  suppression) — exercé via `TaskQuestionSchemaApiTest` (pas un test JPA isolé dédié, mais le vrai
  chemin bout en bout controller→use case→repository→JPA→H2).
- [x] `TaskQuestionAnswerRepository`/JPA : écriture confirmée (`UserQuestionFormSmokeIT`). Pas de
  méthode de lecture — **`TaskQuestionAnswerJpaEntity.toDomain()` supprimé en Phase 8** (balayage
  Jacoco) : la méthode était du code mort (0 appelant, `TaskQuestionAnswerRepository` est
  volontairement write-only, D-USERQ-TRACE-SCOPE) ; pas de test écrit pour un code sans appelant
  réel plutôt que de fabriquer un appel artificiel. À rajouter avec le premier vrai lecteur.
- [x] `TaskQuestionSchemaController` : CRUD admin + RBAC vérifié contre la vraie chaîne Spring
  Security (`TaskQuestionSchemaApiTest`, pas `standaloneSetup`).
- [x] Endpoint runtime : accessible à tout rôle porteur de tâche, ne renvoie jamais
  `createdBy`/`updatedBy` (`jsonPath("$.createdBy").doesNotExist()`).
- [x] Endpoint runtime : rejette (404) un `taskId` dont le `formKey` ne correspond pas au
  `schemaKey` demandé (D-USERQ-RUNTIME-SCOPE).
- [x] Test d'intégration réel contre un process Flowable de fixture (`UserQuestionFormSmokeIT`,
  fixture `user-question-smoke.bpmn20.xml`) — confirme `Task.getFormKey()` lisible côté serveur.
- [ ] Transaction : rollback orphelin non testé explicitement — voir doc implementation §
  Déviations (nécessiterait un mock partiel du moteur Flowable dans un contexte Spring réel, jugé
  hors périmètre pour cette itération).

## End-to-End Tests

- [x] `E2E-USERQ-1` : une `userTask` déclarant `formKey = user-question:<schemaKey>` affiche le
  formulaire (mock du endpoint runtime) et complète avec les bonnes variables — **scindé** en 2
  scénarios Playwright indépendants plutôt qu'un seul chaîné (voir `E2E-USERQ-2` ci-dessous) :
  meilleure isolation, chaque échec pointe directement vers la bonne moitié.
- [x] `E2E-USERQ-2` : un admin crée un schéma via l'écran `/admin/task-question-schemas`, il
  apparaît dans la liste.
- **Correction de portée** : la preuve que la variable de process est bien **lue** par une
  passerelle exclusive en aval (pas seulement émise — leçon TODO §52 Phase 9) ne peut pas être
  apportée par Playwright ici (E2E frontend, backend entièrement mocké par `page.route`, jamais de
  vrai moteur Flowable) — c'est `UserQuestionFormSmokeIT` (Integration Tests, réel Flowable) qui
  apporte cette preuve, pas un test Playwright. Le document testing d'origine confondait les deux
  niveaux ; corrigé ici.
- [x] Régression : `E2E-FORM-*` existants toujours verts — **65/65** au total (63 existants + 2
  nouveaux), zéro régression.

## Test Data

- Fixture BPMN `user-question-smoke.bpmn20.xml` : `userTask` portant
  `formKey = user-question:orientation-dossier` suivi d'une passerelle exclusive (`${decision ==
  'approve'}` / `${decision == 'reject'}`) — même famille que les fixtures `*-smoke.bpmn20.xml`.
- Schéma de test type : `{ schemaKey: "orientation-dossier", fields: [{name: "decision", type:
  SELECT, options: ["approve", "reject"], required: true}] }`.

## Test Reporting & Coverage

- **Balayage Jacoco fichier par fichier fait en Phase 8** (rapport HTML régénéré après un `mvn
  clean test` complet) — comble le point M9.3 laissé ouvert en Phase 7. Écarts réels trouvés et
  corrigés : `GetTaskQuestionSchemaForTaskUseCaseImpl` (branche « formKey correspond mais schéma
  supprimé » jamais exercée), `TaskQuestionSchemaController` (branche `update()` sur `schemaKey`
  inconnu jamais exercée), `TaskQuestionFormKeys.formKeyFor` (jamais appelé en dehors de sa propre
  définition — remplacé les littéraux `"user-question:orientation-dossier"` codés en dur dans 3
  fichiers de test par des appels à cette méthode, à la fois plus sûr et lui donnant une vraie
  couverture), `TaskQuestionAnswerJpaEntity.toDomain()` (code mort, supprimé plutôt que testé
  artificiellement). Écarts restants, à faible valeur de test et non corrigés : deux branches
  `null` vs `blank`/`omis` dans des méthodes privées utilitaires du contrôleur
  (`required(...)`, `listOrEmpty(...)`) — même comportement visible des deux côtés, jugé sans
  valeur de test supplémentaire. `TaskQuestionAnswerJpaEntity`/`JpaTaskQuestionAnswerRepositoryAdapter`
  affichent une couverture en apparence faible sous `mvn test` seul (`*IT.java` exclu par
  convention du dépôt) — cohérent avec le reste du dépôt (ex. `FlowableWorkflowEngineAdapter
  .correlateMessage`, couvert principalement par `CallbackSmokeIT`), la preuve fonctionnelle réelle
  vient de `UserQuestionFormSmokeIT`, pas de la mesure Jacoco isolée du run par défaut.
- **Trouvaille adversariale post-Phase-9 (subagent dédié, demande explicite utilisateur)** :
  `assertAndRecordUserQuestionAnswers` codait en dur `allowUnknownFields=true` et
  `complete(...)` transmettait les `variables` brutes (pas la map `answers` filtrée) au moteur —
  toute clé hors schéma passait donc sans validation ni trace. Corrigé (`allowUnknownFields=false`
  + transmission de `answers` filtrée au lieu de `variables`) ; test de régression
  `complete_withUserQuestionFormKey_variableNotDeclaredByTheSchema_rejectsAndNeverReachesTheEngine`
  ajouté (`CompleteTaskUseCaseImplTest`, 13 tests désormais).
- `./mvnw verify` (après `clean`) : **1592 tests**, 0 échec, ArchUnit conforme, « All coverage
  checks have been met » (Jacoco LINE ≥ 85 % / BRANCH ≥ 75 % bundle).
- Frontend : Vitest **289/289** (269 existants + 20 nouveaux), `vue-tsc -b` et `eslint .` propres,
  Playwright **65/65** (63 existants + 2 nouveaux, `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium
  npx playwright test`, exit 0).
- `UserQuestionFormSmokeIT` exécutée séparément (`-Dtest=UserQuestionFormSmokeIT`, convention du
  dépôt — pas de plugin Failsafe, `*IT.java` exclu du run par défaut) : 1/1, 0 échec.

## Manual Testing

- Vérification visuelle de l'écran admin (types de champs, message d'erreur de collision).
- Parcours manuel : création schéma → édition BPMN (`formKey`) → complétion tâche en tant que
  porteur non-admin.

## Performance Testing

- Hors périmètre — volumétrie comparable à `document-generation`, pas de nouveau risque de
  performance identifié.

## Bug Tracking

- Même process que les features précédentes : tout écart réel trouvé pendant l'implémentation ou
  la revue est documenté dans `docs/ai/implementation/2026-09-05-feature-questions-utilisateur-taches.md`
  et dans `TODO.md` §55, jamais corrigé silencieusement sans trace.
