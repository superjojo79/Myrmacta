# Guide de conception d'un diagramme BPMN pour FlowForge

> Public : quiconque modélise ou modifie un processus `.bpmn20.xml` destiné à tourner sur le
> moteur FlowForge (dans l'éditeur intégré `/admin/bpmn-editor` ou avec un outil externe type
> Camunda Modeler). Ce document répond aux questions pratiques que l'existant
> ([05-workflow-engine.md](analysis%20doc/05-workflow-engine.md), [06-connecteurs.md](analysis%20doc/06-connecteurs.md),
> [07-generation-documents.md](analysis%20doc/07-generation-documents.md),
> [08-templates-documents.md](analysis%20doc/08-templates-documents.md), [04-dossiers.md](analysis%20doc/04-dossiers.md))
> documente chacun sous son propre angle, sans jamais les rassembler du point de vue de
> l'auteur d'un diagramme. Tous les noms de variables, de beans et d'attributs cités ci-dessous
> sont vérifiés contre le code réel du dépôt (`src/main/java/com/flowforge/infrastructure/flowable/`),
> pas seulement contre le pseudo-code des autres documents d'analyse — les deux écarts trouvés
> sont signalés explicitement.

---

## 1. Convention à utiliser

### Identifiants BPMN

| Élément | Convention | Exemple réel du dépôt |
|---|---|---|
| `process id` | `camelCase`, devient la **`processDefinitionKey`** — doit être **strictement identique** à `WorkflowTypeConfig.workflowKey` (config créée dans `/admin/workflow-types`) et à `DossierType.workflowKey` si ce processus est le workflow par défaut d'un type de dossier | `smokeProcess`, `signalCatchSmoke` |
| `id` des éléments (tâches, événements, passerelles) | `camelCase` descriptif de l'activité, pas de préfixe technique | `smokeTask`, `updateWorkflowLinkStatusTask`, `waitSignal` |
| `id` des `sequenceFlow` | `flow_<origine>_<destination>` | `flow_start_task`, `flow_task_update` |
| `id` d'un `signal` | `camelCase`, portée globale par défaut | `connectorSmokeSignal` (`flowable:scope="global"`) |

Ces conventions sont celles déjà en usage dans `src/main/resources/processes/*.bpmn20.xml`
(processus de smoke test) — les reprendre à l'identique pour tout nouveau processus garde le
dépôt homogène et lisible en diff.

### `serviceTask` — toujours par `delegateExpression`, jamais par classe Java

Chaque tâche de service appelle un bean Spring enregistré sous un nom (`@Component("xxxTask")`
sur une classe qui implémente `JavaDelegate`), référencé via :

```xml
<serviceTask id="monTask" name="Nom lisible"
             flowable:delegateExpression="${nomDuBean}" />
```

Ne jamais utiliser `flowable:class` (couplage classe Java figée dans le XML, incompatible avec
le rechargement Spring et avec les tests) — tous les `serviceTask` du dépôt utilisent
`delegateExpression`. Le nom entre `${...}` est **exactement** la valeur passée à `@Component(...)`
— voir le catalogue des tâches disponibles en §7.

### Ce que l'éditeur BPMN intégré permet réellement d'éditer

L'éditeur (`/admin/bpmn-editor`, voir [14-edition-bpmn.md](analysis%20doc/14-edition-bpmn.md)) est
`bpmn-js` Modeler + `bpmn-js-properties-panel` avec un **provider Flowable volontairement
restreint** (`flowablePropertiesProvider.ts`) — décision **D-EDIT-SCOPE**. Seuls ces attributs
`flowable:*` sont exposés dans le panel de propriétés :

| Élément | Attribut éditable dans l'UI |
|---|---|
| `serviceTask` | `flowable:async` (case à cocher), `flowable:delegateExpression`, `flowable:expression` |
| `signal` | `flowable:scope` |

**Explicitement absents de l'UI** : `assignee`, `candidateGroups`, `formKey`. L'assignation d'une
tâche humaine (`userTask`) n'est **pas déclarée dans le BPMN** — voir §6. Un diagramme importé
depuis un outil externe qui porte d'autres attributs `flowable:*` (ex. `assignee` posé dans
Camunda Modeler) les conserve tels quels au ré-export (`bpmn-moddle` préserve les attributs
d'espace de noms inconnus), mais l'éditeur FlowForge ne permet ni de les visualiser ni de les
modifier — à écrire/modifier hors FlowForge si besoin, en connaissance de cause.

### Convention de nommage des variables de processus

Il n'existe pas de schéma de variables imposé par Flowable — la convention ci-dessous est celle
suivie par toutes les tâches livrées (voir catalogue §7) :

- **camelCase**, préfixé par le domaine fonctionnel quand plusieurs variables se rapportent au
  même mécanisme : `notification*` (`notificationChannels`, `notificationTo`…), `document*`
  (`documentType`, `documentVariant`, `documentLocale`), `child*` (`childDossierTypeCode`,
  `childDossierLabel`…).
- **Entrée vs sortie** : une tâche documente dans son nom de variable si elle la lit (entrée,
  fixée par le concepteur du diagramme ou une tâche précédente) ou l'écrit (sortie, à consommer
  par une tâche suivante) — voir la colonne dédiée du catalogue.
- **Trois variables réservées**, injectées automatiquement par la plateforme au démarrage de
  **toute** instance (jamais à fixer soi-même — une tentative lève `IllegalArgumentException`,
  voir `WorkflowVariables.assertNoReservedKeys`) :

  | Variable | Contenu |
  |---|---|
  | `dossierId` | UUID du dossier manipulé |
  | `dossierWorkflowLinkId` | UUID de l'exécution courante (`DossierWorkflowLink`) |
  | `tenantId` | tenant courant |

  Toute tâche qui a besoin du dossier ou du tenant les lit sur `DelegateExecution`, jamais en
  paramètre statique du BPMN.

---

## 2. Déclenchement d'un workflow

Trois origines possibles — toutes aboutissent à `dossierId`/`dossierWorkflowLinkId`/`tenantId`
injectées, le reste des variables passées par l'appelant :

| Origine | Mécanisme | Détail |
|---|---|---|
| **Connecteur externe** (webhook, signal Flowable…) | `TriggerConnector.onEvent(...)` → outbox → relais (`OutboxRelayScheduler`) | [06-connecteurs.md](analysis%20doc/06-connecteurs.md). `WorkflowTriggerEvent.variables()` devient les variables du processus — **pas** `dossierFields()` (voir §3, écart important) |
| **Manuel — API/UI** | `POST /api/dossiers/{id}/workflows` (en-tête `Idempotency-Key` obligatoire) → `ManualStartOrchestrator.start(...)` | Depuis la fiche dossier, bouton **Démarrer workflow** (`GUIDE-UTILISATEUR.md` §3) |
| **Chaînage — dossier enfant/suite** | `serviceTask` `${createChildDossierTask}` (voir §7) — crée un dossier `PARENT_CHILD` ou `SUBSEQUENT` **et** démarre son workflow via le même `ManualStartOrchestrator` | [04-dossiers.md](analysis%20doc/04-dossiers.md) §« Démarrage d'un nouveau workflow sur un dossier existant » |

> **Chaîner un *nouveau* workflow sur le *même* dossier** (pas un dossier enfant) — utile pour
> enchaîner deux processus BPMN distincts sur le même objet — n'a **pas** de `serviceTask`
> prêt à l'emploi dans le dépôt actuel : seul `createChildDossierTask` existe, et il crée
> systématiquement un `DossierRelation`. Pour ce cas, retomber sur l'appel manuel API une fois
> le workflow précédent clos (statut terminal atteint), ou écrire un delegate dédié appelant
> `ManualStartOrchestrator` avec le `dossierId` courant — ne pas contourner la règle de
> concurrence (« un seul `DossierWorkflowLink` actif par dossier », voir
> [04-dossiers.md](analysis%20doc/04-dossiers.md)).

---

## 3. Référencer les champs dynamiques du dossier

**Point le plus fréquemment mal compris : les champs dynamiques du dossier (`Dossier.fields`,
schéma `DossierType.fieldDefinitions`) ne sont PAS automatiquement injectés comme variables de
processus, ni automatiquement disponibles dans un template de document.** C'est un écart réel
entre le pseudo-code de [04-dossiers.md](analysis%20doc/04-dossiers.md) (`DossierAwareTask`, chargement
automatique du dossier avant chaque `ServiceTask`) et le code livré : cette classe **n'existe
pas** dans `src/main/java`. Chaque tâche lit exactement les variables qu'elle déclare — rien de
plus.

**Impact concret pour l'auteur d'un diagramme :** rien n'échoue à la conception ni au déploiement
si un champ dynamique nécessaire n'est jamais propagé — l'erreur se révèle seulement à
l'exécution, et pas toujours bruyamment. `ThymeleafHtmlTemplateProcessor` construit son contexte
uniquement à partir des variables réellement présentes ; une variable scalaire absente
(`${montantSinistre}` jamais fixée) est résolue à `null` par Thymeleaf et rendue silencieusement
comme une chaîne vide dans le PDF — aucune exception, aucun log, juste un champ visiblement vide
sur le document livré. Seule la navigation de propriété sur un objet composite absent
(`${dossier.reference}` sans variable `dossier`) déclenche une `TemplateProcessingException` (500
à la génération). Conséquence pratique : tester chaque template avec un jeu de variables réel
avant mise en production, ne pas se fier à l'absence d'erreur en développement comme preuve que
tous les champs sont bien câblés, et considérer que toute reprise du pseudo-code de
[04-dossiers.md](analysis%20doc/04-dossiers.md) sur ce point précis (`DossierAwareTask`) décrit une intention non
implémentée, pas le comportement réel.

Concrètement :

- **Au démarrage**, seul `WorkflowTriggerEvent.variables()` (chaîné trigger) ou
  `StartWorkflowRequest.variables()` (API manuelle) devient des variables de processus.
  `WorkflowTriggerEvent.dossierFields()` sert **uniquement** à peupler `Dossier.fields()` à la
  création — ces valeurs ne traversent pas automatiquement vers Flowable.
- **Pour qu'un champ dynamique (ex. `montantSinistre`, `dateIncident`) soit utilisable dans le
  processus ou dans un template de document**, il faut explicitement :
  1. soit le dupliquer dans `variables` au moment du déclenchement (connecteur ou appel manuel)
     — le plus simple si le champ est connu à l'avance ;
  2. soit écrire un `serviceTask` dédié qui appelle `GetDossierUseCase.get(dossierId, ...)`
     et fait `execution.setVariable(...)` pour chaque champ à exposer — nécessaire si le
     workflow doit refléter des champs modifiés après le démarrage (`PATCH` sur le dossier,
     voir `UpdateDossierFieldsUseCaseImpl`).
- **Dans un template Thymeleaf de document** (voir §5 et
  [08-templates-documents.md](analysis%20doc/08-templates-documents.md)) : la syntaxe est `${nomDeLaVariable}`
  — le moteur de rendu (`DocumentGenerationTask`) fusionne **toutes** les variables du processus
  en cours (`execution.getVariables()`), moins une allowlist de clés plateforme filtrée par
  `DocumentVariableFilter` (`dossierId`, `dossierWorkflowLinkId`, `tenantId`,
  `generatedDocumentId`, `documentType`, `documentVariant`, `documentLocale`). Toute autre
  variable — dont les champs dynamiques explicitement copiés à l'étape précédente — est
  utilisable telle quelle dans le template : `${montantSinistre}`.
- **Objet `dossier` en tant que tel** (`${dossier.reference}`, mentionné comme exemple générique
  dans [08-templates-documents.md](analysis%20doc/08-templates-documents.md)) : n'existe **que** si une tâche du
  processus a explicitement fait `execution.setVariable("dossier", ...)` avec un objet portant
  ces accesseurs — ce n'est pas fourni nativement aujourd'hui. Préférer exposer les champs un par
  un (`${dossierReference}`, `${montantSinistre}`…) tant qu'aucune tâche standard ne matérialise
  cet objet.

---

## 4. Instancier / appeler un service externe

Le mécanisme est le **système de connecteurs** ([06-connecteurs.md](analysis%20doc/06-connecteurs.md)), jamais
un appel HTTP écrit en dur dans un delegate ad hoc.

### Étape 1 — créer une instance de connecteur (une fois, côté admin)

Écran d'admin des connecteurs : choisir un type (`ConnectorDefinition.getType()`, ex.
`flowable-signal`, ou un connecteur custom ajouté au classpath — voir
[06-connecteurs.md](analysis%20doc/06-connecteurs.md) « Ajout d'un connecteur custom »), renseigner la
configuration déclarée par `getConfigSchema()`. Cela produit un `ConnectorInstanceData.id`
(UUID) — **c'est cet UUID que le BPMN référence**, jamais le type ou la config en dur.

### Étape 2 — l'appeler depuis un `serviceTask`

```xml
<serviceTask id="appelServiceExterne" name="Appel connecteur"
             flowable:delegateExpression="${connectorServiceTask}" />
```

Variables lues par `connectorServiceTask` :

| Variable | Obligatoire | Rôle |
|---|---|---|
| `connectorInstanceId` | oui | UUID de l'instance de connecteur créée à l'étape 1 |
| `connectorInput` | non (`Map`, défaut vide) | paramètres passés à `ServiceConnector.execute(instance, input)` |
| `tenantId` | non | override du tenant courant — laisser la valeur injectée par défaut sauf besoin explicite |

Variable écrite : `connectorResult` (`Map<String, Object>`, uniquement si le résultat n'est pas
vide) — à lire dans les tâches suivantes du même processus.

### Étape 3 (facultative) — signal Flowable entre deux processus

Le connecteur `flowable-signal` permet à un `serviceTask` (`${connectorServiceTask}` avec
l'instance configurée en émetteur) de déclencher un `intermediateCatchEvent`/`signalEventDefinition`
dans un **autre** processus, sans dépendance directe entre les deux diagrammes — voir
`src/main/resources/processes/signal-smoke.bpmn20.xml` pour un exemple minimal émetteur/récepteur.

---

## 5. Déclencher la génération d'un document

Deux `serviceTask` distincts, presque toujours chaînés l'un après l'autre :

### `documentGenerationTask` — génère le PDF

```xml
<serviceTask id="genererDocument" name="Générer le document"
             flowable:delegateExpression="${documentGenerationTask}" />
```

| Variable | Obligatoire | Rôle |
|---|---|---|
| `documentType` | oui | `templateKey` — résout le template actif (voir [08-templates-documents.md](analysis%20doc/08-templates-documents.md)) |
| `documentVariant` | non | `variantKey` (ex. `"premium"`, `"B2B"`) — résolution en cascade si absent |
| `documentLocale` | non | langue du destinataire (ex. `"fr"`, `"nl"`) — résolution en cascade si absent |

Toutes les autres variables du processus au moment de l'exécution sont transmises au template
Thymeleaf (voir §3 pour la mécanique exacte et l'allowlist de rendu — pas de `T()`, `#beans`,
`new`, cf. décision **D-40**). Variable écrite : `generatedDocumentId` (référence de stockage).

### `attachGeneratedDocumentTask` — attache le PDF au dossier

```xml
<serviceTask id="attacherDocument" name="Attacher au dossier"
             flowable:delegateExpression="${attachGeneratedDocumentTask}" />
```

Lit `dossierId` (réservée, injectée automatiquement) et `generatedDocumentId` (écrite par la
tâche précédente) — échoue si le document référencé n'existe plus dans le stockage. Crée un
`DossierElement` de type `PRODUCED_DOC`, visible dans la fiche dossier, avec `workflowStepId`
= l'`id` de cette activité BPMN.

> **Toujours enchaîner les deux.** `documentGenerationTask` seul génère le PDF sans le rattacher
> au dossier — il resterait orphelin (accessible uniquement via `generatedDocumentId`, jamais
> listé dans les éléments du dossier).

---

## 6. Référencer les intervenants sur un dossier

FlowForge ne déclare **aucun intervenant dans le BPMN lui-même** — ni `assignee`, ni
`candidateGroups` (retiré explicitement du scope de l'éditeur, décision **D-EDIT-SCOPE**, voir
§1). Trois mécanismes distincts couvrent le besoin :

### a) Assignation d'une tâche humaine (`userTask`) — à l'exécution, pas dans le diagramme

Un `userTask` sans `assignee` reste non assignée jusqu'à une action explicite :
`TaskService.setAssignee(taskId, userId)`, exposé par
`PATCH`/action **Réassigner** sur l'écran de suivi du dossier (`GUIDE-UTILISATEUR.md` §3, tâches
en cours) ou sur `/tasks` — identifiant utilisateur saisi librement, pas de groupe candidat en V1.
Rien à écrire dans le diagramme pour ce mécanisme.

### b) Résolution des « participants » d'une exécution — pour les notifications automatiques

`WorkflowParticipantPort.findParticipantUserIds(link)` calcule, **sans configuration BPMN**, la
liste des utilisateurs concernés par un `DossierWorkflowLink` :

- l'utilisateur qui a démarré l'exécution (`startedBy`, ou l'utilisateur Flowable ayant démarré
  l'instance) ;
- tous les *assignees*, passés et présents, des tâches humaines de l'instance (historique
  compris) ;
- les **observateurs** déclarés sur la `WorkflowTypeConfig` du processus (`observerUserIds`) —
  seul point réellement configurable, dans l'écran **Admin → Workflows**.

Cette liste est utilisée **automatiquement**, sans aucun `serviceTask` à poser dans le
diagramme : `WorkflowStateEventListener` (écouteur d'événements Flowable enregistré
globalement) notifie chaque participant en canal `BROWSER` (SSE) sur `TASK_ASSIGNED`,
`ACTIVITY_COMPLETED` et `PROCESS_COMPLETED`. Si le besoin se limite à « informer les gens déjà
impliqués qu'il se passe quelque chose », il n'y a rien à faire dans le BPMN.

### c) Notification explicite et ciblée — `notificationDispatchTask`

Pour un message métier explicite (pas juste « ça a bougé »), poser :

```xml
<serviceTask id="notifierGestionnaire" name="Notifier"
             flowable:delegateExpression="${notificationDispatchTask}" />
```

| Variable | Obligatoire | Rôle |
|---|---|---|
| `notificationChannels` | oui | liste ou CSV parmi `EMAIL`, `WEBHOOK`, `BROWSER` |
| `notificationTo` | selon canal | destinataire email |
| `notificationSubject` / `notificationBody` | selon canal | contenu email |
| `notificationUserId` | pour `BROWSER` | **un seul** utilisateur — pour notifier plusieurs intervenants (ex. tous les participants), utiliser un `multiInstanceLoopCharacteristics` Flowable standard sur cette tâche, itérant sur une variable liste, ou combiner avec le mécanisme (b) qui couvre déjà le cas générique |
| `notificationWebhookConfigId` | pour `WEBHOOK` | UUID de la config webhook tenant |
| `notificationPayload` | non | `Map` libre, transmis tel quel au canal |

Le contenu peut référencer les champs dynamiques exposés selon la méthode du §3
(`notificationBody` reste une simple variable process — construire la chaîne avec une expression
FEEL/UEL Flowable ou une tâche amont qui la compose).

---

## 7. Catalogue des `serviceTask` livrés

Référence rapide — `delegateExpression`, variables lues (entrée) et écrites (sortie) :

| `delegateExpression` | Rôle | Entrée | Sortie |
|---|---|---|---|
| `${updateDossierStatusTask}` | change `Dossier.status` | `dossierId` (réservée), `targetDossierStatus` (valeur de `DossierStatus`) | — |
| `${updateWorkflowLinkStatusTask}` | change le statut métier de l'exécution courante (`DossierWorkflowLink.status`, validé contre `WorkflowTypeConfig.statusDefinitions`) | `dossierWorkflowLinkId` (réservée), `targetWorkflowStatus` (code déclaré dans la config workflow) | — |
| `${documentGenerationTask}` | génère un PDF depuis un template | `documentType`, `documentVariant`?, `documentLocale`?, + toute variable utile au template | `generatedDocumentId` |
| `${attachGeneratedDocumentTask}` | rattache le PDF généré au dossier (`DossierElement`) | `dossierId` (réservée), `generatedDocumentId` | — |
| `${connectorServiceTask}` | invoque un connecteur en rôle SERVICE | `connectorInstanceId`, `connectorInput`? | `connectorResult`? |
| `${notificationDispatchTask}` | envoie une notification (outbox) | `notificationChannels` + champs selon canal (§6c) | — |
| `${createChildDossierTask}` | crée un dossier enfant/suite et démarre son workflow | `dossierId` (réservée), `childDossierTypeCode`, `childDossierLabel`, `workflowProcessKey`, `relationType` (`PARENT_CHILD` ou `SUBSEQUENT`), `childDossierFields`?, `childWorkflowVariables`? | — |

Toutes ces classes vivent dans `src/main/java/com/flowforge/infrastructure/flowable/` — en cas
de doute sur une variable, elles font foi avant ce document ou avant les `analysis doc/*`
pseudo-code.

---

## 8. Sécurité et bonnes pratiques à respecter en concevant un processus

- **Ne jamais faire confiance à une variable issue d'un webhook** sans validation métier en
  amont (le connecteur `http-rest` authentifie la requête HMAC/token/IP, mais ne valide pas le
  contenu du payload — c'est `DossierFieldValidator` côté création du dossier qui le fait, pas
  le BPMN).
- **URLs sortantes** (webhook, connecteur HTTP) passent toujours par `OutboundUrlGuard` côté
  infrastructure — ne jamais construire un appel HTTP direct dans un delegate custom sans
  réutiliser cette garde (protection SSRF, voir [06-connecteurs.md](analysis%20doc/06-connecteurs.md) et
  [09-notifications.md](analysis%20doc/09-notifications.md)).
- **Champs sensibles** transmis en variables (ex. copiés depuis `Dossier.fields` pour un
  template ou une notification) passent par le masquage prévu côté notification
  (`DossierFieldsSnapshot`, redaction) — ne pas court-circuiter en composant soi-même une chaîne
  contenant des données sensibles brutes dans `notificationBody`/`notificationPayload`.
- **Templates de document** : n'utiliser que `${...}` et les objets utilitaires Thymeleaf
  autorisés (`#dates`, `#numbers`, `#strings`, `#lists`) — `T()`, `#beans`, `new`, `#request`
  sont refusés à l'activation du template (décision **D-40**, voir
  [08-templates-documents.md](analysis%20doc/08-templates-documents.md)).
- **Concurrence** : ne jamais tenter de démarrer un second workflow sur un dossier qui en a déjà
  un actif en dehors des mécanismes documentés ici (§2) — la contrainte unique
  `uq_dossier_workflow_link_active` le refuse de toute façon, mais un `serviceTask` custom qui
  l'ignorerait ferait échouer le processus en pleine exécution plutôt qu'à la conception.

---

## 9. Checklist avant de déployer un nouveau processus

1. `process id` = `workflowKey` de la `WorkflowTypeConfig` déjà créée (ou à créer juste après un
   premier déploiement via upload — voir `GUIDE-UTILISATEUR.md` §3).
2. Chaque `serviceTask` référence un `delegateExpression` existant (catalogue §7) — jamais
   `flowable:class`.
3. Les champs dynamiques nécessaires au processus ou aux templates sont explicitement propagés
   en variables (§3) — ne pas supposer qu'ils sont disponibles par défaut.
4. Les statuts posés par `targetDossierStatus`/`targetWorkflowStatus` existent bien dans
   `DossierStatus` / dans les `statusDefinitions` de la `WorkflowTypeConfig` du processus.
5. Toute génération de document est suivie d'un rattachement (`attachGeneratedDocumentTask`),
   sauf besoin explicite de PDF non rattaché.
6. Aucun `assignee`/`candidateGroups` attendu du diagramme pour l'assignation des `userTask` —
   c'est un geste opérationnel post-démarrage (§6a).
7. `./mvnw verify` (ArchUnit compris) si un nouveau delegate Java a été ajouté pour ce processus.
