# Formulaires dynamiques rattachés aux tâches BPMN (`userTask`)

> **Statut : première analyse fonctionnelle, aucune décision technique tranchée.** À affiner en
> phase `dev-lifecycle` (requirements → design) avant tout code — voir « Points ouverts » en fin de
> document. Origine : question utilisateur (2026-09-01) — un BPMN peut-il amener vers un formulaire
> dynamique (compléter les champs du dossier, déclencher une génération de documents) ? Réponse
> vérifiée contre le code réel : non aujourd'hui. Analyse demandée pour évaluer la faisabilité.
> Aucune feature ouverte à ce stade — cette analyse ne préjuge pas de la priorité dans la file
> d'attente.

---

## 1. Constat — le tuyau backend existe, rien ne s'en sert

Vérifié contre le code réel (pas supposé) :

| Élément | Fichier | État |
|---|---|---|
| Complétion de tâche avec variables | `WorkflowTaskController.complete`, `CompleteTaskUseCaseImpl` | `POST /api/workflow-tasks/{taskId}/complete` accepte déjà `{ variables: {...} }` — le tuyau backend existe |
| Bouton « Compléter » | `MobileTasksView.vue` | Appelle `store.complete(taskId)` **sans aucune variable** — aveugle, aucun formulaire construit |
| `formKey` sur `userTask` | `MyWorkflowTask`, `WorkflowTaskView`, éditeur BPMN (`flowablePropertiesProvider.ts`) | **Absent partout** — aucun moyen de déclarer qu'une tâche a besoin d'un formulaire, ni côté modèle domaine ni côté éditeur visuel |
| Génération de documents | `${documentGenerationTask}` (`AttachGeneratedDocumentTask`/serviceTask) | Entièrement automatique dans le process, lit des variables déjà présentes à l'exécution — jamais saisies via un formulaire à ce moment-là |
| Édition des champs dossier | `DossierFieldsEditor.vue`, `PATCH /api/dossiers/{id}/fields` | Existe, mais **indépendamment de tout `userTask`** — accessible depuis la fiche dossier à tout moment, pas routée par le BPMN |

Le module `flowable-form` (formulaires natifs Flowable, JSON de définition + rendu par l'UI
Flowable) n'est **pas** sur le classpath (`pom.xml` vérifié) — moteur headless, `flowable-spring-boot-starter`
seul. Toute solution doit être une convention Myrmacta, pas un mécanisme Flowable prêt à l'emploi.

---

## 2. Mécanisme proposé — `flowable:formKey` + convention de « kinds » fixes

### Le bon levier : `flowable:formKey`, attribut BPMN standard du moteur cœur

`flowable:formKey` est un attribut BPMN standard, lu via `Task.getFormKey()` sur le `TaskService`
du moteur **cœur** (`flowable-engine`) — **pas** dépendant du module optionnel `flowable-form`
absent du classpath. Le moteur ne fait que stocker/restituer cette chaîne opaque ; Myrmacta lui
donne son sens. Zéro nouveau couplage au moteur, aucune nouvelle dépendance Maven.

### Deux briques de rendu déjà réutilisables

- **`DynamicFields.vue`** — composant générique `definitions: FieldDefinition[]` + `v-model`, déjà
  utilisé pour l'édition des champs dossier (`DossierFieldsEditor.vue`). Réutilisable tel quel pour
  le cas « compléter les champs du dossier » : schéma déjà connu (`DossierType.fieldDefinitions`),
  aucun nouveau moteur de rendu à écrire.
- **`PATCH /api/dossiers/{id}/fields`** (`UpdateDossierFieldsUseCase`) — gère déjà permission
  (`accessPolicy`/`AccessContext.User`) et concurrence optimiste (`expectedVersion`). Un formulaire
  de champs dossier appelle cet endpoint **tel quel**, aucune logique dupliquée.

### Convention de « kinds » fixes, pas un schéma générique arbitraire

Cohérent avec le principe du dépôt de ne pas construire au-delà du besoin confirmé — les 2 cas cités
par l'utilisateur suffisent à démarrer, pas de mécanisme de plugin/schéma générique ouvert :

1. **`dossier-fields`** → rend `DynamicFields.vue` contre le schéma du `DossierType` du dossier lié
   à la tâche. Soumission : `PATCH /api/dossiers/{id}/fields` (endpoint existant), puis complétion
   de la tâche sans variable (ou `{fieldsUpdated: true}`).
2. **`document-generation`** → nouveau petit composant (forme différente, pas de réutilisation
   possible) : sélection `templateKey` (liste via `ListDocumentTemplateUseCase`), `variantKey`,
   `locale`. Soumission : **complétion de la tâche avec ces 3 variables** — pas d'appel direct à
   `GenerateDocumentUseCase` depuis le frontend. La génération reste exactement où elle est
   aujourd'hui, dans le `serviceTask` `${documentGenerationTask}` suivant dans le process, qui lit
   ces variables comme il le fait déjà. Le formulaire fournit l'entrée, il ne court-circuite pas le
   modèle process-driven existant.

---

## 3. Composants à ajouter

### Backend

- `MyWorkflowTask`/`WorkflowTaskView` — nouveau champ `formKey`.
- `FlowableWorkflowTaskInboxAdapter` — lit `task.getFormKey()` (API `TaskService` déjà injecté,
  aucune nouvelle dépendance).
- `WorkflowTaskController.MyWorkflowTaskResponse` — expose `formKey` au frontend.
- Aucun changement à `CompleteTaskUseCase`/`POST .../complete` — déjà capable de porter des
  variables.

### Frontend

- Nouveau champ texte `flowable:formKey` dans `flowablePropertiesProvider.ts` (éditeur BPMN visuel),
  même patron que `delegateExpression`/`expression` déjà présents pour `userTask`.
- `MyWorkflowTask`/type de tâche frontend — ajoute `formKey`.
- `MobileTasksView.vue`/`TaskList.vue` — si `formKey` présent, ouvrir le formulaire correspondant
  avant de permettre la complétion ; sinon comportement actuel inchangé (bouton « Compléter »
  aveugle, rétrocompatible sans migration des process existants).
- Nouveau composant de sélection template/variante/locale pour le kind `document-generation` (pas
  de composant existant réutilisable pour cette forme).

---

## 4. Points ouverts (à trancher en phase requirements/design)

1. **D-FORM-KIND-SCOPE** — s'en tenir à ces 2 kinds fixes (recommandé, aucun 3ᵉ besoin confirmé) vs.
   prévoir un mécanisme extensible dès maintenant (sur-ingénierie non justifiée à ce stade).
2. **D-FORM-DOSSIER-FIELDS-PARTIAL** — le formulaire `dossier-fields` doit-il permettre d'éditer un
   **sous-ensemble** des champs (ex. seulement ceux pertinents à cette étape) ou toujours le schéma
   complet du `DossierType` ? `DynamicFields.vue` ne le supporte pas aujourd'hui (rend toujours
   `definitions` en entier) — un sous-ensemble demanderait soit un filtre encodé dans le `formKey`
   (ex. `dossier-fields:groupe`), soit un nouveau paramètre au composant.
3. **D-FORM-DOCGEN-TEMPLATE-SCOPE** — le picker doit-il lister tous les templates actifs du tenant,
   ou seulement ceux cohérents avec le type de dossier ? Aucun lien `DossierType`↔`templateKey`
   n'existe aujourd'hui dans le modèle — à vérifier/trancher en design.
4. **D-FORM-VALIDATION** — validation côté client uniquement (comme `DynamicFields.vue` aujourd'hui)
   — le serveur revalide de toute façon via `DossierFieldValidator` sur `PATCH .../fields`, donc pas
   de nouvelle garde nécessaire pour le kind `dossier-fields`.
5. **D-FORM-BACKWARD-COMPAT** — confirmé par construction (tâche sans `formKey` = comportement
   actuel inchangé), pas un point réellement ouvert mais à documenter explicitement en design.
6. **Vérification à faire en design, pas supposée** : confirmer que `Task.getFormKey()` est bien
   accessible sans le module `flowable-form` sur la version Flowable 8.0.x réellement utilisée par
   ce dépôt (comportement historique de l'API Activiti/Flowable, cohérent avec le classpath actuel,
   mais pas vérifié en pratique dans ce dépôt).
